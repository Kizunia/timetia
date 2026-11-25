package com.kibootia.backend.platform.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;

/**
 * A business table with soft delete whose key is given, not generated (ADR 0034): the user profile is keyed by the
 * Keycloak user ID, so the row can be found from a token without a lookup table. Everything else follows
 * {@link SoftDeletableEntity}: {@code delete} sets {@code deleted_at}, entity queries skip those rows, and the table
 * needs the active-marker column and a unique key that includes it.
 *
 * <p>Persist a new row with {@code EntityManager.persist}, not Spring Data's {@code save}: with a given key the
 * repository cannot tell a new row from an existing one and would load it first.
 */
@MappedSuperclass
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
public abstract class AssignedIdEntity extends AuditedEntity {

	@Id
	@Column(name = "id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID id;

	protected AssignedIdEntity() {
	}

	protected AssignedIdEntity(UUID id) {
		this.id = id;
	}

	public UUID getId() {
		return this.id;
	}

}
