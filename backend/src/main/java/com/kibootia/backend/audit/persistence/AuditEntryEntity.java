package com.kibootia.backend.audit.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

import com.kibootia.backend.platform.tenancy.TenantContext;

/**
 * One audit entry as stored (ADR 0046). Immutable: Hibernate never updates it, and the repository offers no update. It
 * carries the tenant but, deliberately, none of the soft-delete, version or audit columns of business tables: the log is
 * append-only and is trimmed only by the retention purge (ADR 0034).
 */
@Entity
@Immutable
@Table(name = "audit_entry")
class AuditEntryEntity {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(name = "id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID id;

	@TenantId
	@Column(name = "tenant_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID tenantId;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	@Column(name = "actor_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID actorId;

	@Column(name = "action", nullable = false, updatable = false, length = 100)
	private String action;

	@Column(name = "target_type", nullable = false, updatable = false, length = 50)
	private String targetType;

	@Column(name = "target_id", updatable = false, columnDefinition = "binary(16)")
	private UUID targetId;

	@Column(name = "outcome", nullable = false, updatable = false, length = 100)
	private String outcome;

	@Column(name = "trace_id", updatable = false, length = 32)
	private String traceId;

	/** A JSON object of field name to non-personal value. */
	@Column(name = "changes", updatable = false, length = 4000)
	private String changes;

	protected AuditEntryEntity() {
	}

	AuditEntryEntity(Instant occurredAt, UUID actorId, String action, String targetType, UUID targetId,
			String outcome, String traceId, String changes) {
		this.occurredAt = occurredAt;
		this.actorId = actorId;
		this.action = action;
		this.targetType = targetType;
		this.targetId = targetId;
		this.outcome = outcome;
		this.traceId = traceId;
		this.changes = changes;
	}

	/** A write needs a tenant; reads without one find nothing. */
	@PrePersist
	void requireTenant() {
		if (TenantContext.current().isEmpty()) {
			throw new IllegalStateException("Refusing to write an audit entry: no tenant is bound to this thread");
		}
	}

}
