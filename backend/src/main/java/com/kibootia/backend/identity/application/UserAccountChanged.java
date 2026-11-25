package com.kibootia.backend.identity.application;

import java.util.UUID;

import com.kibootia.backend.platform.events.DomainEvent;
import com.kibootia.backend.platform.events.EventContext;

/** A user was disabled, enabled, deleted or restored. The handler brings the Keycloak account in line with the profile as it is when the handler runs, so a repeated or late delivery is harmless. */
public record UserAccountChanged(UUID userId, EventContext context) implements DomainEvent {

}
