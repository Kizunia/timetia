package com.kibootia.backend.platform.jobs;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.tenancy.TenantContext;

/**
 * The retention purge (ADR 0034); {@link RetentionPurgeSchedule} runs it daily under a database lock. Each
 * {@link RetentionPurgeable} runs in its own transaction and a failure in one is logged without stopping the others.
 */
@Component
public class RetentionPurgeJob {

	private static final Logger log = LoggerFactory.getLogger(RetentionPurgeJob.class);

	/** Tenants the foundation runs: the single default tenant. A multi-tenant application supplies its own list. */
	private static final List<UUID> TENANTS = List.of(TenantContext.DEFAULT_TENANT);

	private final List<RetentionPurgeable> purgeables;

	private final Authorization authorization;

	private final TransactionTemplate transactions;

	RetentionPurgeJob(List<RetentionPurgeable> purgeables, Authorization authorization,
			PlatformTransactionManager transactionManager) {
		this.purgeables = purgeables;
		this.authorization = authorization;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	/** Runs every purge now and returns the number of rows removed. Used by the schedule and by tests. */
	public int runOnce() {
		int total = 0;
		for (UUID tenant : TENANTS) {
			for (RetentionPurgeable purgeable : this.purgeables) {
				total += purgeInOwnTransaction(tenant, purgeable);
			}
		}
		return total;
	}

	private int purgeInOwnTransaction(UUID tenant, RetentionPurgeable purgeable) {
		try {
			// The tenant is bound before the transaction starts, because Hibernate reads it when the session opens.
			Integer removed = TenantContext.callAs(tenant, () -> this.authorization.runAsSystem(
					"retention purge of " + purgeable.name(), () -> this.transactions.execute((status) -> purgeable.purge())));
			int count = (removed != null) ? removed : 0;
			log.info("Retention purge {} removed {} rows", purgeable.name(), count);
			return count;
		}
		catch (RuntimeException ex) {
			log.error("Retention purge {} failed; the other purges continue", purgeable.name(), ex);
			return 0;
		}
	}

}
