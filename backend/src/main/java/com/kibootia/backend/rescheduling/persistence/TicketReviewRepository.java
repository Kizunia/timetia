package com.kibootia.backend.rescheduling.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Reviews in MySQL. Entity queries are filtered by tenant on their own (ADR 0028). */
interface TicketReviewRepository extends JpaRepository<TicketReviewEntity, UUID> {

	List<TicketReviewEntity> findByTicketIdOrderByTicketRevisionNoAscIdAsc(UUID ticketId);

	List<TicketReviewEntity> findByTicketIdAndTicketRevisionNo(UUID ticketId, int ticketRevisionNo);

}
