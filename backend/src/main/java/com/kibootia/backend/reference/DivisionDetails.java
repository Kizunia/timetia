package com.kibootia.backend.reference;

import java.util.UUID;

/** A division and the department it belongs to. */
public record DivisionDetails(UUID id, String code, UUID departmentId) {

}
