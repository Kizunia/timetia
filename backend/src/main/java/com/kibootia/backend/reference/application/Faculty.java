package com.kibootia.backend.reference.application;

import java.time.Instant;
import java.util.UUID;

/**
 * A faculty member as the use cases see it (ADR 0004). {@code userId} is the Keycloak user they sign in as, which the
 * lecture access facts expose as a teaching-faculty user id; it is null when the person has no account yet.
 */
public record Faculty(UUID id, String code, String name, UUID departmentId, UUID userId, long version,
		Instant createdAt, Instant updatedAt) {

}
