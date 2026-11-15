package com.kibootia.backend.reference.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.kibootia.backend.academic.SessionType;

/** Where teaching assignments are kept. The use cases depend on this port, never on JPA (ADR 0003). */
public interface TeachingAssignmentStore {

	TeachingAssignment insert(UUID divisionId, UUID batchId, UUID subjectId, UUID facultyId, SessionType sessionType);

	Optional<TeachingAssignment> find(UUID id);

	/** The live assignments among these ids; an id with no live assignment is absent. */
	List<TeachingAssignment> findAll(Collection<UUID> ids);

	/** The live assignments of these faculty members. */
	List<TeachingAssignment> findByFaculty(Collection<UUID> facultyIds);

	/** The live assignments in these divisions. */
	List<TeachingAssignment> findByDivisions(Collection<UUID> divisionIds);

	Page<TeachingAssignment> search(UUID division, UUID faculty, UUID subject, Pageable pageable);

	void delete(UUID id);

	boolean exists(UUID id);

	/** Whether a live assignment already covers this division, subject, session type and batch (null batch for theory). */
	boolean duplicateExists(UUID divisionId, UUID subjectId, SessionType sessionType, UUID batchId);

}
