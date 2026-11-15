package com.kibootia.backend.rescheduling.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Conflict findings in MySQL. Entity queries are filtered by tenant on their own (ADR 0028). */
interface ConflictFindingRepository extends JpaRepository<ConflictFindingEntity, UUID> {

	List<ConflictFindingEntity> findByRunIdOrderByIdAsc(UUID runId);

}
