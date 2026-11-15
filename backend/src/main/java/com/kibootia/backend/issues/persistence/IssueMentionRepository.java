package com.kibootia.backend.issues.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** The mentions of issues in MySQL. Entity queries are filtered by tenant on their own (ADR 0028). */
interface IssueMentionRepository extends JpaRepository<IssueMentionEntity, UUID> {

	List<IssueMentionEntity> findByIssueIdIn(Collection<UUID> issueIds);

	boolean existsByIssueIdAndMentionedFacultyId(UUID issueId, UUID mentionedFacultyId);

}
