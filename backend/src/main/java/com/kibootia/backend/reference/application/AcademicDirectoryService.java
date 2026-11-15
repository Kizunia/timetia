package com.kibootia.backend.reference.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.reference.AcademicDirectory;
import com.kibootia.backend.reference.AssignmentDetails;
import com.kibootia.backend.reference.DivisionDetails;
import com.kibootia.backend.reference.RoomDetails;
import com.kibootia.backend.reference.TermDetails;

/**
 * Answers the questions other modules ask of the reference data (see {@link AcademicDirectory}). It gathers each answer
 * with one lookup per kind of record, never one per row, so a page of lectures costs a handful of queries however many
 * lectures it holds. It reads through the store ports like every use case, and returns plain published records.
 */
@Service
class AcademicDirectoryService implements AcademicDirectory {

	private final TeachingAssignmentStore assignments;

	private final CampusStore campus;

	private final CatalogStore catalog;

	AcademicDirectoryService(TeachingAssignmentStore assignments, CampusStore campus, CatalogStore catalog) {
		this.assignments = assignments;
		this.campus = campus;
		this.catalog = catalog;
	}

	@Override
	@Transactional(readOnly = true)
	@NoAuthorization("a read-only lookup for modules that have already authorized the request")
	public Map<UUID, AssignmentDetails> assignments(Collection<UUID> assignmentIds) {
		List<TeachingAssignment> found = this.assignments.findAll(assignmentIds);
		if (found.isEmpty()) {
			return Map.of();
		}
		Set<UUID> divisionIds = ids(found, TeachingAssignment::divisionId);
		Map<UUID, Division> divisions = byId(this.campus.findDivisions(divisionIds), Division::id);
		Map<UUID, Subject> subjects = byId(this.catalog.findSubjects(ids(found, TeachingAssignment::subjectId)),
				Subject::id);
		Map<UUID, Faculty> faculty = byId(this.catalog.findFacultyByIds(ids(found, TeachingAssignment::facultyId)),
				Faculty::id);
		List<Batch> batchesOfDivisions = this.campus.findBatchesOfDivisions(divisionIds);
		Map<UUID, Batch> batchById = byId(batchesOfDivisions, Batch::id);
		Map<UUID, Set<UUID>> batchIdsByDivision = batchesOfDivisions.stream()
			.collect(Collectors.groupingBy(Batch::divisionId, Collectors.mapping(Batch::id, Collectors.toSet())));

		Map<UUID, AssignmentDetails> details = new HashMap<>();
		for (TeachingAssignment assignment : found) {
			Division division = divisions.get(assignment.divisionId());
			Subject subject = subjects.get(assignment.subjectId());
			Faculty teacher = faculty.get(assignment.facultyId());
			if (division == null || subject == null || teacher == null) {
				continue;
			}
			Batch batch = (assignment.batchId() == null) ? null : batchById.get(assignment.batchId());
			// A theory lecture is for the whole division; a lab or tutorial is for its one batch.
			Set<UUID> lectureBatches = (assignment.sessionType() == SessionType.THEORY)
					? batchIdsByDivision.getOrDefault(division.id(), Set.of())
					: (batch == null) ? Set.of() : Set.of(batch.id());
			details.put(assignment.id(),
					new AssignmentDetails(assignment.id(), division.id(), division.code(), division.departmentId(),
							assignment.batchId(), (batch == null) ? null : batch.code(), Set.copyOf(lectureBatches),
							subject.id(), subject.code(), subject.name(), teacher.id(), teacher.name(), teacher.userId(),
							assignment.sessionType()));
		}
		return Map.copyOf(details);
	}

	@Override
	@Transactional(readOnly = true)
	@NoAuthorization("a read-only lookup for modules that have already authorized the request")
	public Map<UUID, RoomDetails> rooms(Collection<UUID> roomIds) {
		return this.catalog.findRooms(roomIds)
			.stream()
			.collect(Collectors.toUnmodifiableMap(Room::id, (room) -> new RoomDetails(room.id(), room.code(), room.name())));
	}

	@Override
	@Transactional(readOnly = true)
	@NoAuthorization("a read-only lookup for modules that have already authorized the request")
	public Optional<TermDetails> term(UUID termId) {
		return this.catalog.findTerm(termId)
			.map((term) -> new TermDetails(term.id(), term.code(), term.startDate(), term.endDate()));
	}

	@Override
	@Transactional(readOnly = true)
	@NoAuthorization("a read-only lookup for modules that have already authorized the request")
	public Optional<DivisionDetails> division(UUID divisionId) {
		return this.campus.findDivision(divisionId)
			.map((division) -> new DivisionDetails(division.id(), division.code(), division.departmentId()));
	}

	@Override
	@Transactional(readOnly = true)
	@NoAuthorization("a read-only lookup for modules that have already authorized the request")
	public Map<UUID, UUID> divisionsOfBatches(Collection<UUID> batchIds) {
		return this.campus.findBatches(batchIds)
			.stream()
			.collect(Collectors.toUnmodifiableMap(Batch::id, Batch::divisionId));
	}

	@Override
	@Transactional(readOnly = true)
	@NoAuthorization("a read-only lookup for modules that have already authorized the request")
	public Set<UUID> facultyIdsOf(UUID userId) {
		return (userId == null) ? Set.of() : Set.copyOf(new HashSet<>(this.catalog.findFacultyIdsOfUser(userId)));
	}

	private static <T> Set<UUID> ids(Collection<T> rows, Function<T, UUID> id) {
		return rows.stream().map(id).collect(Collectors.toSet());
	}

	private static <T> Map<UUID, T> byId(Collection<T> rows, Function<T, UUID> id) {
		return rows.stream().collect(Collectors.toMap(id, Function.identity(), (first, second) -> first));
	}

}
