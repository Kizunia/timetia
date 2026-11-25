package com.kibootia.backend.reference.application;

import java.time.Instant;
import java.util.UUID;

/** A subject (a course) as the use cases see it (ADR 0004). */
public record Subject(UUID id, String code, String name, long version, Instant createdAt, Instant updatedAt) {

}
