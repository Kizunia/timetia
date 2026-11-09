package com.kibootia.backend.access.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.kibootia.backend.platform.authorization.Permission;

/**
 * Every permission the modules declare in code (ADR 0044), collected at startup. The role editor lists from it and the
 * decision point refuses permissions that are not in it, so a typo in {@code @RequiresPermission} is found at startup
 * or on first use instead of silently denying forever.
 */
public final class PermissionRegistry {

	static final String ANY_SUFFIX = "-any";

	private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9]*\\.[a-z][a-z0-9]*(-[a-z0-9]+)*\\.[a-z][a-z0-9]*(-[a-z0-9]+)*");

	private final Map<String, Permission> byName = new LinkedHashMap<>();

	public PermissionRegistry(Collection<Permission> permissions) {
		Set<String> duplicates = new LinkedHashSet<>();
		for (Permission permission : permissions) {
			if (!NAME.matcher(permission.name()).matches()) {
				throw new IllegalStateException("Permission '" + permission.name()
						+ "' is not named <module>.<resource>.<action> in lower-case words joined by hyphens");
			}
			if (this.byName.putIfAbsent(permission.name(), permission) != null) {
				duplicates.add(permission.name());
			}
		}
		if (!duplicates.isEmpty()) {
			throw new IllegalStateException("Permissions declared more than once: " + duplicates);
		}
		for (String name : this.byName.keySet()) {
			if (name.endsWith(ANY_SUFFIX) && !this.byName.containsKey(name.substring(0, name.length() - ANY_SUFFIX.length()))) {
				throw new IllegalStateException("Permission '" + name + "' widens '"
						+ name.substring(0, name.length() - ANY_SUFFIX.length()) + "', which is not declared");
			}
		}
	}

	public Collection<Permission> all() {
		return Collections.unmodifiableCollection(this.byName.values());
	}

	public boolean isRegistered(String name) {
		return this.byName.containsKey(name);
	}

	/** The permissions the built-in {@code user} role holds. */
	public Set<String> userRolePermissions() {
		Set<String> names = new LinkedHashSet<>();
		this.byName.values().stream().filter(Permission::grantedToUserRole).forEach((p) -> names.add(p.name()));
		return Collections.unmodifiableSet(names);
	}

	/** Whether the action has a {@code -any} variant, which makes the plain permission mean "records I own". */
	public boolean hasAnyVariant(String action) {
		return this.byName.containsKey(action + ANY_SUFFIX);
	}

}
