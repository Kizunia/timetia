package com.kibootia.backend.issues.application;

import java.time.Instant;
import java.util.UUID;

import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.issues.domain.IssueStatus;

/**
 * An issue as the use cases see it: plain data (ADR 0004).
 *
 * @param batchId the batch of a lab or tutorial issue; null for a theory issue, which is for the whole division
 * @param closedAt when it was closed, with {@code closedByUserId}; null while open
 */
public record Issue(UUID id, UUID tenantId, SessionType sessionType, IssueStatus status, UUID departmentId, UUID divisionId,
		UUID batchId, String title, String description, UUID openerUserId, Instant closedAt, UUID closedByUserId, long version,
		Instant createdAt, Instant updatedAt) {

	public Issue closed(UUID by, Instant at) {
		return new Issue(this.id, this.tenantId, this.sessionType, IssueStatus.CLOSED, this.departmentId, this.divisionId,
				this.batchId, this.title, this.description, this.openerUserId, at, by, this.version, this.createdAt,
				this.updatedAt);
	}

}
