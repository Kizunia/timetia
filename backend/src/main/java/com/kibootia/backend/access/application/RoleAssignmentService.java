package com.kibootia.backend.access.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.access.AccessPermissions;
import com.kibootia.backend.access.RoleAssignments;
import com.kibootia.backend.access.RoleSummary;
import com.kibootia.backend.access.domain.PermissionRegistry;
import com.kibootia.backend.audit.AuditEntry;
import com.kibootia.backend.audit.AuditLog;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.authorization.RequiresPermission;
import com.kibootia.backend.platform.directory.UserDirectory;
import com.kibootia.backend.platform.directory.UserSummary;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.errors.ApiFieldError;
import com.kibootia.backend.platform.errors.ProblemCodes;
import com.kibootia.backend.platform.ratelimit.RateLimitCategory;
import com.kibootia.backend.platform.ratelimit.RateLimiter;

/**
 * Who holds which role (ADR 0025, ADR 0041). It serves the role screens of the administration interface and, through
 * {@link RoleAssignments}, the steps of the user lifecycle that {@code identity} drives.
 *
 * <p>The safeguards live here because this module owns the assignments: the administrator role is only given by an
 * administrator, and the last administrator who can still sign in cannot be removed ({@link
 * #ensureNotLastAdministrator}).
 */
@Service
public class RoleAssignmentService implements RoleAssignments {

	public static final String USER_NOT_FOUND = "access.user_not_found";

	public static final String LAST_ADMINISTRATOR = "access.last_administrator";

	private final RoleStore roles;

	private final BuiltInRoles builtIns;

	private final PermissionRegistry registry;

	private final UserDirectory users;

	private final Authorization authorization;

	private final AuditLog auditLog;

	private final RateLimiter rateLimiter;

	RoleAssignmentService(RoleStore roles, BuiltInRoles builtIns, PermissionRegistry registry, UserDirectory users,
			Authorization authorization, AuditLog auditLog, RateLimiter rateLimiter) {
		this.roles = roles;
		this.builtIns = builtIns;
		this.registry = registry;
		this.users = users;
		this.authorization = authorization;
		this.auditLog = auditLog;
		this.rateLimiter = rateLimiter;
	}

	// --- the administration screens -------------------------------------------------------------------------------

	/** The roles a user holds. */
	@Transactional(readOnly = true)
	@RequiresPermission(AccessPermissions.VIEW_ROLES)
	public List<RoleSummary> rolesOfUser(UUID userId) {
		requireExistingUser(userId);
		return rolesOf(userId);
	}

