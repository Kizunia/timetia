package com.kibootia.backend.identity.application;

import java.util.UUID;

import com.kibootia.backend.platform.events.DomainEvent;
import com.kibootia.backend.platform.events.EventContext;

/** A user signed up and their email address is not verified; the handler asks Keycloak to send the verification mail. Keycloak's own setting that does this on registration is off, because Kibootia enforces verification itself (ADR 0041). */
public record VerificationEmailRequested(UUID userId, EventContext context) implements DomainEvent {

}
