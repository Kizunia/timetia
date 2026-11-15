package com.kibootia.backend.issues.web;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** The body of {@code POST /api/v1/issues/{id}/links}: the ticket that answers the issue. */
public record LinkRequest(@NotNull UUID ticketId) {

}
