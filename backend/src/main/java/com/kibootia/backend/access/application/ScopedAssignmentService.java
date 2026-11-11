package com.kibootia.backend.access.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.access.AccessPermissions;
import com.kibootia.backend.audit.AuditEntry;
import com.kibootia.backend.audit.AuditLog;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.authorization.GrantedScope;
import com.kibootia.backend.platform.authorization.RequiresPermission;
import com.kibootia.backend.platform.authorization.ScopeDirectory;
import com.kibootia.backend.platform.directory.UserDirectory;
import com.kibootia.backend.platform.directory.UserSummary;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.errors.ApiFieldError;
import com.kibootia.backend.platform.errors.ProblemCodes;
import com.kibootia.backend.platform.ratelimit.RateLimitCategory;
import com.kibootia.backend.platform.ratelimit.RateLimiter;

/**
 * Who holds which role in which place (Timetia ADR 0004). A role with a scope type carries policy-decided permissions;
 * each assignment of it names the place it applies to, or none for {@code GLOBAL}.
 *
 * <p>The rules, each one a denial a test proves:
 * <ul>
 * <li>Only an administrator assigns such a role, because no editor holds a policy-decided permission as such. This
 * is the administrative authorization of the scoped model: assignment power does not delegate in version 1
 * (Timetia ADR 0003).</li>
 * <li>The role must have a scope type. A platform role is assigned through {@link RoleAssignmentService}.</li>
 * <li>A {@code GLOBAL} role takes no place; every other scope type needs one, and the place must exist. With no
 * {@link ScopeDirectory} to ask, a place cannot be validated, so it cannot be assigned (fail closed).</li>
 * </ul>
 */
@Service
public class ScopedAssignmentService {

	public static final String SCOPE_UNVALIDATED = "access.scope_unvalidated";

	private final RoleStore roles;

	private final BuiltInRoles builtIns;

	private final UserDirectory users;

	private final Authorization authorization;

	private final AuditLog auditLog;

	private final RateLimiter rateLimiter;

	private final ObjectProvider<ScopeDirectory> directory;

	ScopedAssignmentService(RoleStore roles, BuiltInRoles builtIns, UserDirectory users, Authorization authorization,
			AuditLog auditLog, RateLimiter rateLimiter, ObjectProvider<ScopeDirectory> directory) {
		this.roles = roles;
		this.builtIns = builtIns;
		this.users = users;
		this.authorization = authorization;
		this.auditLog = auditLog;
		this.rateLimiter = rateLimiter;
		this.directory = directory;
	}

	/** The roles a user holds in places, by role and place. */
	@Transactional(readOnly = true)
	@RequiresPermission(AccessPermissions.VIEW_ROLES)
	public List<ScopedRole> scopedRolesOf(UUID userId) {
		requireExistingUser(userId);
		return view(userId);
	}

