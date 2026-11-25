package com.kibootia.backend.files.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.kibootia.backend.platform.persistence.AssignedIdEntity;

/**
 * The table row of a stored file. Persistence only: it never leaves this package (ADR 0004). The key is given, because
 * the object keys contain it and the objects are written before the row; the tenant, version, audit columns and soft
 * delete come from the platform base types.
 */
@Entity
@Table(name = "stored_file")
class StoredFileEntity extends AssignedIdEntity {

	@Column(name = "owner_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID ownerId;

	@Column(name = "category", nullable = false, updatable = false, length = 40)
	private String category;

	@Column(name = "content_type", nullable = false, updatable = false, length = 100)
	private String contentType;

	@Column(name = "renditions", nullable = false, updatable = false, length = 255)
	private String renditions;

	protected StoredFileEntity() {
	}

	StoredFileEntity(UUID id, UUID ownerId, String category, String contentType, String renditions) {
		super(id);
		this.ownerId = ownerId;
		this.category = category;
		this.contentType = contentType;
		this.renditions = renditions;
	}

	UUID getOwnerId() {
		return this.ownerId;
	}

	String getCategory() {
		return this.category;
	}

	String getContentType() {
		return this.contentType;
	}

	String getRenditions() {
		return this.renditions;
	}

}
