package com.kibootia.backend.audit.application;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.audit.AuditEntry;
import com.kibootia.backend.audit.AuditLog;
import com.kibootia.backend.audit.AuditPermissions;
import com.kibootia.backend.platform.authorization.RequiresPermission;
import com.kibootia.backend.platform.events.FailedEventAdministration;
import com.kibootia.backend.platform.events.FailedEventAdministration.FailedEvent;
import com.kibootia.backend.platform.errors.ApiException;

/**
 * The view of events that keep failing, and the retry action (ADR 0031). The platform reads the registry; this module
 * owns the administrative use case, so a retry is authorized and audited like every administrative change (ADR 0046).
 * The platform's {@link FailedEvent} is the application model: it carries metadata only, never the event's payload.
 */
@Service
public class FailedEventService {

	static final String NOT_FOUND = "audit.failed_event_not_found";

	private final FailedEventAdministration events;

	private final AuditLog auditLog;

	FailedEventService(FailedEventAdministration events, AuditLog auditLog) {
		this.events = events;
		this.auditLog = auditLog;
	}

	@RequiresPermission(AuditPermissions.VIEW_FAILED_EVENTS)
	public Page<FailedEvent> list(Pageable pageable) {
		return this.events.list(pageable);
	}

	/**
	 * Redelivers one failed event now. The audit entry is written first, in this transaction; the redelivery itself runs
	 * afterwards on another thread.
	 */
	@Transactional
	@RequiresPermission(AuditPermissions.RETRY_FAILED_EVENTS)
	public void retry(UUID id) {
		FailedEvent event = this.events.find(id)
			.orElseThrow(() -> ApiException.notFound(NOT_FOUND, "There is no failed event with that ID."));
		this.auditLog.record(AuditEntry.of("audit.failed-event.retried", "failed-event", id)
			.withChange("listener", event.listener())
			.withChange("attempts", String.valueOf(event.attempts())));
		this.events.retry(id);
	}

}
