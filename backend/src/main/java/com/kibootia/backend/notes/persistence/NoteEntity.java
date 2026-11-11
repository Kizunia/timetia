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

	/**
	 * The stored picture of the note, or null. Never written through the entity ({@code updatable = false}): an ordinary
	 * edit of the title or body must not put back an attachment that changed while it was being made. The attachment is
	 * changed by a statement of its own, in {@link JpaNoteStore}.
	 */
	@Column(name = "attachment_file_id", updatable = false, columnDefinition = "binary(16)")
	private UUID attachmentFileId;

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

	UUID getAttachmentFileId() {
		return this.attachmentFileId;
	}

	void change(String title, String body) {
		this.title = title;
		this.body = body;
	}

}
