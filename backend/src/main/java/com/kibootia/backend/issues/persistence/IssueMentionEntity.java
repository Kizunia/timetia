package com.kibootia.backend.issues.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.kibootia.backend.platform.persistence.BaseEntity;

/** A faculty member an issue mentions. Persistence only (ADR 0004). */
@Entity
@Table(name = "issue_mention")
class IssueMentionEntity extends BaseEntity {

	@Column(name = "issue_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID issueId;

	@Column(name = "mentioned_faculty_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID mentionedFacultyId;

	protected IssueMentionEntity() {
	}

	IssueMentionEntity(UUID issueId, UUID mentionedFacultyId) {
		this.issueId = issueId;
		this.mentionedFacultyId = mentionedFacultyId;
	}

	UUID getIssueId() {
		return this.issueId;
	}

	UUID getMentionedFacultyId() {
		return this.mentionedFacultyId;
	}

}