	/**
	 * Makes the user's roles exactly {@code roleIds}. The administrator role can only be given by an administrator, and
	 * taking it from the last active administrator is refused. Every added and removed role is audited.
	 *
	 * <p>READ_COMMITTED so the last-administrator count sees what a concurrent commit changed.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	@RequiresPermission(AccessPermissions.ASSIGN_ROLES)
	public List<RoleSummary> replaceRolesOfUser(UUID userId, Set<UUID> roleIds) {
		this.rateLimiter.acquire(RateLimitCategory.ADMIN_ACTIONS);
		requireExistingUser(userId);
		Map<UUID, Role> wanted = rolesById(roleIds);
		requireUnscoped(wanted.values());
		Set<UUID> current = this.roles.roleIdsOf(userId);
		Role admin = this.builtIns.admin();

		Set<UUID> added = new LinkedHashSet<>(wanted.keySet());
		added.removeAll(current);
		Set<UUID> removed = new LinkedHashSet<>(current);
		removed.removeAll(wanted.keySet());

		if (added.contains(admin.id())) {
			requireAdministrator();
		}
		requireGrantable(added.stream().map(wanted::get).toList());
		if (removed.contains(admin.id())) {
			ensureNotLastAdministrator(userId);
		}
		Map<UUID, Role> removedRoles = rolesById(removed);
		UUID actor = this.authorization.currentUserId();
		for (UUID roleId : added) {
			this.roles.assign(userId, roleId, actor);
			this.auditLog.record(AuditEntry.of("access.role.assigned", "user", userId)
				.withChange("role", wanted.get(roleId).name()));
		}
		for (UUID roleId : removed) {
			this.roles.unassign(userId, roleId);
			this.auditLog.record(AuditEntry.of("access.role.unassigned", "user", userId)
				.withChange("role", removedRoles.get(roleId).name()));
		}
		return rolesOf(userId);
	}

	// --- the contract used by the user lifecycle -------------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	@NoAuthorization("callers authorize; a user reads their own permissions and an administrator reads another's")
	public Set<String> permissionsOf(UUID userId) {
		Set<String> permissions = new TreeSet<>();
		Set<UUID> held = new LinkedHashSet<>(this.roles.roleIdsOf(userId));
		this.roles.scopedAssignmentsOf(userId).forEach((assignment) -> held.add(assignment.roleId()));
		for (Role role : this.roles.findAll(held)) {
			if (role.builtIn() == Role.BuiltIn.ADMIN) {
				this.registry.all()
					.stream()
					.filter((permission) -> !permission.policyDecided())
					.forEach((permission) -> permissions.add(permission.name()));
			}
			else {
				role.permissions().stream().filter(this.registry::isRegistered).forEach(permissions::add);
			}
		}
		return permissions;
	}

	@Override
	@Transactional(readOnly = true)
	@NoAuthorization("callers authorize; the roles are shown to the user or to an administrator")
	public List<RoleSummary> rolesOf(UUID userId) {
		return summaries(this.roles.findAll(this.roles.roleIdsOf(userId)));
	}

	@Override
	@Transactional(readOnly = true)
	@NoAuthorization("callers authorize; used to fill a list an administrator may see")
	public Map<UUID, List<RoleSummary>> rolesOf(Collection<UUID> userIds) {
		Map<UUID, Set<UUID>> ids = this.roles.roleIdsOfAll(userIds);
		Set<UUID> every = new LinkedHashSet<>();
		ids.values().forEach(every::addAll);
		Map<UUID, Role> byId = new HashMap<>();
		this.roles.findAll(every).forEach((role) -> byId.put(role.id(), role));
		Map<UUID, List<RoleSummary>> result = new HashMap<>();
		ids.forEach((user, roleIds) -> result.put(user,
				summaries(roleIds.stream().map(byId::get).filter(java.util.Objects::nonNull).toList())));
		return result;
	}

	@Override
	@Transactional(readOnly = true)
	@NoAuthorization("callers authorize; a safeguard that asks whether an account is an administrator's")
	public boolean holdsAdministratorRole(UUID userId) {
		return isAdministrator(userId);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	@NoAuthorization("the request gate gives every new profile the user role before any use case runs")
	public void assignDefaultRole(UUID userId) {
		Role user = this.builtIns.user();
		if (!this.roles.roleIdsOf(userId).contains(user.id())) {
			this.roles.assign(userId, user.id(), this.authorization.currentUserId());
		}
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void assignInitialRoles(UUID userId, Collection<UUID> roleIds) {
		if (roleIds.isEmpty()) {
			return;
		}
		this.authorization.require(AccessPermissions.ASSIGN_ROLES);
		Map<UUID, Role> wanted = rolesById(new LinkedHashSet<>(roleIds));
		requireUnscoped(wanted.values());
		Role admin = this.builtIns.admin();
		if (wanted.containsKey(admin.id())) {
			requireAdministrator();
		}
		requireGrantable(wanted.values());
		Set<UUID> current = this.roles.roleIdsOf(userId);
		UUID actor = this.authorization.currentUserId();
		for (Role role : wanted.values()) {
			if (!current.contains(role.id())) {
				this.roles.assign(userId, role.id(), actor);
				this.auditLog.record(AuditEntry.of("access.role.assigned", "user", userId).withChange("role", role.name()));
			}
		}
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	@NoAuthorization("bootstrap: identity has checked the setting, the verified email address and that nobody is admin")
	public boolean grantFirstAdministrator(UUID userId) {
		Role admin = this.builtIns.admin();
		this.roles.lock(admin.id());
		if (!this.roles.holdersOf(admin.id()).isEmpty()) {
			return false;
		}
		this.roles.assign(userId, admin.id(), userId);
		return true;
	}

	@Override
	// Not read-only: on a fresh database the built-in roles may not exist yet, and asking for the administrator role
	// creates it (BuiltInRoles heals itself), which is a write.
	@Transactional
	@NoAuthorization("startup: decides whether the bootstrap administrator is still needed; reveals only a yes or no")
	public boolean hasAdministrator() {
		return !this.roles.holdersOf(this.builtIns.admin().id()).isEmpty();
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	@NoAuthorization("a safeguard the calling use case has already authorized")
	public void ensureNotLastAdministrator(UUID userId) {
		Role admin = this.builtIns.admin();
		this.roles.lock(admin.id());
		List<UUID> holders = this.roles.holdersOf(admin.id());
		if (!holders.contains(userId)) {
			return;
		}
		List<UUID> others = holders.stream().filter((holder) -> !holder.equals(userId)).toList();
		boolean anotherCanSignIn = !others.isEmpty()
				&& this.users.find(others).values().stream().anyMatch(UserSummary::active);
		if (!anotherCanSignIn) {
			throw ApiException.conflict(LAST_ADMINISTRATOR,
					"This is the last administrator who can sign in. Make someone else an administrator first.");
		}
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	@NoAuthorization("part of erasing a user, which identity has authorized")
	public void removeAll(UUID userId) {
		this.roles.unassignAll(userId);
	}

	// ---------------------------------------------------------------------------------------------------------------

	private void requireExistingUser(UUID userId) {
		UserSummary user = this.users.find(List.of(userId)).get(userId);
		if (user == null || user.state() == UserSummary.State.DELETED) {
			throw ApiException.notFound(USER_NOT_FOUND, "There is no user with that ID.");
		}
	}

	/** Giving the administrator role is what an administrator does; no other permission is enough. */
	private void requireAdministrator() {
		UUID caller = this.authorization.currentUserId();
		if (!this.roles.roleIdsOf(caller).contains(this.builtIns.admin().id())) {
			throw new AccessDeniedException("Only an administrator can give the administrator role");
		}
	}

