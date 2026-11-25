package com.kibootia.backend.platform.observability;

import java.util.Optional;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** The trace of the work running on this thread, so audit entries and events can point back to logs and traces. */
@Component
public class CurrentTrace {

	private final ObjectProvider<Tracer> tracer;

	CurrentTrace(ObjectProvider<Tracer> tracer) {
		this.tracer = tracer;
	}

	/** The W3C trace ID (32 hex characters), or nothing outside a traced operation. */
	public Optional<String> traceId() {
		Tracer current = this.tracer.getIfAvailable();
		Span span = (current != null) ? current.currentSpan() : null;
		return (span != null) ? Optional.of(span.context().traceId()) : Optional.empty();
	}

}
