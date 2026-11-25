package com.kibootia.backend.reference.application;

import java.time.Instant;
import java.util.UUID;

/** A division (a class) as the use cases see it, belonging to a department (ADR 0004). */
public record Division(UUID id, String code, String name, UUID departmentId, long version, Instant createdAt,
		Instant updatedAt) {

}
