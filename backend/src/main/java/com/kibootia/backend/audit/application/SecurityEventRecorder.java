package com.kibootia.backend.audit.application;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.kibootia.backend.audit.AuditEntry;
import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.observability.CurrentTrace;
import com.kibootia.backend.platform.security.SecurityEvents;

/**
 * Records the refusals that the platform reports as audit entries (Timetia ADR 0005): a signed-in user refused a
 * permission, a refused account, a rate-limit refusal. Each is written in its own transaction, because the refused
 * request has none to join, and with the user ID, a permission name or a code only.
 *
 * <p>The number written for one user is limited per minute, so a flood of refused requests cannot fill the log. Reporting
 * never changes the answer to the caller: a failure to write is logged and swallowed.
 */
@Service
class SecurityEventRecorder implements SecurityEvents {

	private static final Logger log = LoggerFactory.getLogger(SecurityEventRecorder.class);

	private static final int MAX_TRACKED_USERS = 10_000;

	private final AuditEntryStore store;

	private final Clock clock;

	private final CurrentTrace trace;

	private final TransactionTemplate transactions;

	private final int maxPerMinute;

	private final Cache<UUID, AtomicInteger> recent = Caffeine.newBuilder()
		.maximumSize(MAX_TRACKED_USERS)
		.expireAfterWrite(Duration.ofMinutes(1))
		.build();

	SecurityEventRecorder(AuditEntryStore store, Clock clock, CurrentTrace trace,
			PlatformTransactionManager transactionManager, AuditProperties properties) {
		this.store = store;
		this.clock = clock;
		this.trace = trace;
		this.transactions = new TransactionTemplate(transactionManager);
		this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.maxPerMinute = properties.securityEvents().maxPerMinute();
	}

	@Override
	@NoAuthorization("reports a refusal that has already happened")
	public void accessDenied(UUID actor, String permission, String reason) {
		AuditEntry entry = AuditEntry.of("security.access.denied", "permission", null).failedWith(reason);
		write(actor, (permission == null) ? entry : entry.withChange("permission", permission));
	}

	@Override
	@NoAuthorization("reports a refusal that has already happened")
	public void accountRefused(UUID actor, String reason) {
		write(actor, AuditEntry.of("security.account.refused", "user", actor).failedWith(reason));
	}

	@Override
	@NoAuthorization("reports a refusal that has already happened")
	public void rateLimited(UUID actor, String category) {
		write(actor, AuditEntry.of("security.rate-limit.exceeded", "rate-limit", null)
			.failedWith("rate_limited")
			.withChange("category", category));
	}

	private void write(UUID actor, AuditEntry entry) {
		if (this.recent.get(actor, (key) -> new AtomicInteger()).incrementAndGet() > this.maxPerMinute) {
			log.debug("Dropped a security event for a user who already caused {} this minute", this.maxPerMinute);
			return;
		}
		try {
			this.transactions.executeWithoutResult((status) -> this.store.append(new AuditEntryStore.Entry(
					this.clock.instant(), actor, entry.action(), entry.targetType(), entry.targetId(), entry.changes(),
					entry.outcome(), this.trace.traceId().orElse(null))));
		}
		catch (RuntimeException ex) {
			log.warn("A security event could not be recorded: {}", ex.toString());
		}
	}

}
