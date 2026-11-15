package com.kibootia.backend.reference.application;

import java.time.Instant;
import java.util.UUID;

/**
 * A department as the use cases see it: plain data, with no persistence or HTTP concerns (ADR 0004). The {@code code} is
 * the stable short name people write in files and screens (for example {@code CSE}); it is set once and never changes, so
 * a scoped role assigned by that code keeps resolving to the same place.
 */
public record Department(UUID id, String code, String name, long version, Instant createdAt, Instant updatedAt) {

}
