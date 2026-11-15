package com.kibootia.backend.timetable.persistence;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.kibootia.backend.timetable.application.VersionStatus;

/** Timetable versions in MySQL; entity queries are filtered by tenant on their own (ADR 0028). */
interface TimetableVersionRepository extends JpaRepository<TimetableVersionEntity, UUID> {

	@Query("""
			select v from TimetableVersionEntity v
			where (:division is null or v.divisionId = :division) and (:status is null or v.status = :status)
			""")
	Page<TimetableVersionEntity> search(UUID division, VersionStatus status, Pageable pageable);

	boolean existsByDivisionIdAndEffectiveFromAndStatus(UUID divisionId, LocalDate effectiveFrom, VersionStatus status);

	/** The earliest date after {@code date} on which a version with this status takes effect, or none. */
	@Query("""
			select min(v.effectiveFrom) from TimetableVersionEntity v
			where v.divisionId = :division and v.status = :status and v.effectiveFrom > :date
			""")
	Optional<LocalDate> earliestEffectiveAfter(UUID division, VersionStatus status, LocalDate date);

}
