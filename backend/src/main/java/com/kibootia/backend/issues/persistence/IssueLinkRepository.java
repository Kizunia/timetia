package com.kibootia.backend.issues.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** The ticket links of issues in MySQL. Entity queries are filtered by tenant on their own (ADR 0028). */
interface IssueLinkRepository extends JpaRepository<IssueLinkEntity, UUID> {

	List<IssueLinkEntity> findByIssueIdIn(Collection<UUID> issueIds);

	boolean existsByIssueIdAndTicketId(UUID issueId, UUID ticketId);

}
