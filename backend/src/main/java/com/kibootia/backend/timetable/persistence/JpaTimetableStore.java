package com.kibootia.backend.timetable.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.kibootia.backend.timetable.application.NewEntry;
import com.kibootia.backend.timetable.application.TimetableEntry;
import com.kibootia.backend.timetable.application.TimetableStore;
import com.kibootia.backend.timetable.application.TimetableVersion;
import com.kibootia.backend.timetable.application.VersionStatus;

/** Keeps timetable versions and their entries in MySQL through JPA. */
@Component
class JpaTimetableStore implements TimetableStore {

	private final TimetableVersionRepository versions;

	private final TimetableEntryRepository entries;

	JpaTimetableStore(TimetableVersionRepository versions, TimetableEntryRepository entries) {
		this.versions = versions;
		this.entries = entries;
	}

	@Override
	public TimetableVersion insertVersion(UUID divisionId, UUID termId, LocalDate effectiveFrom) {
		return TimetableMapper.toModel(this.versions.saveAndFlush(new TimetableVersionEntity(divisionId, termId, effectiveFrom)));
	}

	@Override
	public Optional<TimetableVersion> findVersion(UUID id) {
		return this.versions.findById(id).map(TimetableMapper::toModel);
	}

	@Override
	public Page<TimetableVersion> searchVersions(UUID division, VersionStatus status, Pageable pageable) {
		return this.versions.search(division, status, pageable).map(TimetableMapper::toModel);
	}

	@Override
	public void deleteVersion(UUID id) {
		this.entries.deleteByTimetableVersionId(id);
		this.versions.deleteById(id);
		this.versions.flush();
	}

	@Override
	public TimetableVersion markPublished(UUID id) {
		TimetableVersionEntity entity = this.versions.findById(id).orElseThrow();
		entity.publish();
		return TimetableMapper.toModel(this.versions.saveAndFlush(entity));
	}

	@Override
	public boolean publishedStartingOn(UUID divisionId, LocalDate effectiveFrom) {
		return this.versions.existsByDivisionIdAndEffectiveFromAndStatus(divisionId, effectiveFrom, VersionStatus.PUBLISHED);
	}

	@Override
	public Optional<TimetableVersion> publishedInForce(UUID divisionId, LocalDate date) {
		return this.versions.inForce(divisionId, VersionStatus.PUBLISHED, date).map(TimetableMapper::toModel);
	}

	@Override
	public Optional<LocalDate> nextPublishedAfter(UUID divisionId, LocalDate date) {
		return this.versions.earliestEffectiveAfter(divisionId, VersionStatus.PUBLISHED, date);
	}

	@Override
	public TimetableEntry insertEntry(UUID versionId, NewEntry entry) {
		return TimetableMapper.toModel(this.entries.saveAndFlush(new TimetableEntryEntity(versionId, entry.assignmentId(),
				entry.dayOfWeek(), entry.startTime(), entry.endTime(), entry.roomId(), entry.mode(), entry.meetingLink())));
	}

	@Override
	public List<TimetableEntry> entriesOf(UUID versionId) {
		return this.entries.findByTimetableVersionIdOrderByDayOfWeekAscStartTimeAsc(versionId)
			.stream()
			.map(TimetableMapper::toModel)
			.toList();
	}

	@Override
	public Optional<TimetableEntry> findEntry(UUID id) {
		return this.entries.findById(id).map(TimetableMapper::toModel);
	}

	@Override
	public void deleteEntry(UUID id) {
		this.entries.deleteById(id);
		this.entries.flush();
	}

}
