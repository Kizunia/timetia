package com.kibootia.backend.audit.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Deliberately not a {@code JpaRepository}: the log accepts new entries and the retention purge, nothing else (ADR 0046).
 * The tenant filter applies to the delete, so a purge only touches the caller's tenant.
 */
interface AuditEntryRepository extends Repository<AuditEntryEntity, UUID> {

	AuditEntryEntity save(AuditEntryEntity entry);

	/** The tenant filter applies, so only the caller's tenant's entries are found. */
	@Query("""
			select e from AuditEntryEntity e
			where (:actor is null or e.actorId = :actor)
			  and (:action is null or e.action like :action escape '!')
			  and (:targetType is null or e.targetType = :targetType)
			  and (:targetId is null or e.targetId = :targetId)
			  and (:from is null or e.occurredAt >= :from)
			  and (:to is null or e.occurredAt < :to)
			""")
	Page<AuditEntryEntity> search(UUID actor, String action, String targetType, UUID targetId, Instant from,
			Instant to, Pageable pageable);

	@Modifying
	@Query("delete from AuditEntryEntity e where e.occurredAt < :cutoff")
	int deleteOccurredBefore(Instant cutoff);

}
