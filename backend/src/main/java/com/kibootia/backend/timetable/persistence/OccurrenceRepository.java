package com.kibootia.backend.timetable.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Lectures in MySQL. Entity queries and the bulk update below are filtered by tenant on their own (ADR 0028), so none of
 * them can reach another tenant's lectures.
 */
interface OccurrenceRepository extends JpaRepository<OccurrenceEntity, UUID>, JpaSpecificationExecutor<OccurrenceEntity> {

	/** The active lectures a timetable entry produced, from a base date on, in date order. */
	@Query("""
			select o from OccurrenceEntity o
			where o.timetableEntryId = :entry and o.recordStatus = :active and o.occurrenceDate >= :from
			order by o.occurrenceDate
			""")
	List<OccurrenceEntity> ofEntryFrom(UUID entry, LocalDate from, OccurrenceStatus active);

	/** The active lectures of a division whose base date is in the range: what a new version that starts there replaces. */
	@Query("""
			select o.id from OccurrenceEntity o
			where o.divisionId = :division and o.recordStatus = :active
			  and o.occurrenceDate >= :from and o.occurrenceDate < :toExclusive
			""")
	List<UUID> activeIdsBetween(UUID division, LocalDate from, LocalDate toExclusive, OccurrenceStatus active);

	@Query("""
			select count(o) from OccurrenceEntity o
			where o.divisionId = :division and o.recordStatus = :active
			  and o.occurrenceDate >= :from and o.occurrenceDate < :toExclusive and o.changedByTicketId is not null
			""")
	long countChangedBetween(UUID division, LocalDate from, LocalDate toExclusive, OccurrenceStatus active);

	/**
	 * Marks lectures superseded. A bulk statement, so the update time is set here and the optimistic-lock version is left
	 * alone: nothing edits a lecture through the entity at the same time.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update OccurrenceEntity o set o.recordStatus = :superseded, o.updatedAt = :now where o.id in :ids")
	int supersede(Collection<UUID> ids, OccurrenceStatus superseded, Instant now);

	/** The active lectures among these IDs, locked until the end of the transaction, in ID order so that changes queue. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from OccurrenceEntity o where o.id in :ids and o.recordStatus = :active order by o.id")
	List<OccurrenceEntity> lockActive(Collection<UUID> ids, OccurrenceStatus active);

}
