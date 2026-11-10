package com.kibootia.backend.audit.application;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.kibootia.backend.TestcontainersConfiguration;
import com.kibootia.backend.audit.AuditEntry;
import com.kibootia.backend.audit.AuditLog;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.persistence.TestWidget;
import com.kibootia.backend.platform.persistence.TestWidgetRepository;
import com.kibootia.backend.platform.tenancy.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The audit log on a real MySQL (ADR 0046): one entry per change in the same transaction, none for a rolled-back
 * change, the actor and tenant taken from the caller, no personal data, and the retention purge.
 */
@SpringBootTest(properties = "kibootia.audit.retention-days=30")
@Import(TestcontainersConfiguration.class)
class AuditLogIT {

	static final UUID ALICE = UUID.fromString("11111111-1111-7111-8111-111111111111");

	static final UUID TARGET = UUID.fromString("33333333-3333-7333-8333-333333333333");

	static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-7000-8000-0000000000bb");

	@Autowired
	AuditLog auditLog;

	@Autowired
	AuditService purge;

	@Autowired
	Authorization authorization;

	@Autowired
	TestWidgetRepository widgets;

	@Autowired
	TransactionTemplate transactions;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void clean() {
		jdbc.update("delete from audit_entry");
		jdbc.update("delete from test_widget");
		signInAs(ALICE);
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	private static void signInAs(UUID user) {
		Jwt token = Jwt.withTokenValue("t").header("alg", "none").subject(user.toString()).claim("email_verified", true).build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));
	}

	private <T> T inTransaction(Callable<T> work) {
		return TenantContext.callAs(TenantContext.DEFAULT_TENANT, () -> transactions.execute((status) -> {
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

	private int entryCount() {
		return jdbc.queryForObject("select count(*) from audit_entry", Integer.class);
	}

	private static String hex(UUID id) {
		return id.toString().replace("-", "");
	}

	@Test
	@DisplayName("a change and its entry are committed together")
	void changeAndEntryCommitTogether() {
		inTransaction(() -> {
			widgets.save(new TestWidget("audited"));
			auditLog.record(AuditEntry.of("test.widget.created", "widget", TARGET).withChange("roles", "+editor"));
			return null;
		});

		assertThat(jdbc.queryForObject("select count(*) from test_widget", Integer.class)).isEqualTo(1);
		Map<String, Object> row = jdbc.queryForMap(
				"select hex(actor_id) actor, hex(tenant_id) tenant, hex(target_id) target, action, target_type, outcome, "
						+ "changes, occurred_at from audit_entry");
		assertThat(row.get("actor")).isEqualTo(hex(ALICE).toUpperCase());
		assertThat(row.get("tenant")).isEqualTo(hex(TenantContext.DEFAULT_TENANT).toUpperCase());
		assertThat(row.get("target")).isEqualTo(hex(TARGET).toUpperCase());
		assertThat(row.get("action")).isEqualTo("test.widget.created");
		assertThat(row.get("target_type")).isEqualTo("widget");
		assertThat(row.get("outcome")).isEqualTo("success");
		assertThat((String) row.get("changes")).isEqualTo("{\"roles\":\"+editor\"}");
		assertThat(row.get("occurred_at")).isNotNull();
	}

	@Test
	@DisplayName("a rolled-back change leaves no entry")
	void rolledBackChangeLeavesNoEntry() {
		assertThatThrownBy(() -> inTransaction(() -> {
			widgets.save(new TestWidget("rolled-back"));
			auditLog.record(AuditEntry.of("test.widget.created", "widget", TARGET));
			throw new IllegalStateException("the change fails after the entry was written");
		})).hasMessageContaining("the change fails");

		assertThat(entryCount()).isZero();
		assertThat(jdbc.queryForObject("select count(*) from test_widget", Integer.class)).isZero();
	}

	@Test
	@DisplayName("an entry cannot be written without a transaction")
	void entryNeedsATransaction() {
		assertThatThrownBy(() -> TenantContext.runAs(TenantContext.DEFAULT_TENANT,
				() -> auditLog.record(AuditEntry.of("test.widget.created", "widget", TARGET))))
			.isInstanceOf(IllegalTransactionStateException.class);
		assertThat(entryCount()).isZero();
	}

	@Test
	@DisplayName("an entry cannot be written without a signed-in caller or the system")
	void entryNeedsACaller() {
		SecurityContextHolder.clearContext();

		assertThatThrownBy(() -> inTransaction(() -> {
			auditLog.record(AuditEntry.of("test.widget.created", "widget", TARGET));
			return null;
		})).isInstanceOf(AccessDeniedException.class);
		assertThat(entryCount()).isZero();
	}

	@Test
	@DisplayName("work done by the system is recorded against the nil actor")
	void systemActor() {
		SecurityContextHolder.clearContext();

		inTransaction(() -> {
			authorization.runAsSystem("test: system action", (Runnable) () -> {
				auditLog.record(AuditEntry.of("test.widget.purged", "widget", null));
			});
			return null;
		});

		assertThat(jdbc.queryForObject("select hex(actor_id) from audit_entry", String.class))
			.isEqualTo("00000000000000000000000000000000");
	}

	@Test
	@DisplayName("a failed action is recorded with its failure code")
	void failureOutcome() {
		inTransaction(() -> {
			auditLog.record(AuditEntry.of("test.widget.deleted", "widget", TARGET).failedWith("access.last_admin"));
			return null;
		});

		assertThat(jdbc.queryForObject("select outcome from audit_entry", String.class)).isEqualTo("access.last_admin");
	}

	@Test
	@DisplayName("the table has no column that could hold a name or an email address")
	void noPersonalDataColumns() {
		List<String> columns = jdbc.queryForList(
				"select column_name from information_schema.columns where table_schema = database() "
						+ "and table_name = 'audit_entry'",
				String.class);

		assertThat(columns).containsExactlyInAnyOrder("id", "tenant_id", "occurred_at", "actor_id", "action",
				"target_type", "target_id", "outcome", "trace_id", "changes");
	}

	@Test
	@DisplayName("the repository offers no way to change or remove an entry except the retention purge")
	void appendOnly() throws ClassNotFoundException {
		Class<?> repository = Class.forName("com.kibootia.backend.audit.persistence.AuditEntryRepository");

		List<String> operations = Arrays.stream(repository.getDeclaredMethods()).map(Method::getName).sorted().toList();

		assertThat(operations).containsExactly("deleteOccurredBefore", "save");
	}

	@Test
	@DisplayName("the purge removes entries older than the retention period and keeps newer ones")
	void purgeRespectsRetention() {
		insertEntry(TenantContext.DEFAULT_TENANT, 40);
		insertEntry(TenantContext.DEFAULT_TENANT, 31);
		insertEntry(TenantContext.DEFAULT_TENANT, 29);
		insertEntry(TenantContext.DEFAULT_TENANT, 1);

		int removed = inTransaction(() -> authorization.runAsSystem("test: purge", purge::purge));

		assertThat(removed).isEqualTo(2);
		assertThat(entryCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("the purge only touches the caller's tenant")
	void purgeIsTenantScoped() {
		insertEntry(TenantContext.DEFAULT_TENANT, 40);
		insertEntry(OTHER_TENANT, 40);

		int removed = inTransaction(() -> authorization.runAsSystem("test: purge", purge::purge));

		assertThat(removed).isEqualTo(1);
		assertThat(jdbc.queryForObject("select hex(tenant_id) from audit_entry", String.class))
			.isEqualTo(hex(OTHER_TENANT).toUpperCase());
	}

	@Test
	@DisplayName("an ordinary user may not purge")
	void purgeIsDeniedToOrdinaryUsers() {
		insertEntry(TenantContext.DEFAULT_TENANT, 40);

		assertThatThrownBy(() -> inTransaction(purge::purge)).isInstanceOf(AccessDeniedException.class);
		assertThat(entryCount()).isEqualTo(1);
	}

	private void insertEntry(UUID tenant, int daysOld) {
		jdbc.update("insert into audit_entry (id, tenant_id, occurred_at, actor_id, action, target_type, outcome) "
				+ "values (unhex(?), unhex(?), now(6) - interval ? day, unhex(?), 'test.widget.created', 'widget', "
				+ "'success')", hex(UUID.randomUUID()), hex(tenant), daysOld, hex(ALICE));
	}

}
