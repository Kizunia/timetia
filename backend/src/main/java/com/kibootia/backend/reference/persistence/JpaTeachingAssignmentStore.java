package com.kibootia.backend.reference.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.reference.application.TeachingAssignment;
import com.kibootia.backend.reference.application.TeachingAssignmentStore;

/** Keeps teaching assignments in MySQL through JPA. A new row is flushed at once so a duplicate is reported now. */
@Component
class JpaTeachingAssignmentStore implements TeachingAssignmentStore {

	private final TeachingAssignmentRepository assignments;

	JpaTeachingAssignmentStore(TeachingAssignmentRepository assignments) {
		this.assignments = assignments;
	}

	@Override
	public TeachingAssignment insert(UUID divisionId, UUID batchId, UUID subjectId, UUID facultyId,
			SessionType sessionType) {
		return toModel(this.assignments
			.saveAndFlush(new TeachingAssignmentEntity(divisionId, batchId, subjectId, facultyId, sessionType)));
	}

	@Override
	public Optional<TeachingAssignment> find(UUID id) {
		return this.assignments.findById(id).map(JpaTeachingAssignmentStore::toModel);
	}

	@Override
	public List<TeachingAssignment> findByFaculty(Collection<UUID> facultyIds) {
		return facultyIds.isEmpty() ? List.of()
				: this.assignments.findByFacultyIdIn(facultyIds).stream().map(JpaTeachingAssignmentStore::toModel).toList();
	}

	@Override
	public List<TeachingAssignment> findByDivisions(Collection<UUID> divisionIds) {
		return divisionIds.isEmpty() ? List.of()
				: this.assignments.findByDivisionIdIn(divisionIds).stream().map(JpaTeachingAssignmentStore::toModel).toList();
	}

	@Override
	public List<TeachingAssignment> findAll(Collection<UUID> ids) {
		return ids.isEmpty() ? List.of()
				: this.assignments.findAllById(ids).stream().map(JpaTeachingAssignmentStore::toModel).toList();
	}

	@Override
	public Page<TeachingAssignment> search(UUID division, UUID faculty, UUID subject, Pageable pageable) {
		return this.assignments.search(division, faculty, subject, pageable).map(JpaTeachingAssignmentStore::toModel);
	}

	@Override
	public void delete(UUID id) {
		this.assignments.findById(id).ifPresent(this.assignments::delete);
		this.assignments.flush();
	}

	@Override
	public boolean exists(UUID id) {
		return this.assignments.findById(id).isPresent();
	}

	@Override
	public boolean duplicateExists(UUID divisionId, UUID subjectId, SessionType sessionType, UUID batchId) {
		return this.assignments.duplicateExists(divisionId, subjectId, sessionType, batchId);
	}

	private static TeachingAssignment toModel(TeachingAssignmentEntity entity) {
		return new TeachingAssignment(entity.getId(), entity.getDivisionId(), entity.getBatchId(), entity.getSubjectId(),
				entity.getFacultyId(), entity.getSessionType(), entity.getVersion(), entity.getCreatedAt(),
				entity.getUpdatedAt());
	}

}
