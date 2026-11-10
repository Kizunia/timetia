package com.kibootia.backend.platform.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionTemplate;

import com.kibootia.backend.TestcontainersConfiguration;
import com.kibootia.backend.platform.tenancy.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The persistence conventions on a real MySQL (ADR 0028, 0033, 0034; OQ-41): UUIDv7 keys in BINARY(16), audit
 * columns, optimistic locking, tenant isolation and soft delete, using a test-only table.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PersistenceConventionsIT {

	static final UUID TENANT_A = UUID.fromString("00000000-0000-7000-8000-00000000000a");

	static final UUID TENANT_B = UUID.fromString("00000000-0000-7000-8000-00000000000b");

	static final UUID ALICE = UUID.fromString("11111111-1111-7111-8111-111111111111");

	static final UUID BOB = UUID.fromString("22222222-2222-7222-8222-222222222222");

	@Autowired
	TestWidgetRepository widgets;

	@Autowired
	TransactionTemplate transactions;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EntityManager entityManager;

	@BeforeEach
	void cleanTable() {
		jdbc.update("delete from test_widget");
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	private <T> T asTenant(UUID tenant, Callable<T> work) {
		return TenantContext.callAs(tenant, () -> transactions.execute((status) -> {
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

	private UUID create(UUID tenant, String name) {
		return asTenant(tenant, () -> widgets.save(new TestWidget(name)).getId());
	}

	@Test
	@DisplayName("ids are UUIDv7, assigned on save and stored as BINARY(16)")
	void idsAreUuidV7StoredAsBinary16() {
		UUID id = create(TENANT_A, "first");

		assertThat(id).isNotNull();
		assertThat(id.version()).isEqualTo(7);
		assertThat(jdbc.queryForObject("select length(id) from test_widget", Integer.class)).isEqualTo(16);
		assertThat(jdbc.queryForObject("select hex(id) from test_widget", String.class))
			.isEqualToIgnoringCase(id.toString().replace("-", ""));
	}

	@Test
	@DisplayName("ids made later sort after ids made earlier")
	void idsAreTimeOrdered() throws InterruptedException {
		UUID first = create(TENANT_A, "first");
		Thread.sleep(5);
		UUID second = create(TENANT_A, "second");

		// The hexadecimal text of a UUID sorts like its bytes, which is how MySQL orders a BINARY(16) key.
		assertThat(second.toString().compareTo(first.toString())).isGreaterThan(0);
	}

	@Test
	@DisplayName("the tenant, creation and modification columns are filled")
	void auditColumnsAreFilled() {
		signInAs(ALICE);
		UUID id = create(TENANT_A, "audited");
		TestWidget created = asTenant(TENANT_A, () -> widgets.findById(id).orElseThrow());

		assertThat(created.getTenantId()).isEqualTo(TENANT_A);
		assertThat(created.getCreatedBy()).isEqualTo(ALICE);
		assertThat(created.getUpdatedBy()).isEqualTo(ALICE);
		assertThat(created.getCreatedAt()).isNotNull().isBefore(Instant.now().plusSeconds(5));
		assertThat(created.getVersion()).isZero();

		signInAs(BOB);
		asTenant(TENANT_A, () -> {
			TestWidget widget = widgets.findById(id).orElseThrow();
			widget.setName("renamed");
			return widgets.saveAndFlush(widget);
		});
		TestWidget updated = asTenant(TENANT_A, () -> widgets.findById(id).orElseThrow());

		assertThat(updated.getCreatedBy()).isEqualTo(ALICE);
		assertThat(updated.getUpdatedBy()).isEqualTo(BOB);
		assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(updated.getCreatedAt());
		assertThat(updated.getCreatedAt()).isEqualTo(created.getCreatedAt());
		assertThat(updated.getVersion()).isEqualTo(1);
	}

	@Test
	@DisplayName("work with no signed-in user is recorded against the system actor")
	void systemActorWithoutUser() {
		UUID id = create(TENANT_A, "by-system");

		TestWidget widget = asTenant(TENANT_A, () -> widgets.findById(id).orElseThrow());
		assertThat(widget.getCreatedBy()).isEqualTo(new UUID(0L, 0L));
	}

	@Test
	@DisplayName("a stale write is rejected as an optimistic locking failure")
	void staleWriteIsRejected() {
		UUID id = create(TENANT_A, "contended");
		TestWidget first = asTenant(TENANT_A, () -> widgets.findById(id).orElseThrow());
		TestWidget second = asTenant(TENANT_A, () -> widgets.findById(id).orElseThrow());

		first.setName("by-first");
		asTenant(TENANT_A, () -> widgets.saveAndFlush(first));
		second.setName("by-second");

		assertThatThrownBy(() -> asTenant(TENANT_A, () -> widgets.saveAndFlush(second)))
			.isInstanceOf(ObjectOptimisticLockingFailureException.class);
		assertThat(jdbc.queryForObject("select name from test_widget", String.class)).isEqualTo("by-first");
	}

	@Test
	@DisplayName("a tenant cannot read another tenant's rows by id, query, count or page")
	void tenantsAreIsolatedOnReads() {
		UUID inA = create(TENANT_A, "alpha");
		UUID inB = create(TENANT_B, "beta");

		assertThat(asTenant(TENANT_B, () -> widgets.findById(inA))).isEmpty();
		assertThat(asTenant(TENANT_A, () -> widgets.findById(inA))).isPresent();
		assertThat(asTenant(TENANT_B, () -> widgets.findById(inB))).isPresent();
		assertThat(asTenant(TENANT_A, () -> widgets.findAll())).extracting(TestWidget::getName).containsExactly("alpha");
		assertThat(asTenant(TENANT_B, () -> widgets.count())).isEqualTo(1);
		assertThat(asTenant(TENANT_B, () -> widgets.findByPrefix("al"))).isEmpty();
		assertThat(asTenant(TENANT_B, () -> widgets.findAll(org.springframework.data.domain.PageRequest.of(0, 10))
			.getTotalElements())).isEqualTo(1);
		assertThat(asTenant(TENANT_B, () -> widgets.existsById(inA))).isFalse();
	}

	@Test
	@DisplayName("bulk update and delete only touch the caller's tenant")
	void tenantsAreIsolatedOnBulkWrites() {
		create(TENANT_A, "alpha");
		create(TENANT_B, "beta");

		int updated = asTenant(TENANT_B, () -> widgets.renameAll("changed"));
		assertThat(updated).isEqualTo(1);
		assertThat(jdbc.queryForObject("select name from test_widget where tenant_id = ?", String.class,
				uuidBytes(TENANT_A))).isEqualTo("alpha");

		asTenant(TENANT_B, () -> widgets.deleteAllRows());
		assertThat(jdbc.queryForObject("select count(*) from test_widget where tenant_id = ?", Integer.class,
				uuidBytes(TENANT_A))).isEqualTo(1);
	}

	@Test
	@DisplayName("a native query is not filtered by tenant, so it needs its own condition")
	void nativeQueriesBypassTheTenantFilter() {
		create(TENANT_A, "alpha");
		create(TENANT_B, "beta");

		Number unfiltered = asTenant(TENANT_B,
				() -> (Number) entityManager.createNativeQuery("select count(*) from test_widget").getSingleResult());
		Number filtered = asTenant(TENANT_B,
				() -> (Number) entityManager.createNativeQuery("select count(*) from test_widget where tenant_id = ?1")
					.setParameter(1, uuidBytes(TENANT_B))
					.getSingleResult());

		assertThat(unfiltered.intValue()).isEqualTo(2);
		assertThat(filtered.intValue()).isEqualTo(1);
	}

	@Test
	@DisplayName("with no tenant bound, reads find nothing and writes are refused")
	void noTenantFailsClosed() {
		create(TENANT_A, "alpha");

		List<TestWidget> visible = transactions.execute((status) -> widgets.findAll());
		Long counted = transactions.execute((status) -> widgets.count());
		assertThat(visible).isEmpty();
		assertThat(counted).isZero();
		assertThatThrownBy(() -> transactions.execute((status) -> widgets.save(new TestWidget("orphan"))))
			.hasMessageContaining("no tenant is bound");
		assertThat(jdbc.queryForObject("select count(*) from test_widget", Integer.class)).isEqualTo(1);
	}

	@Test
	@DisplayName("deleting hides the row from entity queries but keeps it in the table")
	void softDeleteHidesTheRow() {
		UUID id = create(TENANT_A, "doomed");

		asTenant(TENANT_A, () -> {
			widgets.deleteById(id);
			return null;
		});

		assertThat(asTenant(TENANT_A, () -> widgets.findById(id))).isEmpty();
		assertThat(asTenant(TENANT_A, () -> widgets.findAll())).isEmpty();
		assertThat(asTenant(TENANT_A, () -> widgets.count())).isZero();
		assertThat(jdbc.queryForObject("select count(*) from test_widget", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select deleted_at from test_widget", Object.class)).isNotNull();
	}

	@Test
	@DisplayName("a deleted row's name can be used again, a live row's name cannot")
	void uniqueKeyIgnoresDeletedRows() {
		UUID id = create(TENANT_A, "unique-name");
		assertThatThrownBy(() -> create(TENANT_A, "unique-name")).isInstanceOf(DataIntegrityViolationException.class);

		asTenant(TENANT_A, () -> {
			widgets.deleteById(id);
			return null;
		});
		UUID recreated = create(TENANT_A, "unique-name");

		assertThat(recreated).isNotEqualTo(id);
		assertThat(jdbc.queryForObject("select count(*) from test_widget", Integer.class)).isEqualTo(2);
		// The same name in another tenant is not a clash.
		assertThat(create(TENANT_B, "unique-name")).isNotNull();
	}

	@Test
	@DisplayName("a deleted row can be restored with explicit SQL, which entity queries then see again")
	void restoreNeedsNativeSql() {
		UUID id = create(TENANT_A, "restorable");
		asTenant(TENANT_A, () -> {
			widgets.deleteById(id);
			return null;
		});

		int restored = asTenant(TENANT_A, () -> entityManager
			.createNativeQuery("update test_widget set deleted_at = null where id = ?1 and tenant_id = ?2 "
					+ "and deleted_at is not null")
			.setParameter(1, uuidBytes(id))
			.setParameter(2, uuidBytes(TENANT_A))
			.executeUpdate());

		assertThat(restored).isEqualTo(1);
		assertThat(asTenant(TENANT_A, () -> widgets.findById(id))).isPresent();
		assertThat(List.of(asTenant(TENANT_B, () -> widgets.findById(id)))).allMatch(java.util.Optional::isEmpty);
	}

	private static byte[] uuidBytes(UUID id) {
		java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(16);
		buffer.putLong(id.getMostSignificantBits());
		buffer.putLong(id.getLeastSignificantBits());
		return buffer.array();
	}

}
