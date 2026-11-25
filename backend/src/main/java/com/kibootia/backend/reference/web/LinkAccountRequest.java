package com.kibootia.backend.reference.web;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The body of {@code PUT /faculties/{id}/account}: the account the faculty member signs in as, or null to remove the link.
 * {@code version} is the version the client last read: if the row changed since, the update is refused with 409.
 */
public record LinkAccountRequest(@Schema(nullable = true) UUID userId, @NotNull @PositiveOrZero Long version) {

}
