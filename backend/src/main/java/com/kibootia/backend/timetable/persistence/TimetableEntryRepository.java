package com.kibootia.backend.timetable.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Timetable entries in MySQL; entity queries are filtered by tenant on their own (ADR 0028). */
interface TimetableEntryRepository extends JpaRepository<TimetableEntryEntity, UUID> {

	List<TimetableEntryEntity> findByTimetableVersionIdOrderByDayOfWeekAscStartTimeAsc(UUID timetableVersionId);

	void deleteByTimetableVersionId(UUID timetableVersionId);

}
