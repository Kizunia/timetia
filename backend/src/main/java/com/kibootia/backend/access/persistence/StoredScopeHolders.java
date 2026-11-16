package com.kibootia.backend.access.persistence;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.kibootia.backend.access.ScopeHolders;
import com.kibootia.backend.platform.persistence.UuidBytes;
import com.kibootia.backend.platform.tenancy.TenantContext;

/** Reads who holds a scoped role in a place from the stored assignments, in one indexed query. */
@Component
class StoredScopeHolders implements ScopeHolders {

	private final JdbcClient jdbc;

	StoredScopeHolders(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Map<UUID, Set<UUID>> usersIn(String scopeType, Collection<UUID> scopeIds) {
		if (scopeIds.isEmpty()) {
			return Map.of();
		}
		List<byte[]> places = scopeIds.stream().distinct().map(UuidBytes::toBytes).toList();
		Map<UUID, Set<UUID>> holders = new HashMap<>();
		this.jdbc.sql("""
				select ra.scope_id as scope_id, ra.user_id as user_id
				from role_assignment ra
				join role r on r.id = ra.role_id and r.tenant_id = ra.tenant_id and r.deleted_at is null
				where ra.tenant_id = :tenant and r.scope_type = :type and ra.scope_id in (:places)
				""")
			.param("tenant", UuidBytes.toBytes(TenantContext.require()))
			.param("type", scopeType)
			.param("places", places)
			.query((rs, row) -> {
				holders.computeIfAbsent(UuidBytes.fromBytes(rs.getBytes("scope_id")), (place) -> new HashSet<>())
					.add(UuidBytes.fromBytes(rs.getBytes("user_id")));
				return row;
			})
			.list();
		return holders;
	}

	@Override
	public Map<UUID, Set<UUID>> usersHolding(String scopeType, Collection<UUID> scopeIds, String permission) {
		if (scopeIds.isEmpty()) {
			return Map.of();
		}
		List<byte[]> places = scopeIds.stream().distinct().map(UuidBytes::toBytes).toList();
		Map<UUID, Set<UUID>> holders = new HashMap<>();
		// The same places as usersIn, narrowed to the roles that carry the permission (role_permission, which is where
		// StoredScopedGrants reads what a scoped role grants).
		this.jdbc.sql("""
				select distinct ra.scope_id as scope_id, ra.user_id as user_id
				from role_assignment ra
				join role r on r.id = ra.role_id and r.tenant_id = ra.tenant_id and r.deleted_at is null
				join role_permission rp on rp.role_id = r.id and rp.tenant_id = r.tenant_id
				where ra.tenant_id = :tenant and r.scope_type = :type and ra.scope_id in (:places)
				  and rp.permission = :permission
				""")
			.param("tenant", UuidBytes.toBytes(TenantContext.require()))
			.param("type", scopeType)
			.param("places", places)
			.param("permission", permission)
			.query((rs, row) -> {
				holders.computeIfAbsent(UuidBytes.fromBytes(rs.getBytes("scope_id")), (place) -> new HashSet<>())
					.add(UuidBytes.fromBytes(rs.getBytes("user_id")));
				return row;
			})
			.list();
		return holders;
	}

}
