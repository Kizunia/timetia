package com.kibootia.backend.audit.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Deliberately not a {@code JpaRepository}: the log accepts new entries and the retention purge, nothing else (ADR 0046).
 * The tenant filter applies to the delete, so a purge only touches the caller's tenant.
 */
interface AuditEntryRepository extends Repository<AuditEntryEntity, UUID> {

	AuditEntryEntity save(AuditEntryEntity entry);

	@Modifying
	@Query("delete from AuditEntryEntity e where e.occurredAt < :cutoff")
	int deleteOccurredBefore(Instant cutoff);

}
