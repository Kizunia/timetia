package com.kibootia.backend.access.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.access.AccessPermissions;
import com.kibootia.backend.access.domain.PermissionRegistry;
import com.kibootia.backend.audit.AuditEntry;
import com.kibootia.backend.audit.AuditLog;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.authorization.Permission;
import com.kibootia.backend.platform.authorization.RequiresPermission;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.errors.ApiFieldError;
import com.kibootia.backend.platform.errors.ProblemCodes;
import com.kibootia.backend.platform.ratelimit.RateLimitCategory;
import com.kibootia.backend.platform.ratelimit.RateLimiter;

/**
 * The use cases for roles (ADR 0025, ADR 0044). Every change is audited in its own transaction, and two rules stop role
 * editing from becoming a way to escalate privilege: an editor can only give a role permissions they hold themselves,
 * and the administrator role cannot be edited at all.
 */
@Service
public class RoleService {

	public static final String NOT_FOUND = "access.role_not_found";

	public static final String NAME_IN_USE = "access.role_name_in_use";

	public static final String PROTECTED = "access.role_protected";

	public static final String IN_USE = "access.role_in_use";

	/** No '@', so a role name written into the audit log can never look like an email address (ADR 0046). */
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 ._-]{0,78}[A-Za-z0-9]|[A-Za-z0-9]");

	/** The audit log keeps a value to 200 characters; a long list of permissions is summarised as a count. */
	private static final int AUDIT_VALUE_LIMIT = 200;

	private final RoleStore roles;

	private final PermissionRegistry registry;

	private final Authorization authorization;

	private final AuditLog auditLog;

	private final RateLimiter rateLimiter;

	RoleService(RoleStore roles, PermissionRegistry registry, Authorization authorization, AuditLog auditLog,
			RateLimiter rateLimiter) {
		this.roles = roles;
		this.registry = registry;
		this.authorization = authorization;
		this.auditLog = auditLog;
		this.rateLimiter = rateLimiter;
	}

	/** Every permission the modules declare, for the role editor. */
	@RequiresPermission(AccessPermissions.VIEW_ROLES)
	public List<Permission> permissions() {
		return List.copyOf(this.registry.all());
	}

	@Transactional(readOnly = true)
	@RequiresPermission(AccessPermissions.VIEW_ROLES)
	public Page<Role> list(Pageable pageable) {
		return this.roles.list(pageable).map(this::withEffectivePermissions);
	}

	@Transactional(readOnly = true)
	@RequiresPermission(AccessPermissions.VIEW_ROLES)
	public Role get(UUID id) {
		return withEffectivePermissions(this.roles.find(id).orElseThrow(RoleService::notFound));
	}

	@Transactional
	@RequiresPermission(AccessPermissions.MANAGE_ROLES)
	public Role create(NewRole role) {
		this.rateLimiter.acquire(RateLimitCategory.ADMIN_ACTIONS);
		String name = validName(role.name());
		Set<String> permissions = validPermissions(role.permissions());
		requireGrantable(permissions);
		if (this.roles.nameInUse(name, null)) {
			throw nameInUse();
		}
		Role created = this.roles.insert(name, role.description().strip(), permissions);
		this.auditLog.record(AuditEntry.of("access.role.created", "role", created.id())
			.withChange("roleName", name)
			.withChange("permissions", summarise(permissions, Set.of())));
		return created;
	}

	@Transactional
	@RequiresPermission(AccessPermissions.MANAGE_ROLES)
	public Role update(UUID id, RoleChanges changes) {
		this.rateLimiter.acquire(RateLimitCategory.ADMIN_ACTIONS);
		Role current = this.roles.find(id).orElseThrow(RoleService::notFound);
		if (current.builtIn() == Role.BuiltIn.ADMIN) {
			throw protectedRole("The administrator role holds every permission and cannot be edited.");
		}
		String name = validName(changes.name());
		if (current.builtIn() == Role.BuiltIn.USER && !name.equals(current.name())) {
			throw protectedRole("The user role cannot be renamed.");
		}
		Set<String> permissions = validPermissions(changes.permissions());
		Set<String> added = difference(permissions, current.permissions());
		Set<String> removed = difference(current.permissions(), permissions);
		requireGrantable(added);
		if (!name.equals(current.name()) && this.roles.nameInUse(name, id)) {
			throw nameInUse();
		}
		Role updated = this.roles.update(id, changes.expectedVersion(), name, changes.description().strip(), permissions);
		AuditEntry entry = AuditEntry.of("access.role.updated", "role", id).withChange("roleName", name);
		if (!added.isEmpty() || !removed.isEmpty()) {
			entry = entry.withChange("permissions", summarise(added, removed));
		}
		this.auditLog.record(entry);
		return updated;
	}

	@Transactional
	@RequiresPermission(AccessPermissions.MANAGE_ROLES)
	public void delete(UUID id) {
		this.rateLimiter.acquire(RateLimitCategory.ADMIN_ACTIONS);
		Role role = this.roles.find(id).orElseThrow(RoleService::notFound);
		if (role.isBuiltIn()) {
			throw protectedRole("The built-in roles cannot be deleted.");
		}
		if (role.assignedUsers() > 0) {
			throw ApiException.conflict(IN_USE, "Users still hold this role. Take it away from them first.");
		}
		this.roles.softDelete(id);
		this.auditLog.record(AuditEntry.of("access.role.deleted", "role", id).withChange("roleName", role.name()));
	}

	/** The administrator role holds every declared permission without rows of its own, so the editor shows them all. */
	private Role withEffectivePermissions(Role role) {
		if (role.builtIn() != Role.BuiltIn.ADMIN) {
			return role;
		}
		Set<String> all = new TreeSet<>();
		this.registry.all().forEach((permission) -> all.add(permission.name()));
		return new Role(role.id(), role.version(), role.name(), role.description(), role.builtIn(), all,
				role.assignedUsers(), role.createdAt(), role.updatedAt());
	}

	/** A role can only be given what its editor holds, or editing roles would be a way to gain permissions. */
	private void requireGrantable(Set<String> permissions) {
		for (String permission : permissions) {
			this.authorization.require(permission);
		}
	}

	private String validName(String name) {
		String stripped = (name == null) ? "" : name.strip();
		if (!NAME.matcher(stripped).matches()) {
			throw ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.",
					List.of(new ApiFieldError("name", "invalid_format",
							"must be 1 to 80 letters, digits, spaces, dots, dashes or underscores")));
		}
		return stripped;
	}

	private Set<String> validPermissions(Set<String> requested) {
		Set<String> permissions = new TreeSet<>();
		List<ApiFieldError> errors = new ArrayList<>();
		for (String name : (requested == null) ? Set.<String>of() : requested) {
			if (this.registry.isRegistered(name)) {
				permissions.add(name);
			}
			else {
				errors.add(new ApiFieldError("permissions", "unknown_permission", "'" + name + "' is not a permission"));
			}
		}
		if (!errors.isEmpty()) {
			throw ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.", errors);
		}
		return permissions;
	}

	private static Set<String> difference(Set<String> from, Set<String> minus) {
		Set<String> result = new TreeSet<>(from);
		result.removeAll(minus);
		return result;
	}

	/** {@code +a,+b,-c}, or a count when that would not fit in an audit value. */
	static String summarise(Set<String> added, Set<String> removed) {
		List<String> parts = new ArrayList<>();
		added.forEach((name) -> parts.add("+" + name));
		removed.forEach((name) -> parts.add("-" + name));
		String text = String.join(",", parts);
		if (text.length() <= AUDIT_VALUE_LIMIT) {
			return text.isEmpty() ? "none" : text;
		}
		return "+" + added.size() + " -" + removed.size() + " permissions";
	}

	private static ApiException notFound() {
		return ApiException.notFound(NOT_FOUND, "There is no role with that ID.");
	}

	private static ApiException nameInUse() {
		return ApiException.conflict(NAME_IN_USE, "Another role already has this name.");
	}

	private static ApiException protectedRole(String message) {
		return ApiException.conflict(PROTECTED, message);
	}

}
