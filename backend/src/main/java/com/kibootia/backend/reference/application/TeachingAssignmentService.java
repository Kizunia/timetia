package com.kibootia.backend.reference.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.academic.AcademicPermissions;
import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.audit.AuditEntry;
import com.kibootia.backend.audit.AuditLog;
import com.kibootia.backend.platform.authorization.RequiresPermission;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.errors.ApiFieldError;
import com.kibootia.backend.platform.errors.ProblemCodes;

/**
 * The use cases that manage teaching assignments. Super Admin configuration ({@link AcademicPermissions#CONFIG_MANAGE}).
 * Beyond checking the referenced division, subject, faculty and batch exist, it keeps the data model's rule: a theory
 * assignment names no batch; a lab or tutorial names exactly one batch of its division. An assignment has no editable
 * field, so there is create, read, list and delete, but no update.
 */
@Service
public class TeachingAssignmentService {

	public static final String NOT_FOUND = "reference.teaching_assignment_not_found";

	public static final String DUPLICATE = "reference.duplicate_teaching_assignment";

	private final TeachingAssignmentStore assignments;

	private final CampusStore campus;

	private final CatalogStore catalog;

	private final AuditLog auditLog;

	TeachingAssignmentService(TeachingAssignmentStore assignments, CampusStore campus, CatalogStore catalog,
			AuditLog auditLog) {
		this.assignments = assignments;
		this.campus = campus;
		this.catalog = catalog;
		this.auditLog = auditLog;
	}

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public TeachingAssignment create(UUID divisionId, UUID batchId, UUID subjectId, UUID facultyId,
			SessionType sessionType) {
		validate(divisionId, batchId, subjectId, facultyId, sessionType);
		if (this.assignments.duplicateExists(divisionId, subjectId, sessionType, batchId)) {
			throw ApiException.conflict(DUPLICATE,
					"A teaching assignment for this division, subject, session type and batch already exists.");
		}
		TeachingAssignment created = this.assignments.insert(divisionId, batchId, subjectId, facultyId, sessionType);
		this.auditLog.record(AuditEntry.of("reference.teaching-assignment.created", "teaching-assignment", created.id()));
		return created;
	}

	@Transactional(readOnly = true)
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public TeachingAssignment get(UUID id) {
		return this.assignments.find(id)
			.orElseThrow(() -> ApiException.notFound(NOT_FOUND, "There is no teaching assignment with that ID."));
	}

	@Transactional(readOnly = true)
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Page<TeachingAssignment> list(UUID division, UUID faculty, UUID subject, Pageable pageable) {
		return this.assignments.search(division, faculty, subject, pageable);
	}

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public void delete(UUID id) {
		if (!this.assignments.exists(id)) {
			throw ApiException.notFound(NOT_FOUND, "There is no teaching assignment with that ID.");
		}
		this.assignments.delete(id);
		this.auditLog.record(AuditEntry.of("reference.teaching-assignment.deleted", "teaching-assignment", id));
	}

	/** Checks the references exist and the batch rule holds, collecting every bad field so the caller sees them all. */
	private void validate(UUID divisionId, UUID batchId, UUID subjectId, UUID facultyId, SessionType sessionType) {
		List<ApiFieldError> errors = new ArrayList<>();
		if (divisionId == null || !this.campus.divisionExists(divisionId)) {
			errors.add(new ApiFieldError("divisionId", "unknown_division", "There is no division with that ID."));
		}
		if (subjectId == null || !this.catalog.subjectExists(subjectId)) {
			errors.add(new ApiFieldError("subjectId", "unknown_subject", "There is no subject with that ID."));
		}
		if (facultyId == null || !this.catalog.facultyExists(facultyId)) {
			errors.add(new ApiFieldError("facultyId", "unknown_faculty", "There is no faculty with that ID."));
		}
		if (sessionType == SessionType.THEORY) {
			if (batchId != null) {
				errors.add(new ApiFieldError("batchId", "batch_not_allowed", "A theory assignment names no batch."));
			}
		}
		else if (batchId == null) {
			errors.add(new ApiFieldError("batchId", "batch_required", "A lab or tutorial assignment needs a batch."));
		}
		else {
			// The batch must exist and belong to the division of the assignment.
			this.campus.findBatch(batchId).filter((batch) -> batch.divisionId().equals(divisionId))
				.ifPresentOrElse((batch) -> {
				}, () -> errors
					.add(new ApiFieldError("batchId", "unknown_batch", "There is no batch of that division with that ID.")));
		}
		if (!errors.isEmpty()) {
			throw ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.", errors);
		}
	}

}
