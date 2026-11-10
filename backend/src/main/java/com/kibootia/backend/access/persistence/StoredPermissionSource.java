package com.kibootia.backend.access.persistence;

import java.util.Set;
import java.util.TreeSet;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.kibootia.backend.access.application.Role;
import com.kibootia.backend.access.domain.PermissionRegistry;
import com.kibootia.backend.access.domain.PermissionSource;
import com.kibootia.backend.platform.authorization.Caller;
import com.kibootia.backend.platform.persistence.UuidBytes;

/**
 * A caller's permissions from the roles stored in the database (ADR 0025, ADR 0044). Each call is one indexed query on
 * the caller's assignments, so a role change, a removal or a disabled account applies on the very next request, on any
 * number of application instances, with no cache to invalidate. The administrator role has no permission rows: it
 * holds every permission the modules declare.
 *
 * <p>A stored permission that no module declares any more is ignored, so removing a module cannot leave a role
 * pointing at something that does not exist.
 */
@Component
class StoredPermissionSource implements PermissionSource {

	private final JdbcClient jdbc;

	private final PermissionRegistry registry;

	StoredPermissionSource(JdbcClient jdbc, PermissionRegistry registry) {
		this.jdbc = jdbc;
		this.registry = registry;
	}

	@Override
	public Set<String> permissionsOf(Caller caller) {
		Set<String> permissions = new TreeSet<>();
		boolean[] administrator = { false };
		this.jdbc.sql("""
				select r.built_in as built_in, rp.permission as permission
				from role_assignment ra
				join role r on r.id = ra.role_id and r.tenant_id = ra.tenant_id and r.deleted_at is null
				left join role_permission rp on rp.role_id = r.id and rp.tenant_id = r.tenant_id
				where ra.tenant_id = :tenant and ra.user_id = :user
				""")
			.param("tenant", UuidBytes.toBytes(caller.tenantId()))
			.param("user", UuidBytes.toBytes(caller.userId()))
			.query((rs, row) -> {
				if (Role.BuiltIn.ADMIN.name().equals(rs.getString("built_in"))) {
					administrator[0] = true;
				}
				String permission = rs.getString("permission");
				if (permission != null && this.registry.isRegistered(permission)) {
					permissions.add(permission);
				}
				return null;
			})
			.list();
		if (administrator[0]) {
			this.registry.all().forEach((permission) -> permissions.add(permission.name()));
		}
		return permissions;
	}

}
