package com.kibootia.backend.platform.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;

import org.hibernate.annotations.TenantId;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.kibootia.backend.platform.tenancy.TenantContext;

/**
 * The columns every foundation table has apart from its key: the tenant (ADR 0028), audit columns filled by Spring Data
 * auditing, and a version for optimistic locking, whose conflict surfaces as 409 (ADR 0043). Extend {@link BaseEntity}
 * for a generated UUIDv7 key, or {@link AssignedIdEntity} when the key comes from outside, as the user profile's does
 * from Keycloak.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditedEntity {

	@TenantId
	@Column(name = "tenant_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID tenantId;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@CreatedBy
	@Column(name = "created_by", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID createdBy;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@LastModifiedBy
	@Column(name = "updated_by", nullable = false, columnDefinition = "binary(16)")
	private UUID updatedBy;

	/**
	 * Writes need a tenant. Reads without one find nothing; refusing the write here means no row can be created that
	 * belongs to nobody.
	 */
	@PrePersist
	void requireTenant() {
		if (TenantContext.current().isEmpty()) {
			throw new IllegalStateException(
					"Refusing to persist " + getClass().getSimpleName() + ": no tenant is bound to this thread");
		}
	}

	public UUID getTenantId() {
		return this.tenantId;
	}

	public long getVersion() {
		return this.version;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public UUID getCreatedBy() {
		return this.createdBy;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

	public UUID getUpdatedBy() {
		return this.updatedBy;
	}

}
