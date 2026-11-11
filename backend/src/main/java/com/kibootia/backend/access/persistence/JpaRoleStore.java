package com.kibootia.backend.access.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.kibootia.backend.access.application.Role;
import com.kibootia.backend.access.application.RoleStore;
import com.kibootia.backend.platform.persistence.UuidBytes;
import com.kibootia.backend.platform.tenancy.TenantContext;

/**
 * Keeps roles in MySQL. A role is a JPA entity, with the conventions of every business table. What it allows and who
 * holds it are rows in link tables, written with SQL that names the tenant itself (ADR 0028): there is nothing in them
 * worth a version or a soft delete, and the audit log is their history (ADR 0050).
 */
@Repository
class JpaRoleStore implements RoleStore {

	private static final String ADMIN_DESCRIPTION = "Holds every permission. Cannot be edited or deleted.";

	private static final String USER_DESCRIPTION = "Given to everyone who signs in.";

	/** The scope id of an assignment that has no place (migration V202611121000). */
	static final byte[] NO_SCOPE = new byte[16];

	private final RoleRepository roles;

	private final JdbcClient jdbc;

	private final EntityManager entityManager;

	private final Clock clock;

	JpaRoleStore(RoleRepository roles, JdbcClient jdbc, EntityManager entityManager, Clock clock) {
		this.roles = roles;
		this.jdbc = jdbc;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	@Override
	public Role insert(String name, String description, String scopeType, Set<String> permissions) {
		RoleEntity entity = this.roles.saveAndFlush(new RoleEntity(name, description, null, scopeType));
		insertPermissions(entity.getId(), permissions);
		return toRole(entity, permissions, 0);
	}

	@Override
	public Optional<Role> find(UUID id) {
		return this.roles.findById(id).map((entity) -> toRoles(List.of(entity)).get(0));
	}

	@Override
	public Optional<Role> findBuiltIn(Role.BuiltIn which) {
		return this.roles.findByBuiltIn(which).map((entity) -> toRoles(List.of(entity)).get(0));
	}

	@Override
	public Page<Role> list(Pageable pageable) {
		Page<RoleEntity> page = this.roles.findAll(pageable);
		return new PageImpl<>(toRoles(page.getContent()), pageable, page.getTotalElements());
	}

	@Override
	public List<Role> findAll(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return toRoles(this.roles.findAllByIdIn(ids));
	}

	@Override
	public boolean nameInUse(String name, UUID except) {
		return this.roles.nameInUse(name, except);
	}

	@Override
	public Role update(UUID id, long expectedVersion, String name, String description, Set<String> permissions) {
		RoleEntity entity = this.roles.findById(id).orElseThrow();
		if (entity.getVersion() != expectedVersion) {
			throw new ObjectOptimisticLockingFailureException(RoleEntity.class, id);
		}
		boolean rowChanged = !entity.getName().equals(name) || !entity.getDescription().equals(description);
		entity.change(name, description);
		this.roles.saveAndFlush(entity);
		if (!rowChanged) {
			// Only what the role allows changed, which the entity does not see: advance the version anyway, so another
			// editor who started from the old version is refused. This lock increments at once, so the answer shows the
			// new version; an optimistic force-increment would only do it at commit.
			this.entityManager.lock(entity, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
		}
		replacePermissions(id, permissions);
		return toRoles(List.of(entity)).get(0);
	}

	@Override
	public void softDelete(UUID id) {
		this.roles.findById(id).ifPresent(this.roles::delete);
		this.roles.flush();
	}

	@Override
	public int purgeDeletedBefore(java.time.Instant cutoff) {
		this.entityManager.flush();
		this.entityManager.clear();
		this.jdbc.sql("""
				delete rp from role_permission rp join role r on r.id = rp.role_id and r.tenant_id = rp.tenant_id
				where r.tenant_id = :tenant and r.deleted_at is not null and r.deleted_at < :cutoff
				""")
			.param("tenant", tenant())
			.param("cutoff", LocalDateTime.ofInstant(cutoff, ZoneOffset.UTC))
			.update();
		return this.jdbc
			.sql("delete from role where tenant_id = :tenant and deleted_at is not null and deleted_at < :cutoff")
			.param("tenant", tenant())
			.param("cutoff", LocalDateTime.ofInstant(cutoff, ZoneOffset.UTC))
			.update();
	}

	@Override
	public void ensureBuiltInRoles(Set<String> userRolePermissions) {
		this.roles.findByBuiltIn(Role.BuiltIn.ADMIN)
			.orElseGet(() -> this.roles.save(new RoleEntity("Administrator", ADMIN_DESCRIPTION, Role.BuiltIn.ADMIN, null)));
		RoleEntity user = this.roles.findByBuiltIn(Role.BuiltIn.USER)
			.orElseGet(() -> this.roles.save(new RoleEntity("User", USER_DESCRIPTION, Role.BuiltIn.USER, null)));
		this.roles.flush();
		for (String permission : userRolePermissions) {
			// The seed row records that the user role has been offered this permission. Only the first offer adds it, so
			// an administrator who takes it off is not overruled at the next start.
			int offered = this.jdbc
				.sql("insert ignore into role_permission_seed (tenant_id, permission) values (:tenant, :permission)")
				.param("tenant", tenant())
				.param("permission", permission)
				.update();
			if (offered == 1) {
				insertPermissions(user.getId(), Set.of(permission));
			}
		}
	}

	@Override
	public void lock(UUID roleId) {
		this.roles.lockById(roleId).orElseThrow();
	}

	@Override
	public Set<UUID> roleIdsOf(UUID userId) {
		return new LinkedHashSet<>(this.jdbc
			.sql("""
					select ra.role_id from role_assignment ra
					join role r on r.id = ra.role_id and r.tenant_id = ra.tenant_id
					where ra.tenant_id = :tenant and ra.user_id = :user and r.scope_type is null
					""")
			.param("tenant", tenant())
			.param("user", UuidBytes.toBytes(userId))
			.query((rs, row) -> uuid(rs, "role_id"))
			.list());
	}

	@Override
	public List<ScopedAssignment> scopedAssignmentsOf(UUID userId) {
		return this.jdbc.sql("""
				select ra.role_id, ra.scope_id from role_assignment ra
				join role r on r.id = ra.role_id and r.tenant_id = ra.tenant_id
				where ra.tenant_id = :tenant and ra.user_id = :user and r.scope_type is not null
				order by ra.role_id, ra.scope_id
				""")
			.param("tenant", tenant())
			.param("user", UuidBytes.toBytes(userId))
			.query((rs, row) -> new ScopedAssignment(uuid(rs, "role_id"), scopeOf(rs.getBytes("scope_id"))))
			.list();
	}

	@Override
	public Map<UUID, Set<UUID>> roleIdsOfAll(Collection<UUID> userIds) {
		Map<UUID, Set<UUID>> result = new HashMap<>();
		if (userIds.isEmpty()) {
			return result;
		}
		this.jdbc
			.sql("""
					select ra.user_id, ra.role_id from role_assignment ra
					join role r on r.id = ra.role_id and r.tenant_id = ra.tenant_id
					where ra.tenant_id = :tenant and ra.user_id in (:users) and r.scope_type is null
					""")
			.param("tenant", tenant())
			.param("users", userIds.stream().map(UuidBytes::toBytes).toList())
			.query((rs, row) -> {
				result.computeIfAbsent(uuid(rs, "user_id"), (key) -> new LinkedHashSet<>()).add(uuid(rs, "role_id"));
				return null;
			})
			.list();
		return result;
	}

	@Override
	public List<UUID> holdersOf(UUID roleId) {
		return this.jdbc
			.sql("select user_id from role_assignment where tenant_id = :tenant and role_id = :role")
			.param("tenant", tenant())
			.param("role", UuidBytes.toBytes(roleId))
			.query((rs, row) -> uuid(rs, "user_id"))
			.list();
	}

	@Override
	public void assign(UUID userId, UUID roleId, UUID assignedBy) {
		this.jdbc.sql("""
				insert ignore into role_assignment (tenant_id, user_id, role_id, assigned_at, assigned_by)
				values (:tenant, :user, :role, :at, :by)
				""")
			.param("tenant", tenant())
			.param("user", UuidBytes.toBytes(userId))
			.param("role", UuidBytes.toBytes(roleId))
			.param("at", LocalDateTime.ofInstant(this.clock.instant(), ZoneOffset.UTC))
			.param("by", UuidBytes.toBytes(assignedBy))
			.update();
	}

	@Override
	public void assign(UUID userId, UUID roleId, UUID scopeId, UUID assignedBy) {
		this.jdbc.sql("""
				insert ignore into role_assignment (tenant_id, user_id, role_id, scope_id, assigned_at, assigned_by)
				values (:tenant, :user, :role, :scope, :at, :by)
				""")
			.param("tenant", tenant())
			.param("user", UuidBytes.toBytes(userId))
			.param("role", UuidBytes.toBytes(roleId))
			.param("scope", scope(scopeId))
			.param("at", LocalDateTime.ofInstant(this.clock.instant(), ZoneOffset.UTC))
			.param("by", UuidBytes.toBytes(assignedBy))
			.update();
	}

	@Override
	public void unassign(UUID userId, UUID roleId) {
		unassign(userId, roleId, null);
	}

	@Override
	public void unassign(UUID userId, UUID roleId, UUID scopeId) {
		this.jdbc.sql("""
				delete from role_assignment
				where tenant_id = :tenant and user_id = :user and role_id = :role and scope_id = :scope
				""")
			.param("tenant", tenant())
			.param("user", UuidBytes.toBytes(userId))
			.param("role", UuidBytes.toBytes(roleId))
			.param("scope", scope(scopeId))
			.update();
	}

	@Override
	public void unassignAll(UUID userId) {
		this.jdbc.sql("delete from role_assignment where tenant_id = :tenant and user_id = :user")
			.param("tenant", tenant())
			.param("user", UuidBytes.toBytes(userId))
			.update();
	}

	// ---------------------------------------------------------------------------------------------------------------

	private List<Role> toRoles(List<RoleEntity> entities) {
		if (entities.isEmpty()) {
			return List.of();
		}
		List<byte[]> ids = entities.stream().map((entity) -> UuidBytes.toBytes(entity.getId())).toList();
		Map<UUID, Set<String>> permissions = new HashMap<>();
		this.jdbc
			.sql("select role_id, permission from role_permission where tenant_id = :tenant and role_id in (:ids)")
			.param("tenant", tenant())
			.param("ids", ids)
			.query((rs, row) -> {
				permissions.computeIfAbsent(uuid(rs, "role_id"), (key) -> new TreeSet<>()).add(rs.getString("permission"));
				return null;
			})
			.list();
		Map<UUID, Integer> holders = new HashMap<>();
		this.jdbc.sql("""
				select role_id, count(*) as holders from role_assignment
				where tenant_id = :tenant and role_id in (:ids) group by role_id
				""")
			.param("tenant", tenant())
			.param("ids", ids)
			.query((rs, row) -> {
				holders.put(uuid(rs, "role_id"), rs.getInt("holders"));
				return null;
			})
			.list();
		List<Role> roles = new ArrayList<>();
		for (RoleEntity entity : entities) {
			roles.add(toRole(entity, permissions.getOrDefault(entity.getId(), Set.of()),
					holders.getOrDefault(entity.getId(), 0)));
		}
		return roles;
	}

	private static Role toRole(RoleEntity entity, Set<String> permissions, int assignedUsers) {
		return new Role(entity.getId(), entity.getVersion(), entity.getName(), entity.getDescription(),
				entity.getBuiltIn(), entity.getScopeType(), Set.copyOf(permissions), assignedUsers, entity.getCreatedAt(),
				entity.getUpdatedAt());
	}

	private void insertPermissions(UUID roleId, Set<String> permissions) {
		for (String permission : permissions) {
			this.jdbc.sql("""
					insert ignore into role_permission (tenant_id, role_id, permission)
					values (:tenant, :role, :permission)
					""")
				.param("tenant", tenant())
				.param("role", UuidBytes.toBytes(roleId))
				.param("permission", permission)
				.update();
		}
	}

	private void replacePermissions(UUID roleId, Set<String> permissions) {
		Set<String> current = new TreeSet<>(this.jdbc
			.sql("select permission from role_permission where tenant_id = :tenant and role_id = :role")
			.param("tenant", tenant())
			.param("role", UuidBytes.toBytes(roleId))
			.query((rs, row) -> rs.getString("permission"))
			.list());
		for (String permission : current) {
			if (!permissions.contains(permission)) {
				this.jdbc.sql("""
						delete from role_permission where tenant_id = :tenant and role_id = :role and permission = :permission
						""")
					.param("tenant", tenant())
					.param("role", UuidBytes.toBytes(roleId))
					.param("permission", permission)
					.update();
			}
		}
		Set<String> added = new TreeSet<>(permissions);
		added.removeAll(current);
		insertPermissions(roleId, added);
	}

	/** The place of a stored assignment: null for the all-zero id, which means no place. */
	private static UUID scopeOf(byte[] stored) {
		return java.util.Arrays.equals(stored, NO_SCOPE) ? null : UuidBytes.fromBytes(stored);
	}

	private static byte[] scope(UUID scopeId) {
		return (scopeId == null) ? NO_SCOPE : UuidBytes.toBytes(scopeId);
	}

	private static byte[] tenant() {
		return UuidBytes.toBytes(TenantContext.require());
	}

	private static UUID uuid(ResultSet rs, String column) throws SQLException {
		return UuidBytes.fromBytes(rs.getBytes(column));
	}

}
