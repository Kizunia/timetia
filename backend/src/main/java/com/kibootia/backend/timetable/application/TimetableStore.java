package com.kibootia.backend.timetable.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Where timetable versions and their entries are kept. The use cases depend on this port, never on JPA (ADR 0003). Reads
 * and writes act on the current tenant's rows.
 */
public interface TimetableStore {

	TimetableVersion insertVersion(UUID divisionId, UUID termId, LocalDate effectiveFrom);

	Optional<TimetableVersion> findVersion(UUID id);

	/** @param division only versions of this division, or all when null; @param status only this status, or all when null */
	Page<TimetableVersion> searchVersions(UUID division, VersionStatus status, Pageable pageable);

	/** Removes a version and its entries for good. Only a draft is ever removed. */
	void deleteVersion(UUID id);

	/** Makes the version published. */
	TimetableVersion markPublished(UUID id);

	/** Whether a published version of the division already takes effect on that date. */
	boolean publishedStartingOn(UUID divisionId, LocalDate effectiveFrom);

	/** The published version of the division that is in force on that date, if there is one. */
	Optional<TimetableVersion> publishedInForce(UUID divisionId, LocalDate date);

	/** The first date after {@code date} on which a published version of the division takes effect, if any. */
	Optional<LocalDate> nextPublishedAfter(UUID divisionId, LocalDate date);

	TimetableEntry insertEntry(UUID versionId, NewEntry entry);

	/** The entries of a version in a stable order: day, then start time. */
	List<TimetableEntry> entriesOf(UUID versionId);

	Optional<TimetableEntry> findEntry(UUID id);

	void deleteEntry(UUID id);

}
