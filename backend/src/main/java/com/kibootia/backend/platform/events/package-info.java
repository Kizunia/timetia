/**
 * Durable events (ADR 0031, ADR 0039): the context that travels inside an event, the listener annotation that restores
 * it, idempotent handling, retry with backoff and the administration of events that keep failing.
 */
@NamedInterface("events")
package com.kibootia.backend.platform.events;

import org.springframework.modulith.NamedInterface;
