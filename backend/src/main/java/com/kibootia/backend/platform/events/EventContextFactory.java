package com.kibootia.backend.platform.events;

import java.time.Clock;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.kibootia.backend.platform.persistence.UuidV7;
import com.kibootia.backend.platform.tenancy.TenantContext;

/** Captures the tenant and trace of the publisher for a new event. */
@Component
public class EventContextFactory {

	private final Clock clock;

	private final ObjectProvider<Tracer> tracer;

	EventContextFactory(Clock clock, ObjectProvider<Tracer> tracer) {
		this.clock = clock;
		this.tracer = tracer;
	}

	/** The context for an event raised now, by work that runs in a tenant. */
	public EventContext capture() {
		return new EventContext(UuidV7.next(this.clock), TenantContext.require(), traceparent(), this.clock.instant());
	}

	private String traceparent() {
		Tracer current = this.tracer.getIfAvailable();
		Span span = (current != null) ? current.currentSpan() : null;
		if (span == null) {
			return null;
		}
		return "00-" + span.context().traceId() + "-" + span.context().spanId() + "-"
				+ (Boolean.FALSE.equals(span.context().sampled()) ? "00" : "01");
	}

}
