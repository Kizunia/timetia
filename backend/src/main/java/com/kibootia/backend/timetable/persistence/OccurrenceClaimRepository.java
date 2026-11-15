package com.kibootia.backend.timetable.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.kibootia.backend.timetable.domain.ClaimKind;

/** Claims in MySQL. Entity queries and the bulk delete are filtered by tenant on their own (ADR 0028). */
interface OccurrenceClaimRepository extends JpaRepository<OccurrenceClaimEntity, UUID> {

	/**
	 * The claims on a kind of resource on some dates and hours. It can return a few more than the exact combinations asked
	 * for, because dates and hours are matched separately; the store narrows the answer.
	 */
	@Query("""
			select c from OccurrenceClaimEntity c
			where c.claimKind = :kind and c.resourceId = :resource and c.claimDate in :dates and c.cellHour in :hours
			""")
	List<OccurrenceClaimEntity> find(ClaimKind kind, UUID resource, Collection<LocalDate> dates, Collection<Integer> hours);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from OccurrenceClaimEntity c where c.occurrenceId in :occurrenceIds")
	int deleteOf(Collection<UUID> occurrenceIds);

}
