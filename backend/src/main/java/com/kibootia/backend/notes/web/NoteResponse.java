package com.kibootia.backend.notes.web;

import java.time.Instant;
import java.util.UUID;

/**
 * A note as the API returns it. The {@code version} is what a later update must send back; the tenant is not shown,
 * because the caller only ever sees their own.
 */
public record NoteResponse(UUID id, UUID ownerId, String title, String body, long version, Instant createdAt,
		Instant updatedAt) {

}
