package com.kibootia.backend.audit.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.audit.AuditEntry;
import com.kibootia.backend.audit.AuditLog;
import com.kibootia.backend.audit.AuditPermissions;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.authorization.RequiresPermission;
import com.kibootia.backend.platform.observability.CurrentTrace;

/** Writes audit entries and removes expired ones (ADR 0046). */
@Service
class AuditService implements AuditLog {

	private static final Logger log = LoggerFactory.getLogger(AuditService.class);

	private final AuditEntryStore store;

	private final Authorization authorization;

	private final CurrentTrace trace;

	private final Clock clock;

	private final AuditProperties properties;

	AuditService(AuditEntryStore store, Authorization authorization, CurrentTrace trace, Clock clock,
			AuditProperties properties) {
		this.store = store;
		this.authorization = authorization;
		this.trace = trace;
		this.clock = clock;
		this.properties = properties;
	}

	/**
	 * MANDATORY: the entry joins the transaction of the change it describes and fails loudly if there is none.
	 */
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	@NoAuthorization("every module records its own auditable changes; the calling use case has already authorized them")
	public void record(AuditEntry entry) {
		this.store.append(new AuditEntryStore.Entry(this.clock.instant(), this.authorization.currentUserId(),
				entry.action(), entry.targetType(), entry.targetId(), entry.changes(), entry.outcome(),
				this.trace.traceId().orElse(null)));
	}

	/** Removes the tenant's entries older than the retention period and returns how many. */
	@Transactional
	@RequiresPermission(AuditPermissions.PURGE_ENTRIES)
	public int purgeExpired() {
		Instant cutoff = this.clock.instant().minus(Duration.ofDays(this.properties.retentionDays()));
		int removed = this.store.deleteOccurredBefore(cutoff);
		log.info("Removed {} audit entries older than {} days", removed, this.properties.retentionDays());
		return removed;
	}

}
