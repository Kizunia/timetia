package com.kibootia.backend.reference.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.kibootia.backend.academic.SessionType;

/** Teaching assignments in MySQL; entity queries are tenant-filtered and skip soft-deleted rows (ADR 0028, 0034). */
interface TeachingAssignmentRepository extends JpaRepository<TeachingAssignmentEntity, UUID> {

	/**
	 * Whether a live assignment already covers this division, subject, session type and batch (a null batch for theory).
	 * The {@code batch} parameter is compared with a null-safe operator so theory rows (no batch) match correctly.
	 */
	@Query("""
			select count(a) > 0 from TeachingAssignmentEntity a
			where a.divisionId = :division and a.subjectId = :subject and a.sessionType = :sessionType
			  and (a.batchId = :batch or (a.batchId is null and :batch is null))
			""")
	boolean duplicateExists(UUID division, UUID subject, SessionType sessionType, UUID batch);

	List<TeachingAssignmentEntity> findByFacultyIdIn(Collection<UUID> facultyIds);

	List<TeachingAssignmentEntity> findByDivisionIdIn(Collection<UUID> divisionIds);

	@Query("""
			select a from TeachingAssignmentEntity a
			where (:division is null or a.divisionId = :division)
			  and (:faculty is null or a.facultyId = :faculty)
			  and (:subject is null or a.subjectId = :subject)
			""")
	Page<TeachingAssignmentEntity> search(UUID division, UUID faculty, UUID subject, Pageable pageable);

}
