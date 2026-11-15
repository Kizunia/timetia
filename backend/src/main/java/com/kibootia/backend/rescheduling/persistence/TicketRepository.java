package com.kibootia.backend.rescheduling.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Tickets in MySQL. Entity queries are filtered by tenant on their own (ADR 0028). */
interface TicketRepository extends JpaRepository<TicketEntity, UUID>, JpaSpecificationExecutor<TicketEntity> {

}
