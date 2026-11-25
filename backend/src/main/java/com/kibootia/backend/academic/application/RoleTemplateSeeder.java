package com.kibootia.backend.academic.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.kibootia.backend.academic.domain.AcademicRoles;
import com.kibootia.backend.access.RoleTemplates;
import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.tenancy.TenantContext;

/**
 * Creates the Timetia roles at startup in the default tenant (Timetia ADR 0004). A role that already exists is left
 * alone, so an administrator's edits to a seeded role are never undone; a permission added to a template later reaches
 * a role that already exists only when an administrator adds it.
 */
@Component
public class RoleTemplateSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(RoleTemplateSeeder.class);

	private final RoleTemplates templates;

	private final TransactionTemplate transactions;

	RoleTemplateSeeder(RoleTemplates templates, PlatformTransactionManager transactionManager) {
		this.templates = templates;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	@Override
	@NoAuthorization("startup work before any user is signed in")
	public void run(ApplicationArguments arguments) {
		seed();
	}

	/** Creates the roles that are missing and returns how many it created. Safe to repeat. */
	@NoAuthorization("startup work before any user is signed in")
	public int seed() {
		return TenantContext.callAs(TenantContext.DEFAULT_TENANT, () -> this.transactions.execute((status) -> {
			int created = 0;
			for (AcademicRoles.Template template : AcademicRoles.TEMPLATES) {
				if (this.templates.ensure(template.name(), template.description(), template.scopeType(),
						template.permissions())) {
					created++;
				}
			}
			log.info("The Timetia roles are in place ({} created)", created);
			return created;
		}));
	}

}
