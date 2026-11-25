package com.kibootia.backend.platform.persistence;

import jakarta.persistence.MappedSuperclass;

import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;

/**
 * A business table with soft delete (ADR 0034): {@code delete} sets {@code deleted_at}, and entity queries skip those
 * rows. The table also needs the generated active-marker column and a unique key that includes it, because MySQL
 * treats NULLs as distinct (see the reference migration in {@code notes}). Native queries are not filtered.
 */
@MappedSuperclass
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
public abstract class SoftDeletableEntity extends BaseEntity {

}
