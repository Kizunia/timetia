package com.kibootia.backend.access.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.kibootia.backend.access.domain.PermissionRegistry;
import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.tenancy.TenantContext;

/**
 * The administrator and user roles (ADR 0025, ADR 0041). They are created at startup in the default tenant, and the user
 * role is offered every permission a module flags for it that it has not seen yet, once, so an administrator's removals
 * stay removed. Use cases that need one of them call {@link #admin()} or {@link #user()}, which create it first if it is
 * missing, so a database that lost the rows heals itself instead of refusing every sign-in.
 */
@Component
class BuiltInRoles implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(BuiltInRoles.class);

	private final RoleStore roles;

	private final PermissionRegistry registry;

	private final TransactionTemplate transactions;

	BuiltInRoles(RoleStore roles, PermissionRegistry registry, PlatformTransactionManager transactionManager) {
		this.roles = roles;
		this.registry = registry;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	/** Startup: the tenant is bound before the transaction starts, because Hibernate reads it when a session opens. */
	@Override
	@NoAuthorization("startup work before any user is signed in")
	public void run(ApplicationArguments arguments) {
		TenantContext.runAs(TenantContext.DEFAULT_TENANT, () -> this.transactions.executeWithoutResult((status) -> {
			this.roles.ensureBuiltInRoles(this.registry.userRolePermissions());
			log.info("The built-in roles are in place");
		}));
	}

	/** In the caller's transaction and tenant. */
	Role admin() {
		return find(Role.BuiltIn.ADMIN);
	}

	/** In the caller's transaction and tenant. */
	Role user() {
		return find(Role.BuiltIn.USER);
	}

	private Role find(Role.BuiltIn which) {
		return this.roles.findBuiltIn(which).orElseGet(() -> {
			this.roles.ensureBuiltInRoles(this.registry.userRolePermissions());
			return this.roles.findBuiltIn(which).orElseThrow();
		});
	}

}
