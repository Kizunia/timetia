package com.kibootia.backend.access.persistence;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.kibootia.backend.platform.authorization.Caller;
import com.kibootia.backend.platform.authorization.GrantedScope;
import com.kibootia.backend.platform.authorization.ScopedGrants;
import com.kibootia.backend.platform.persistence.UuidBytes;

/**
 * Where a caller holds a policy-decided permission, from the stored role assignments (Timetia ADR 0004). Each call is
 * one indexed query on the caller's assignments, so a change or a removal applies on the very next request on any
 * instance, with no cache. Only live roles that carry a scope type count, and a deleted role grants nothing.
 */
@Component
class StoredScopedGrants implements ScopedGrants {

	private final JdbcClient jdbc;

	StoredScopedGrants(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<GrantedScope> grantsOf(Caller caller, String action) {
		return List.copyOf(new LinkedHashSet<>(this.jdbc.sql("""
				select r.scope_type as scope_type, ra.scope_id as scope_id
				from role_assignment ra
				join role r on r.id = ra.role_id and r.tenant_id = ra.tenant_id and r.deleted_at is null
				join role_permission rp on rp.role_id = r.id and rp.tenant_id = r.tenant_id
				where ra.tenant_id = :tenant and ra.user_id = :user and r.scope_type is not null
				  and rp.permission = :action
				order by r.scope_type, ra.scope_id
				""")
			.param("tenant", UuidBytes.toBytes(caller.tenantId()))
			.param("user", UuidBytes.toBytes(caller.userId()))
			.param("action", action)
			.query((rs, row) -> grant(rs.getString("scope_type"), rs.getBytes("scope_id")))
			.list()));
	}

	private static GrantedScope grant(String kind, byte[] scopeId) {
		if (GrantedScope.GLOBAL.equals(kind)) {
			return GrantedScope.global();
		}
		UUID id = UuidBytes.fromBytes(scopeId);
		return GrantedScope.of(kind, id);
	}

}
