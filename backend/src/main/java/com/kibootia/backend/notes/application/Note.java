package com.kibootia.backend.notes.application;

import java.time.Instant;
import java.util.UUID;

import com.kibootia.backend.platform.authorization.Owned;
import com.kibootia.backend.platform.authorization.TenantBound;

/**
 * A note as the use cases see it: plain data, with no persistence or HTTP concerns (ADR 0004). It tells the decision
 * point who owns it and which tenant it belongs to.
 */
public record Note(UUID id, UUID tenantId, UUID ownerId, String title, String body, long version, Instant createdAt,
		Instant updatedAt) implements Owned, TenantBound {

}
