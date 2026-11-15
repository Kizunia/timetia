package com.kibootia.backend.rescheduling.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Conflict check runs in MySQL. Entity queries are filtered by tenant on their own (ADR 0028). */
interface ConflictCheckRunRepository extends JpaRepository<ConflictCheckRunEntity, UUID> {

	/** The latest run of a ticket. IDs are time ordered, so they break a tie between runs made in the same instant. */
	Optional<ConflictCheckRunEntity> findFirstByTicketIdOrderByCreatedAtDescIdDesc(UUID ticketId);

}
