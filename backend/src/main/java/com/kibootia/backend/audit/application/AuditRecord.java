package com.kibootia.backend.audit.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * An audit entry as the viewer shows it: the stored entry plus the names of the people in it, looked up when it is shown
 * and never stored (ADR 0046).
 *
 * @param actor who did it
 * @param target the user acted on, when the entry is about a user; null for other kinds of record
 */
public record AuditRecord(UUID id, Instant occurredAt, Person actor, String action, String targetType, UUID targetId,
		Person target, Map<String, String> changes, String outcome, String traceId) {

	/**
	 * A person in an entry. Only the ID is stored, so a user who was erased can only be shown as erased.
	 *
	 * @param name the current name, or null when the user was erased or the actor is the system
	 */
	public record Person(UUID id, String name, Kind kind) {

		public enum Kind {

			/** Work no user started: a job, an event handler, startup. */
			SYSTEM,

			/** Kibootia still holds this user. */
			USER,

			/** Kibootia no longer holds this user: they were erased, or never existed here. */
			ERASED

		}

	}

}
