package com.kibootia.backend.reference.application;

import java.time.Instant;
import java.util.UUID;

/** A batch (a lab group) as the use cases see it, belonging to a division (ADR 0004). */
public record Batch(UUID id, String code, String name, UUID divisionId, long version, Instant createdAt,
		Instant updatedAt) {

}
