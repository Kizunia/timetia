package com.kibootia.backend.rescheduling.web;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** The body of {@code POST /api/v1/tickets/{id}/escalate}: the HOD to take a rejection to. */
public record EscalateRequest(@NotNull UUID hodUserId) {

}
