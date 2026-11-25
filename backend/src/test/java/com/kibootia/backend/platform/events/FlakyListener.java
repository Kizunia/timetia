package com.kibootia.backend.platform.events;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.test.context.TestComponent;

import com.kibootia.backend.audit.AuditPermissions;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.observability.CurrentTrace;
import com.kibootia.backend.platform.tenancy.TenantContext;

/**
 * A test listener that fails a chosen number of times and records what the wrapper gave it. Import it into a test; it is
 * a {@code @TestComponent}, so no other test sees it.
 */
@TestComponent
public class FlakyListener {

	/** An event raised by a test. */
	public record TestEvent(String label, EventContext context) implements DomainEvent {

	}

	/** What the handler saw when it ran. */
	public record Seen(Optional<UUID> tenant, Optional<String> traceId, boolean actedAsSystem) {

	}

	/** Every call of the handler, including the ones that failed. */
	public static final AtomicInteger CALLS = new AtomicInteger();

	/** Calls that ran to the end. */
	public static final AtomicInteger SUCCESSES = new AtomicInteger();

	/** How many more calls fail before one succeeds. */
	public static final AtomicInteger FAILURES_LEFT = new AtomicInteger();

	public static final AtomicReference<Seen> LAST_SEEN = new AtomicReference<>();

	private final Authorization authorization;

	private final CurrentTrace trace;

	FlakyListener(Authorization authorization, CurrentTrace trace) {
		this.authorization = authorization;
		this.trace = trace;
	}

	public static void reset() {
		CALLS.set(0);
		SUCCESSES.set(0);
		FAILURES_LEFT.set(0);
		LAST_SEEN.set(null);
	}

	@DurableEventListener
	void on(TestEvent event) {
		CALLS.incrementAndGet();
		LAST_SEEN.set(new Seen(TenantContext.current(), this.trace.traceId(),
				this.authorization.can(AuditPermissions.PURGE_ENTRIES)));
		if (FAILURES_LEFT.getAndUpdate((left) -> Math.max(0, left - 1)) > 0) {
			throw new IllegalStateException("the handler fails on purpose");
		}
		SUCCESSES.incrementAndGet();
	}

}
