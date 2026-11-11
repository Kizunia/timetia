package com.kibootia.backend.access.application;

import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.access.RoleTemplates;
import com.kibootia.backend.access.domain.PermissionRegistry;
import com.kibootia.backend.access.domain.ScopeKinds;
import com.kibootia.backend.platform.authorization.NoAuthorization;

/** Creates the roles a module seeds at startup (Timetia ADR 0004), and leaves existing ones as they are. */
@Service
class RoleTemplateService implements RoleTemplates {

	private final RoleStore roles;

	private final PermissionRegistry registry;

	private final ScopeKinds scopeKinds;

	RoleTemplateService(RoleStore roles, PermissionRegistry registry, ScopeKinds scopeKinds) {
		this.roles = roles;
		this.registry = registry;
		this.scopeKinds = scopeKinds;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	@NoAuthorization("startup seeding by a module, before any user is signed in")
	public boolean ensure(String name, String description, String scopeType, Set<String> permissions) {
		if (scopeType != null && !this.scopeKinds.isKnown(scopeType)) {
			throw new IllegalArgumentException("'" + scopeType + "' is not a declared scope kind");
		}
		Set<String> declared = new TreeSet<>();
		for (String permission : permissions) {
			if (!this.registry.isRegistered(permission)) {
				continue;
			}
			if (this.registry.isPolicyDecided(permission) != (scopeType != null)) {
				throw new IllegalArgumentException("Role '" + name + "' cannot hold '" + permission
						+ "': a role with a scope type holds only policy-decided permissions, and one that holds a "
						+ "policy-decided permission needs a scope type");
			}
			declared.add(permission);
		}
		if (this.roles.nameInUse(name, null)) {
			return false;
		}
		this.roles.insert(name, description, scopeType, declared);
		return true;
	}

}
