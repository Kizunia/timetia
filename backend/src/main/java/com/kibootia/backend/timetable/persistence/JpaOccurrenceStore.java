package com.kibootia.backend.timetable.persistence;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.kibootia.backend.timetable.LectureSlot;
import com.kibootia.backend.timetable.application.NewOccurrence;
import com.kibootia.backend.timetable.application.Occurrence;
import com.kibootia.backend.timetable.application.OccurrenceQuery;
import com.kibootia.backend.timetable.application.OccurrenceStore;

/** Keeps the dated lectures in MySQL through JPA. */
@Component
class JpaOccurrenceStore implements OccurrenceStore {

	private final OccurrenceRepository occurrences;

	private final Clock clock;

	JpaOccurrenceStore(OccurrenceRepository occurrences, Clock clock) {
		this.occurrences = occurrences;
		this.clock = clock;
	}

	@Override
	public List<Occurrence> insertAll(List<NewOccurrence> newOccurrences) {
		List<OccurrenceEntity> saved = this.occurrences
			.saveAll(newOccurrences.stream().map(OccurrenceEntity::new).toList());
		this.occurrences.flush();
		return saved.stream().map(TimetableMapper::toModel).toList();
	}

	@Override
	public Occurrence insertExtra(NewOccurrence newOccurrence, UUID ticketId) {
		OccurrenceEntity entity = new OccurrenceEntity(newOccurrence);
		entity.createdBy(ticketId);
		return TimetableMapper.toModel(this.occurrences.saveAndFlush(entity));
	}

	@Override
	public List<UUID> supersede(UUID divisionId, LocalDate from, LocalDate toExclusive) {
		List<UUID> ids = this.occurrences.activeIdsBetween(divisionId, from, toExclusive, OccurrenceStatus.ACTIVE);
		if (!ids.isEmpty()) {
			this.occurrences.supersede(ids, OccurrenceStatus.SUPERSEDED, this.clock.instant());
		}
		return ids;
	}

	@Override
	public long countChanged(UUID divisionId, LocalDate from, LocalDate toExclusive) {
		return this.occurrences.countChangedBetween(divisionId, from, toExclusive, OccurrenceStatus.ACTIVE);
	}

	@Override
	public Optional<Occurrence> find(UUID id) {
		return this.occurrences.findById(id)
			.filter((entity) -> entity.getRecordStatus() == OccurrenceStatus.ACTIVE)
			.map(TimetableMapper::toModel);
	}

	@Override
	public List<Occurrence> findAll(Collection<UUID> ids) {
		return ids.isEmpty() ? List.of()
				: this.occurrences.findAllById(ids)
					.stream()
					.filter((entity) -> entity.getRecordStatus() == OccurrenceStatus.ACTIVE)
					.map(TimetableMapper::toModel)
					.toList();
	}

	@Override
	public List<Occurrence> findOfEntryFrom(UUID entryId, LocalDate baseDate) {
		return this.occurrences.ofEntryFrom(entryId, baseDate, OccurrenceStatus.ACTIVE)
			.stream()
			.map(TimetableMapper::toModel)
			.toList();
	}

	@Override
	public List<Occurrence> lockAll(Collection<UUID> ids) {
		return ids.isEmpty() ? List.of()
				: this.occurrences.lockActive(ids, OccurrenceStatus.ACTIVE).stream().map(TimetableMapper::toModel).toList();
	}

	@Override
	public Occurrence move(UUID id, LectureSlot slot, UUID ticketId) {
		OccurrenceEntity entity = this.occurrences.findById(id).orElseThrow();
		entity.moveTo(slot, ticketId);
		return TimetableMapper.toModel(this.occurrences.saveAndFlush(entity));
	}

	@Override
	public Occurrence cancel(UUID id, UUID ticketId) {
		OccurrenceEntity entity = this.occurrences.findById(id).orElseThrow();
		entity.cancel(ticketId);
		return TimetableMapper.toModel(this.occurrences.saveAndFlush(entity));
	}

	@Override
	public Occurrence restore(UUID id, LectureSlot slot, UUID facultyId, UUID ticketId) {
		OccurrenceEntity entity = this.occurrences.findById(id).orElseThrow();
		entity.restore(slot, facultyId, ticketId);
		return TimetableMapper.toModel(this.occurrences.saveAndFlush(entity));
	}

	@Override
	public Occurrence reassign(UUID id, UUID facultyId, UUID ticketId) {
		OccurrenceEntity entity = this.occurrences.findById(id).orElseThrow();
		entity.reassign(facultyId, ticketId);
		return TimetableMapper.toModel(this.occurrences.saveAndFlush(entity));
	}

	@Override
	public Page<Occurrence> search(OccurrenceQuery query, Pageable pageable) {
		return this.occurrences.findAll(OccurrenceSpecifications.matching(query), pageable).map(TimetableMapper::toModel);
	}

}