	/**
	 * Makes the user's scoped roles exactly {@code wanted}. Every added and removed assignment is audited with the role,
	 * the scope type and the place; a repeated request changes nothing.
	 *
	 * <p>READ_COMMITTED like the other assignment changes, so what a concurrent commit changed is seen.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	@RequiresPermission(AccessPermissions.ASSIGN_ROLES)
	public List<ScopedRole> replaceScopedRolesOfUser(UUID userId, List<NewScopedRole> wanted) {
		this.rateLimiter.acquire(RateLimitCategory.ADMIN_ACTIONS);
		requireAdministrator();
		requireExistingUser(userId);
		Set<NewScopedRole> requested = new LinkedHashSet<>(wanted);
		Map<UUID, Role> found = new HashMap<>();
		this.roles.findAll(requested.stream().map(NewScopedRole::roleId).distinct().toList())
			.forEach((role) -> found.put(role.id(), role));
		validate(requested, found);

		Set<RoleStore.ScopedAssignment> target = new LinkedHashSet<>();
		requested.forEach((assignment) -> target.add(new RoleStore.ScopedAssignment(assignment.roleId(), assignment.scopeId())));
		List<RoleStore.ScopedAssignment> current = this.roles.scopedAssignmentsOf(userId);
		Map<UUID, Role> everyRole = new HashMap<>(found);
		this.roles.findAll(current.stream().map(RoleStore.ScopedAssignment::roleId).distinct().toList())
			.forEach((role) -> everyRole.putIfAbsent(role.id(), role));

		UUID actor = this.authorization.currentUserId();
		for (RoleStore.ScopedAssignment assignment : target) {
			if (!current.contains(assignment)) {
				this.roles.assign(userId, assignment.roleId(), assignment.scopeId(), actor);
				this.auditLog.record(entry("access.role.assigned", userId, everyRole.get(assignment.roleId()), assignment));
			}
		}
		for (RoleStore.ScopedAssignment assignment : current) {
			if (!target.contains(assignment)) {
				this.roles.unassign(userId, assignment.roleId(), assignment.scopeId());
				this.auditLog.record(entry("access.role.unassigned", userId, everyRole.get(assignment.roleId()), assignment));
			}
		}
		return view(userId);
	}

	// ---------------------------------------------------------------------------------------------------------------

	private void validate(Set<NewScopedRole> requested, Map<UUID, Role> found) {
		List<ApiFieldError> errors = new ArrayList<>();
		ScopeDirectory scopes = null;
		for (NewScopedRole assignment : requested) {
			Role role = found.get(assignment.roleId());
			if (role == null) {
				errors.add(new ApiFieldError("assignments", "unknown_role", "There is no role " + assignment.roleId()));
			}
			else if (!role.isScoped()) {
				errors.add(new ApiFieldError("assignments", "role_not_scoped",
						"'" + role.name() + "' has no scope type; give it with the roles of the user"));
			}
			else if (GrantedScope.GLOBAL.equals(role.scopeType())) {
				if (assignment.scopeId() != null) {
					errors.add(new ApiFieldError("assignments", "scope_not_allowed",
							"'" + role.name() + "' applies everywhere and takes no scope"));
				}
			}
			else if (assignment.scopeId() == null) {
				errors.add(new ApiFieldError("assignments", "scope_required",
						"'" + role.name() + "' needs a " + role.scopeType().toLowerCase() + " to apply to"));
			}
			else {
				if (scopes == null) {
					// Fail closed: with nothing to ask, a place cannot be checked, so no role can be given in one.
					scopes = this.directory.getIfAvailable();
					if (scopes == null) {
						throw ApiException.conflict(SCOPE_UNVALIDATED,
								"Scopes cannot be checked yet, because nothing provides them. Only global roles can be given.");
					}
				}
				if (!scopes.exists(role.scopeType(), assignment.scopeId())) {
					errors.add(new ApiFieldError("assignments", "unknown_scope",
							"There is no " + role.scopeType().toLowerCase() + " " + assignment.scopeId()));
				}
			}
		}
		if (!errors.isEmpty()) {
			throw ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.", errors);
		}
	}

	/**
	 * A role that carries policy-decided permissions is given by an administrator: no one else holds those permissions as
	 * such, so no one else may give them (Timetia ADR 0004).
	 */
	private void requireAdministrator() {
		UUID caller = this.authorization.currentUserId();
		if (!this.roles.roleIdsOf(caller).contains(this.builtIns.admin().id())) {
			throw new AccessDeniedException("Only an administrator can give a role that applies within a scope");
		}
	}

	private List<ScopedRole> view(UUID userId) {
		List<RoleStore.ScopedAssignment> assignments = this.roles.scopedAssignmentsOf(userId);
		Map<UUID, Role> byId = new HashMap<>();
		this.roles.findAll(assignments.stream().map(RoleStore.ScopedAssignment::roleId).distinct().toList())
			.forEach((role) -> byId.put(role.id(), role));
		return assignments.stream()
			.map((assignment) -> new ScopedRole(assignment.roleId(), byId.get(assignment.roleId()).name(),
					byId.get(assignment.roleId()).scopeType(), assignment.scopeId()))
			.toList();
	}

	private static AuditEntry entry(String action, UUID userId, Role role, RoleStore.ScopedAssignment assignment) {
		AuditEntry entry = AuditEntry.of(action, "user", userId)
			.withChange("role", role.name())
			.withChange("scopeType", role.scopeType());
		return (assignment.scopeId() == null) ? entry : entry.withChange("scopeId", assignment.scopeId().toString());
	}

	private void requireExistingUser(UUID userId) {
		UserSummary user = this.users.find(List.of(userId)).get(userId);
		if (user == null || user.state() == UserSummary.State.DELETED) {
			throw ApiException.notFound(RoleAssignmentService.USER_NOT_FOUND, "There is no user with that ID.");
		}
	}

	/**
	 * A role assignment as it is shown: the role, its scope type and the place; {@code scopeId} is null for a global
	 * role.
	 */
	public record ScopedRole(UUID roleId, String roleName, String scopeType, UUID scopeId) {

	}

	/** One assignment a caller asks for; {@code scopeId} is null for a global role. */
	public record NewScopedRole(UUID roleId, UUID scopeId) {

	}

}
