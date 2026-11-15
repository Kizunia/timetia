package com.kibootia.backend.reference;

import java.util.UUID;

/**
 * A faculty member as the modules that build on the reference data see them.
 *
 * @param userId the account the faculty member signs in as, or null when they have none yet
 */
public record FacultyDetails(UUID id, String name, UUID userId, UUID departmentId) {

}
