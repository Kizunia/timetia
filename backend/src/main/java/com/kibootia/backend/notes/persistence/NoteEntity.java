package com.kibootia.backend.notes.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.kibootia.backend.platform.persistence.SoftDeletableEntity;

/**
 * The table row of a note. Persistence only: it never leaves this package (ADR 0004). The key, tenant, version, audit
 * columns and soft delete come from the platform base types; the unique key on (tenant, owner, title, active marker) is
 * in the migration.
 */
@Entity
@Table(name = "note")
class NoteEntity extends SoftDeletableEntity {

	@Column(name = "owner_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID ownerId;

	@Column(name = "title", nullable = false, length = 120)
	private String title;

	@Column(name = "body", nullable = false, length = 4000)
	private String body;

	protected NoteEntity() {
	}

	NoteEntity(UUID ownerId, String title, String body) {
		this.ownerId = ownerId;
		this.title = title;
		this.body = body;
	}

	UUID getOwnerId() {
		return this.ownerId;
	}

	String getTitle() {
		return this.title;
	}

	String getBody() {
		return this.body;
	}

	void change(String title, String body) {
		this.title = title;
		this.body = body;
	}

}
