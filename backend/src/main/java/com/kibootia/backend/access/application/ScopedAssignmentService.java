package com.kibootia.backend.access.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.access.AccessPermissions;
import com.kibootia.backend.access.ScopedAssignments;
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
 * <li>A place is named by its id or by its code, never both. Both are checked against the same directory.</li>
 * </ul>
 *
 * <p>The same rules serve the API (which sets the whole set of a user's scoped roles) and the provisioning import
 * ({@link ScopedAssignments}, which only adds), so a file cannot do what the screen would refuse.
 */
@Service
public class ScopedAssignmentService implements ScopedAssignments {

	private static final Logger log = LoggerFactory.getLogger(ScopedAssignmentService.class);

	public static final String SCOPE_UNVALIDATED = "access.scope_unvalidated";

	public static final String ASSIGNMENT_NOT_FOUND = "access.assignment_not_found";

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
		Set<RoleStore.ScopedAssignment> target = validate(requested, found);

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

	/**
	 * Gives the user one more scoped role and leaves the rest alone. Unlike
	 * {@link #replaceScopedRolesOfUser}, a screen that has not seen a newer change cannot overwrite it. Holding it already
	 * changes nothing and is not an error.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	@RequiresPermission(AccessPermissions.ASSIGN_ROLES)
	public List<ScopedRole> addScopedRole(UUID userId, NewScopedRole wanted) {
		this.rateLimiter.acquire(RateLimitCategory.ADMIN_ACTIONS);
		requireAdministrator();
		requireExistingUser(userId);
		Map<UUID, Role> found = new HashMap<>();
		this.roles.findAll(List.of(wanted.roleId())).forEach((role) -> found.put(role.id(), role));
		for (RoleStore.ScopedAssignment assignment : validate(Set.of(wanted), found)) {
			if (this.roles.assignIfAbsent(userId, assignment.roleId(), assignment.scopeId(),
					this.authorization.currentUserId())) {
				this.auditLog.record(entry("access.role.assigned", userId, found.get(assignment.roleId()), assignment));
			}
		}
		return view(userId);
	}

	/** Takes away one scoped role in one place, and says 404 when the user does not hold it there. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	@RequiresPermission(AccessPermissions.ASSIGN_ROLES)
	public List<ScopedRole> removeScopedRole(UUID userId, UUID roleId, UUID scopeId) {
		this.rateLimiter.acquire(RateLimitCategory.ADMIN_ACTIONS);
		requireAdministrator();
		requireExistingUser(userId);
		RoleStore.ScopedAssignment assignment = new RoleStore.ScopedAssignment(roleId, scopeId);
		Role role = this.roles.find(roleId).orElse(null);
		if (role == null || !this.roles.scopedAssignmentsOf(userId).contains(assignment)) {
			throw ApiException.notFound(ASSIGNMENT_NOT_FOUND, "The user does not hold that role there.");
		}
		this.roles.unassign(userId, roleId, scopeId);
		this.auditLog.record(entry("access.role.unassigned", userId, role, assignment));
		return view(userId);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// The port the provisioning import uses (Timetia ADR 0006)

	@Override
	@Transactional(readOnly = true)
	@RequiresPermission(AccessPermissions.ASSIGN_ROLES)
	public Resolution resolve(String roleName, String scopeCode) {
		requireAdministrator();
		String name = (roleName == null) ? "" : roleName.strip();
		Role role = name.isEmpty() ? null : this.roles.findScopedByName(name).orElse(null);
		if (role == null) {
			boolean platformRole = !name.isEmpty() && this.roles.nameInUse(name, null);
			return platformRole
					? Resolution.refused(Failure.ROLE_NOT_ASSIGNABLE,
							"'" + name + "' is a platform role; a file can only give roles that apply within a scope")
					: Resolution.refused(Failure.UNKNOWN_ROLE, "There is no role with that name");
		}
		Place place = place(role, null, scopeCode);
		return (place.failure() == null)
				? Resolution.resolved(role.id(), role.name(), role.scopeType(), place.scopeId())
				: Resolution.refused(place.failure(), place.message());
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	@RequiresPermission(AccessPermissions.ASSIGN_ROLES)
	public boolean addIfMissing(UUID userId, Resolution resolution) {
		requireAdministrator();
		if (resolution == null || !resolution.isResolved()) {
			throw new IllegalArgumentException("Only a resolved assignment can be added");
		}
		requireExistingUser(userId);
		// Do not trust the resolution: a role may have been deleted, or a place removed, since it was made.
		Role role = this.roles.find(resolution.roleId()).filter(Role::isScoped).orElse(null);
		if (role == null) {
			throw refusal(Failure.UNKNOWN_ROLE, "There is no role " + resolution.roleId());
		}
		Place place = place(role, resolution.scopeId(), null);
		if (place.failure() != null) {
			throw refusal(place.failure(), place.message());
		}
		RoleStore.ScopedAssignment assignment = new RoleStore.ScopedAssignment(role.id(), place.scopeId());
		boolean added = this.roles.assignIfAbsent(userId, role.id(), place.scopeId(), this.authorization.currentUserId());
		if (added) {
			this.auditLog.record(entry("access.role.assigned", userId, role, assignment));
		}
		return added;
	}

	// ---------------------------------------------------------------------------------------------------------------

	/** Checks every requested assignment and returns the set to hold. Collects the field errors of all of them. */
	private Set<RoleStore.ScopedAssignment> validate(Set<NewScopedRole> requested, Map<UUID, Role> found) {
		List<ApiFieldError> errors = new ArrayList<>();
		Set<RoleStore.ScopedAssignment> target = new LinkedHashSet<>();
		for (NewScopedRole assignment : requested) {
			Role role = found.get(assignment.roleId());
			if (role == null) {
				errors.add(new ApiFieldError("assignments", "unknown_role", "There is no role " + assignment.roleId()));
			}
			else if (!role.isScoped()) {
				errors.add(new ApiFieldError("assignments", "role_not_scoped",
						"'" + role.name() + "' has no scope type; give it with the roles of the user"));
			}
			else {
				Place place = place(role, assignment.scopeId(), assignment.scopeCode());
				if (place.failure() == Failure.SCOPE_UNVALIDATED) {
					throw refusal(place.failure(), place.message());
				}
				else if (place.failure() != null) {
					errors.add(new ApiFieldError("assignments", code(place.failure()), place.message()));
				}
				else {
					target.add(new RoleStore.ScopedAssignment(role.id(), place.scopeId()));
				}
			}
		}
		if (!errors.isEmpty()) {
			throw ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.", errors);
		}
		return target;
	}

	/**
	 * The one rule set for where a role applies. The place is named by {@code scopeId} or by {@code scopeCode} (a blank
	 * code is no code). Returns the place's id (null for a global role) or the rule it broke.
	 */
	private Place place(Role role, UUID scopeId, String scopeCode) {
		boolean named = scopeCode != null && !scopeCode.isBlank();
		if (named && scopeId != null) {
			return Place.refused(Failure.SCOPE_AMBIGUOUS, "Name the place by its id or by its code, not both");
		}
		if (GrantedScope.GLOBAL.equals(role.scopeType())) {
			return (scopeId != null || named)
					? Place.refused(Failure.SCOPE_NOT_ALLOWED, "'" + role.name() + "' applies everywhere and takes no scope")
					: new Place(null, null, null);
		}
		if (scopeId == null && !named) {
			return Place.refused(Failure.SCOPE_REQUIRED,
					"'" + role.name() + "' needs a " + role.scopeType().toLowerCase(Locale.ROOT) + " to apply to");
		}
		// Fail closed: with nothing to ask, a place cannot be checked, so no role can be given in one.
		ScopeDirectory scopes = this.directory.getIfAvailable();
		if (scopes == null) {
			return Place.refused(Failure.SCOPE_UNVALIDATED,
					"Scopes cannot be checked yet, because nothing provides them. Only global roles can be given.");
		}
		try {
			UUID id = scopeId;
			if (named) {
				id = scopes.idOf(role.scopeType(), scopeCode).orElse(null);
				if (id == null) {
					return Place.refused(Failure.UNKNOWN_SCOPE,
							"There is no " + role.scopeType().toLowerCase(Locale.ROOT) + " with that code");
				}
			}
			if (!scopes.exists(role.scopeType(), id)) {
				return Place.refused(Failure.UNKNOWN_SCOPE,
						"There is no " + role.scopeType().toLowerCase(Locale.ROOT) + " " + id);
			}
			return new Place(id, null, null);
		}
		catch (RuntimeException ex) {
			// Fail closed: a directory that cannot answer is the same as one that is missing. Never accept, never a 500.
			// Only the exception type is logged: a message could carry the code that was asked about.
			log.warn("The scope directory could not answer ({}), so the place was not accepted", ex.getClass().getName());
			return Place.refused(Failure.SCOPE_UNVALIDATED,
					"Scopes cannot be checked right now. Try again later; only global roles can be given until then.");
		}
	}

	/** 409 when places cannot be checked at all; otherwise 400 with the rule that was broken. */
	private static ApiException refusal(Failure failure, String message) {
		if (failure == Failure.SCOPE_UNVALIDATED) {
			return ApiException.conflict(SCOPE_UNVALIDATED, message);
		}
		return ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.",
				List.of(new ApiFieldError("assignments", code(failure), message)));
	}

	private static String code(Failure failure) {
		return failure.name().toLowerCase(Locale.ROOT);
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
		Map<String, Map<UUID, String>> codes = codesOf(assignments, byId);
		return assignments.stream().map((assignment) -> {
			Role role = byId.get(assignment.roleId());
			String code = (assignment.scopeId() == null) ? null
					: codes.getOrDefault(role.scopeType(), Map.of()).get(assignment.scopeId());
			return new ScopedRole(assignment.roleId(), role.name(), role.scopeType(), assignment.scopeId(), code);
		}).toList();
	}

	/** The codes of the places in the assignments, by kind, when something provides places; empty otherwise. */
	private Map<String, Map<UUID, String>> codesOf(List<RoleStore.ScopedAssignment> assignments, Map<UUID, Role> byId) {
		ScopeDirectory scopes = this.directory.getIfAvailable();
		Map<String, Map<UUID, String>> codes = new HashMap<>();
		if (scopes == null) {
			return codes;
		}
		Map<String, Set<UUID>> idsByKind = new HashMap<>();
		for (RoleStore.ScopedAssignment assignment : assignments) {
			if (assignment.scopeId() != null) {
				idsByKind.computeIfAbsent(byId.get(assignment.roleId()).scopeType(), (kind) -> new LinkedHashSet<>())
					.add(assignment.scopeId());
			}
		}
		try {
			idsByKind.forEach((kind, ids) -> codes.put(kind, scopes.codesOf(kind, ids)));
		}
		catch (RuntimeException ex) {
			// Codes are only for showing an assignment; the screen falls back to the id. It never fails a read.
			log.warn("The scope directory could not give codes ({}), so ids are shown", ex.getClass().getName());
			codes.clear();
		}
		return codes;
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

	/** Where a role applies, or the rule that was broken. */
	private record Place(UUID scopeId, Failure failure, String message) {

		static Place refused(Failure failure, String message) {
			return new Place(null, failure, message);
		}

	}

	/**
	 * A role assignment as it is shown: the role, its scope type and the place; {@code scopeId} is null for a global
	 * role, and {@code scopeCode} is null when there is no place or nothing provides the codes.
	 */
	public record ScopedRole(UUID roleId, String roleName, String scopeType, UUID scopeId, String scopeCode) {

	}

	/**
	 * One assignment a caller asks for. The place is {@code scopeId} or {@code scopeCode}, never both; both are null for a
	 * global role.
	 */
	public record NewScopedRole(UUID roleId, UUID scopeId, String scopeCode) {

		public NewScopedRole(UUID roleId, UUID scopeId) {
			this(roleId, scopeId, null);
		}

	}

}