	/**
	 * Giving a role gives its permissions, so the giver must hold each of them, as when a role is edited ({@code
	 * RoleService}). Otherwise assigning roles would be a way to gain permissions, including by giving oneself a role. An
	 * administrator holds every permission and may give any role.
	 */
	private void requireGrantable(Collection<Role> given) {
		if (isAdministrator(this.authorization.currentUserId())) {
			return;
		}
		for (Role role : given) {
			for (String permission : role.permissions()) {
				if (this.registry.isRegistered(permission)) {
					this.authorization.require(permission);
				}
			}
		}
	}

	/**
	 * A role with a scope type is assigned within a place, through the scoped-role endpoint; it cannot be given here,
	 * where it would have no place (Timetia ADR 0004).
	 */
	private static void requireUnscoped(Collection<Role> given) {
		List<ApiFieldError> errors = new ArrayList<>();
		for (Role role : given) {
			if (role.isScoped()) {
				errors.add(new ApiFieldError("roleIds", "scope_required",
						"'" + role.name() + "' is assigned within a scope; use the scoped roles of the user"));
			}
		}
		if (!errors.isEmpty()) {
			throw ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.", errors);
		}
	}

	private boolean isAdministrator(UUID userId) {
		return this.roles.roleIdsOf(userId).contains(this.builtIns.admin().id());
	}

	private Map<UUID, Role> rolesById(Set<UUID> ids) {
		Map<UUID, Role> found = new HashMap<>();
		this.roles.findAll(ids).forEach((role) -> found.put(role.id(), role));
		List<ApiFieldError> errors = new ArrayList<>();
		for (UUID id : ids) {
			if (!found.containsKey(id)) {
				errors.add(new ApiFieldError("roleIds", "unknown_role", "There is no role " + id));
			}
		}
		if (!errors.isEmpty()) {
			throw ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.", errors);
		}
		return found;
	}

	/** Built-in roles first, then by name. */
	private static List<RoleSummary> summaries(List<Role> roles) {
		return roles.stream()
			.sorted(Comparator.comparing((Role role) -> !role.isBuiltIn()).thenComparing(Role::name))
			.map((role) -> new RoleSummary(role.id(), role.name(), role.isBuiltIn()))
			.toList();
	}

}
