package com.kibootia.backend.platform.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import org.hibernate.annotations.UuidGenerator;

/**
 * Columns every foundation table has: a UUIDv7 key stored as {@code BINARY(16)} (ADR 0033), plus everything in
 * {@link AuditedEntity}: the tenant (ADR 0028), audit columns and a version for optimistic locking. Persistence
 * entities stay inside their module and are never returned from an API (ADR 0004).
 */
@MappedSuperclass
public abstract class BaseEntity extends AuditedEntity {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(name = "id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID id;

	public UUID getId() {
		return this.id;
	}

}
