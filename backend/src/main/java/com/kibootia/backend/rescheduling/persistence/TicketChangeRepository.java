package com.kibootia.backend.rescheduling.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** The changes tickets make, in MySQL. Entity queries are filtered by tenant on their own (ADR 0028). */
interface TicketChangeRepository extends JpaRepository<TicketChangeEntity, UUID> {

	Optional<TicketChangeEntity> findFirstByTicketIdOrderByIdAsc(UUID ticketId);

	List<TicketChangeEntity> findByTicketIdIn(Collection<UUID> ticketIds);

}
