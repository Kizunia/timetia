package com.kibootia.backend.rescheduling.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.kibootia.backend.rescheduling.domain.TicketStatus;

/** Tickets in MySQL. Entity queries are filtered by tenant on their own (ADR 0028). */
interface TicketRepository extends JpaRepository<TicketEntity, UUID>, JpaSpecificationExecutor<TicketEntity> {

	/** The tickets in this state that are about this lecture, or that want a slot on one of these dates. */
	@Query("""
			select t from TicketEntity t
			where t.status = :status and t.id in (
			  select c.ticketId from TicketChangeEntity c where c.sourceOccurrenceId = :lecture or c.targetDate in :dates)
			""")
	List<TicketEntity> inStateAffectedBy(TicketStatus status, UUID lecture, Collection<LocalDate> dates);

	List<TicketEntity> findByStatusInAndExpiresAtLessThanEqualOrderByExpiresAtAsc(Collection<TicketStatus> statuses,
			Instant instant, Limit limit);

}
