package com.kibootia.backend.reference;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.kibootia.backend.TestcontainersConfiguration;
import com.kibootia.backend.academic.AcademicScopes;
import com.kibootia.backend.platform.authorization.ScopeDirectory;
import com.kibootia.backend.platform.fixtures.ScopeDirectoryContract;
import com.kibootia.backend.platform.persistence.UuidBytes;
import com.kibootia.backend.platform.tenancy.TenantContext;

/**
 * Proves the reference module's {@link com.kibootia.backend.reference.persistence.JpaScopeDirectory} meets the rules
 * every {@link ScopeDirectory} must keep, against a real MySQL (integration contract, Timetia ADR 0004). The hooks create
 * places with a chosen id directly in the tables, because production ids are generated; the provider's read path
 * (tenant filtering, soft delete, exact code match) is the real one under test.
 *
 * <p>Each test runs under its own tenant, so nothing a test writes is visible to another, and a foreign-tenant place is
 * written under a second tenant that the reads never bind. The tables are cleared when a fresh directory is asked for.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReferenceScopeDirectoryIT extends ScopeDirectoryContract {

	@Autowired
	private ScopeDirectory directory;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID tenant;

	private TenantContext.Scope tenantBinding;

	@AfterEach
	void unbindTenant() {
		if (this.tenantBinding != null) {
			this.tenantBinding.close();
			this.tenantBinding = null;
		}
	}

	@Override
	protected ScopeDirectory newDirectory() {
		unbindTenant();
		this.jdbc.update("delete from batch");
		this.jdbc.update("delete from division");
		this.jdbc.update("delete from department");
		this.tenant = UUID.randomUUID();
		this.tenantBinding = TenantContext.bind(this.tenant);
		return this.directory;
	}

	@Override
	protected void addPlace(String kind, UUID id, String code) {
		insert(kind, id, code, this.tenant);
	}

	@Override
	protected void deletePlace(String kind, UUID id) {
		this.jdbc.update("update " + table(kind) + " set deleted_at = ? where id = ?",
				LocalDateTime.now(ZoneOffset.UTC), UuidBytes.toBytes(id));
	}

	@Override
	protected void addPlaceInAnotherTenant(String kind, UUID id, String code) {
		insert(kind, id, code, UUID.randomUUID());
	}

	/** Writes a live place with the chosen id. A division and a batch carry a parent id; with no database foreign key in
	 * this slice, a placeholder parent is enough for a read test. */
	private void insert(String kind, UUID id, String code, UUID inTenant) {
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
		byte[] system = UuidBytes.toBytes(new UUID(0L, 0L));
		switch (kind) {
			case AcademicScopes.DEPARTMENT -> this.jdbc.update("""
					insert into department (id, tenant_id, version, created_at, created_by, updated_at, updated_by, code, name)
					values (?, ?, 0, ?, ?, ?, ?, ?, ?)
					""", UuidBytes.toBytes(id), UuidBytes.toBytes(inTenant), now, system, now, system, code, code);
			case AcademicScopes.DIVISION -> this.jdbc.update("""
					insert into division (id, tenant_id, version, created_at, created_by, updated_at, updated_by, code, name, department_id)
					values (?, ?, 0, ?, ?, ?, ?, ?, ?, ?)
					""", UuidBytes.toBytes(id), UuidBytes.toBytes(inTenant), now, system, now, system, code, code,
					UuidBytes.toBytes(UUID.randomUUID()));
			case AcademicScopes.BATCH -> this.jdbc.update("""
					insert into batch (id, tenant_id, version, created_at, created_by, updated_at, updated_by, code, name, division_id)
					values (?, ?, 0, ?, ?, ?, ?, ?, ?, ?)
					""", UuidBytes.toBytes(id), UuidBytes.toBytes(inTenant), now, system, now, system, code, code,
					UuidBytes.toBytes(UUID.randomUUID()));
			default -> throw new IllegalArgumentException("Not a reference kind: " + kind);
		}
	}

	private static String table(String kind) {
		return switch (kind) {
			case AcademicScopes.DEPARTMENT -> "department";
			case AcademicScopes.DIVISION -> "division";
			case AcademicScopes.BATCH -> "batch";
			default -> throw new IllegalArgumentException("Not a reference kind: " + kind);
		};
	}

}
