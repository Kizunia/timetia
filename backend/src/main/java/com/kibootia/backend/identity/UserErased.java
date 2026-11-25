package com.kibootia.backend.identity;

import java.util.UUID;

import com.kibootia.backend.platform.events.DomainEvent;
import com.kibootia.backend.platform.events.EventContext;

/**
 * Erasure of a user began: an administrator asked for it, or the retention period of a deleted user passed (ADR 0034).
 * Part of the module's public contract. Other modules remove what they hold about the user through a
 * {@code @DurableEventListener}, so the removal survives a restart. Identity itself deletes the Keycloak account and
 * then the profile.
 */
public record UserErased(UUID userId, EventContext context) implements DomainEvent {

}
