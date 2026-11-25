package com.kibootia.backend.audit.web;

import com.kibootia.backend.platform.events.FailedEventAdministration.FailedEvent;

/** Converts the application model to the API type by hand (ADR 0005). */
final class FailedEventMapper {

	private FailedEventMapper() {
	}

	static FailedEventResponse toResponse(FailedEvent event) {
		return new FailedEventResponse(event.id(), event.eventType(), event.listener(), event.publishedAt(),
				event.attempts(), event.lastAttemptAt(), event.retriesExhausted());
	}

}
