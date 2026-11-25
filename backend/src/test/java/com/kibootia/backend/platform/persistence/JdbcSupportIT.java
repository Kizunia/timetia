package com.kibootia.backend.platform.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionTemplate;

import com.kibootia.backend.TestcontainersConfiguration;
import com.kibootia.backend.platform.persistence.JdbcWidgetStore.Widget;
import com.kibootia.backend.platform.tenancy.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What {@link JdbcSupport} must do for the JDBC adapters, on a real MySQL: join the use case's transaction (so a rollback
 * undoes the adapter's writes, also beside JPA), and keep every convention JPA kept (tenant filter, audit columns,
 * optimistic lock, duplicate key, soft delete, paging). Uses the test-only {@code test_widget} table.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JdbcSupportIT {

	static final UUID TENANT_A = UUID.fromString("00000000-0000-7000-8000-00000000000a");

	static final UUID TENANT_B = UUID.fromString("00000000-0000-7000-8000-00000000000b");

	static final UUID ALICE = UUID.fromString("11111111-1111-7111-8111-111111111111");

	@Autowired
	DataSource dataSource;

	@Autowired
	TransactionTemplate transactions;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	TestWidgetRepository widgetRepository;

	JdbcWidgetStore widgets;

	@BeforeEach
	void setUp() {
		this.widgets = new JdbcWidgetStore(this.dataSource);
		this.jdbc.update("delete from test_widget");
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	/** Runs the work as a tenant, inside one transaction, the way a use case runs. */
	private <T> T inTransaction(UUID tenant, Callable<T> work) {
		return TenantContext.callAs(tenant, () -> this.transactions.execute((status) -> {
			try {
				return work.call();
			}
			catch (RuntimeException ex) {
				throw ex;
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		}));
	}

	private static void signInAs(UUID user) {
		Jwt token = Jwt.withTokenValue("test").header("alg", "none").subject(user.toString()).claim("email_verified", true).build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));
	}

	/** Counts the rows another connection can see, which is what is committed. */
	private int committedRows() throws SQLException {
		try (Connection other = this.dataSource.getConnection();
				PreparedStatement statement = other.prepareStatement("select count(*) from test_widget");
				ResultSet rows = statement.executeQuery()) {
			rows.next();
			return rows.getInt(1);
		}
	}

	@Test
	@DisplayName("the adapter joins the use case's transaction: its row is invisible to others until commit")
	void joinsTheTransaction() throws SQLException {
		inTransaction(TENANT_A, () -> {
			UUID id = this.widgets.insert("in transaction");

			assertThat(this.widgets.find(id)).isPresent();
			assertThat(committedRows()).isZero();
			return null;
		});

		assertThat(committedRows()).isEqualTo(1);
	}

	@Test
	@DisplayName("a rollback undoes the adapter's writes")
	void rollbackUndoesTheWrites() throws SQLException {
		assertThatThrownBy(() -> inTransaction(TENANT_A, () -> {
			this.widgets.insert("rolled back");
			throw new IllegalStateException("the use case fails");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(committedRows()).isZero();
	}

	@Test
	@DisplayName("beside JPA, one rollback undoes both what JPA wrote and what the adapter wrote")
	void rollbackUndoesJpaAndJdbcTogether() throws SQLException {
		assertThatThrownBy(() -> inTransaction(TENANT_A, () -> {
			this.widgetRepository.saveAndFlush(new TestWidget("by jpa"));
			this.widgets.insert("by jdbc");
			assertThat(this.jdbc.queryForObject("select count(*) from test_widget", Integer.class)).isEqualTo(2);
			throw new IllegalStateException("the use case fails");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(committedRows()).isZero();
	}

	@Test
	@DisplayName("beside JPA, both commit together and JPA reads what the adapter wrote")
	void commitsJpaAndJdbcTogether() throws SQLException {
		UUID jdbcId = inTransaction(TENANT_A, () -> {
			this.widgetRepository.saveAndFlush(new TestWidget("by jpa"));
			return this.widgets.insert("by jdbc");
		});

		assertThat(committedRows()).isEqualTo(2);
		TestWidget read = inTransaction(TENANT_A, () -> this.widgetRepository.findById(jdbcId).orElseThrow());
		assertThat(read.getName()).isEqualTo("by jdbc");
		assertThat(read.getVersion()).isZero();
	}

	@Test
	@DisplayName("without a transaction a statement commits on its own")
	void commitsWithoutATransaction() throws SQLException {
		TenantContext.runAs(TENANT_A, () -> this.widgets.insert("alone"));

		assertThat(committedRows()).isEqualTo(1);
	}

	@Test
	@DisplayName("ids made one after another increase, also within one millisecond, so ordering by id is ordering by creation")
	void idsIncrease() {
		List<UUID> ids = inTransaction(TENANT_A, () -> {
			List<UUID> made = new ArrayList<>();
			for (int i = 0; i < 200; i++) {
				made.add(this.widgets.insert("widget " + i));
			}
			return made;
		});

		assertThat(ids).allSatisfy((id) -> assertThat(id.version()).isEqualTo(7));
		for (int i = 1; i < ids.size(); i++) {
			assertThat(ids.get(i).toString()).isGreaterThan(ids.get(i - 1).toString());
		}
	}

	@Test
	@DisplayName("the id is a UUIDv7 in BINARY(16) and the audit columns are filled like auditing fills them")
	void idAndAuditColumns() {
		signInAs(ALICE);
		UUID id = inTransaction(TENANT_A, () -> this.widgets.insert("audited"));

		assertThat(id.version()).isEqualTo(7);
		assertThat(this.jdbc.queryForObject("select length(id) from test_widget", Integer.class)).isEqualTo(16);
		Widget widget = TenantContext.callAs(TENANT_A, () -> this.widgets.find(id).orElseThrow());
		assertThat(widget.tenantId()).isEqualTo(TENANT_A);
		assertThat(widget.version()).isZero();
		assertThat(widget.createdBy()).isEqualTo(ALICE);
		assertThat(widget.updatedBy()).isEqualTo(ALICE);
		assertThat(widget.createdAt()).isNotNull().isEqualTo(widget.updatedAt());
	}

	@Test
	@DisplayName("work no user started is recorded under the system actor")
	void systemActor() {
		UUID id = inTransaction(TENANT_A, () -> this.widgets.insert("by a job"));

		Widget widget = TenantContext.callAs(TENANT_A, () -> this.widgets.find(id).orElseThrow());
		assertThat(widget.createdBy()).isEqualTo(new UUID(0L, 0L));
	}

	@Test
	@DisplayName("every read is filtered by tenant, and a read with no tenant finds nothing")
	void tenantFilter() {
		UUID id = inTransaction(TENANT_A, () -> this.widgets.insert("tenant a"));

		assertThat(TenantContext.callAs(TENANT_A, () -> this.widgets.find(id))).isPresent();
		assertThat(TenantContext.callAs(TENANT_B, () -> this.widgets.find(id))).isEmpty();
		assertThat(this.widgets.find(id)).isEmpty();
	}

	@Test
	@DisplayName("a write with no tenant bound is refused")
	void writeNeedsATenant() {
		assertThatThrownBy(() -> this.widgets.insert("nobody's")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("a change with the right version advances it; with a stale version it is refused as an optimistic lock failure")
	void optimisticLock() {
		UUID id = inTransaction(TENANT_A, () -> this.widgets.insert("first"));

		inTransaction(TENANT_A, () -> {
			this.widgets.rename(id, 0, "second");
			return null;
		});

		Widget renamed = TenantContext.callAs(TENANT_A, () -> this.widgets.find(id).orElseThrow());
		assertThat(renamed.name()).isEqualTo("second");
		assertThat(renamed.version()).isEqualTo(1);
		assertThatThrownBy(() -> inTransaction(TENANT_A, () -> {
			this.widgets.rename(id, 0, "third");
			return null;
		})).isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}

	@Test
	@DisplayName("a duplicate key is a DataIntegrityViolationException with MySQL's error 1062 in the cause chain")
	void duplicateKey() {
		inTransaction(TENANT_A, () -> this.widgets.insert("same name"));

		assertThatThrownBy(() -> inTransaction(TENANT_A, () -> this.widgets.insert("same name")))
			.isExactlyInstanceOf(DataIntegrityViolationException.class)
			.hasRootCauseInstanceOf(SQLException.class)
			.rootCause()
			.satisfies((cause) -> assertThat(((SQLException) cause).getErrorCode()).isEqualTo(1062));
	}

	@Test
	@DisplayName("a soft-deleted row is not read, and its name can be used again")
	void softDelete() {
		UUID id = inTransaction(TENANT_A, () -> this.widgets.insert("gone"));

		inTransaction(TENANT_A, () -> {
			this.widgets.softDelete(id);
			return null;
		});

		assertThat(TenantContext.callAs(TENANT_A, () -> this.widgets.find(id))).isEmpty();
		assertThat(this.jdbc.queryForObject("select count(*) from test_widget", Integer.class)).isEqualTo(1);
		inTransaction(TENANT_A, () -> this.widgets.insert("gone"));
	}

	@Test
	@DisplayName("a page has its slice, the total from a count query and the page request")
	void paging() {
		inTransaction(TENANT_A, () -> {
			for (String name : new String[] { "a", "b", "c", "d", "e" }) {
				this.widgets.insert(name);
			}
			return null;
		});
		inTransaction(TENANT_B, () -> this.widgets.insert("other tenant"));

		Page<Widget> second = TenantContext.callAs(TENANT_A, () -> this.widgets.page(PageRequest.of(1, 2)));

		assertThat(second.getContent()).extracting(Widget::name).containsExactly("c", "d");
		assertThat(second.getTotalElements()).isEqualTo(5);
		assertThat(second.getTotalPages()).isEqualTo(3);
	}

	@Test
	@DisplayName("any other SQL failure is a JdbcAccessException with the SQLException as its cause")
	void otherFailures() {
		assertThatThrownBy(() -> this.widgets.runBrokenStatement()).isExactlyInstanceOf(JdbcAccessException.class)
			.hasCauseInstanceOf(SQLException.class);
	}

	@Test
	@DisplayName("the connection goes back to the pool after every statement")
	void connectionsAreReleased() {
		// More statements than the pool has connections: a leak would make the later ones wait and fail.
		TenantContext.runAs(TENANT_A, () -> {
			for (int i = 0; i < 60; i++) {
				this.widgets.find(UUID.randomUUID());
			}
		});
	}

}
