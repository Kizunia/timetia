package com.kibootia.backend.identity.application;

import java.util.UUID;

import com.kibootia.backend.platform.events.DomainEvent;
import com.kibootia.backend.platform.events.EventContext;

/** An invited user's account exists in Keycloak; the handler asks Keycloak to email the set-password link. A durable event, so a Keycloak outage delays the mail instead of losing it. */
public record UserInvited(UUID userId, EventContext context) implements DomainEvent {

}
