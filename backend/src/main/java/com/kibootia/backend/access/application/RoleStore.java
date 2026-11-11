package com.kibootia.backend.access.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Where roles, what they allow and who holds them are kept. The use cases depend on this port, never on JPA or SQL
 * (ADR 0003). Everything acts on the current tenant. Taking a permission off a role and taking a role off a user delete
 * the row; the audit log keeps the history (ADR 0050).
 */
public interface RoleStore {

	/** Creates a role. {@code scopeType} is null for a platform role (Timetia ADR 0004). */
	Role insert(String name, String description, String scopeType, Set<String> permissions);

	Optional<Role> find(UUID id);

	Optional<Role> findBuiltIn(Role.BuiltIn which);

	Page<Role> list(Pageable pageable);

	List<Role> findAll(Collection<UUID> ids);

	boolean nameInUse(String name, UUID except);

	/**
	 * Changes a role if it is still at {@code expectedVersion}, otherwise fails with an optimistic locking failure. A
	 * change to the permissions alone also advances the version.
	 */
	Role update(UUID id, long expectedVersion, String name, String description, Set<String> permissions);

	/** Marks the role deleted. The caller has checked that nobody holds it. */
	void softDelete(UUID id);

	/** Permanently removes roles deleted before the cutoff, with what they allowed. Returns how many. */
	int purgeDeletedBefore(java.time.Instant cutoff);

	/** Creates the two built-in roles if they are missing and offers the user role any permission it has not seen. */
	void ensureBuiltInRoles(Set<String> userRolePermissions);

	/** Locks the role's row until the transaction ends, so concurrent changes to who holds it take turns. */
	void lock(UUID roleId);

	/** The platform roles the user holds: roles with no scope type. Roles with a scope type are in {@link #scopedAssignmentsOf}. */
	Set<UUID> roleIdsOf(UUID userId);

	/** The roles with a scope type that the user holds, one per role and place (Timetia ADR 0004). */
	List<ScopedAssignment> scopedAssignmentsOf(UUID userId);

	/** The role IDs of several users at once, without a place. A user who holds no role is absent from the map. */
	java.util.Map<UUID, Set<UUID>> roleIdsOfAll(Collection<UUID> userIds);

	/** The IDs of every user who holds the role, in any place. */
	List<UUID> holdersOf(UUID roleId);

	/** Gives the role without a place. */
	void assign(UUID userId, UUID roleId, UUID assignedBy);

	/** Gives the role in a place; {@code scopeId} is null for the global scope. */
	void assign(UUID userId, UUID roleId, UUID scopeId, UUID assignedBy);

	/** Takes the role away, without a place. */
	void unassign(UUID userId, UUID roleId);

	/** Takes the role away in one place; {@code scopeId} is null for the global scope. */
	void unassign(UUID userId, UUID roleId, UUID scopeId);

	void unassignAll(UUID userId);

	/** A role held in a place; {@code scopeId} is null for a role whose scope type is GLOBAL. */
	record ScopedAssignment(UUID roleId, UUID scopeId) {

	}

}
