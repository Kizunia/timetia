package com.kibootia.backend.reference.application;

import java.time.Instant;
import java.util.UUID;

/** A room as the use cases see it (ADR 0004). {@code capacity} is the number of seats, or null when unknown. */
public record Room(UUID id, String code, String name, RoomType roomType, Integer capacity, long version,
		Instant createdAt, Instant updatedAt) {

}
