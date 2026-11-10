package com.kibootia.backend.identity.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.kibootia.backend.audit.AuditEntry;
import com.kibootia.backend.audit.AuditLog;
import com.kibootia.backend.identity.UserErased;
import com.kibootia.backend.platform.events.EventContextFactory;

/**
 * The first step of erasing a user, shared by an administrator's request and the retention job: record that it began,
 * audit it and publish the event the durable handlers react to. It runs in the caller's transaction. It carries no
 * transaction or security annotation of its own, so it is a plain object, not a proxy: the caller has already
 * authorized the erasure.
 */
@Component
class ErasureStarter {

	private final UserProfileStore profiles;

	private final AuditLog auditLog;

	private final ApplicationEventPublisher events;

	private final EventContextFactory eventContexts;

	private final Clock clock;

	ErasureStarter(UserProfileStore profiles, AuditLog auditLog, ApplicationEventPublisher events,
			EventContextFactory eventContexts, Clock clock) {
		this.profiles = profiles;
		this.auditLog = auditLog;
		this.events = events;
		this.eventContexts = eventContexts;
		this.clock = clock;
	}

	void begin(UUID id) {
		this.profiles.markErasureRequested(id, this.clock.instant());
		this.auditLog.record(AuditEntry.of("identity.user.erasure-requested", "user", id));
		this.events.publishEvent(new UserErased(id, this.eventContexts.capture()));
	}

}
