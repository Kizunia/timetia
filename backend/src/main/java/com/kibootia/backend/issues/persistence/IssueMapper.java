package com.kibootia.backend.issues.persistence;

import com.kibootia.backend.issues.application.Issue;

/** Converts between the issue persistence entity and the application model by hand (ADR 0005). */
final class IssueMapper {

	private IssueMapper() {
	}

	static Issue toModel(IssueEntity entity) {
		return new Issue(entity.getId(), entity.getTenantId(), entity.getSessionType(), entity.getStatus(),
				entity.getDepartmentId(), entity.getDivisionId(), entity.getBatchId(), entity.getTitle(),
				entity.getDescription(), entity.getOpenerUserId(), entity.getClosedAt(), entity.getClosedByUserId(),
				entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
	}

}
