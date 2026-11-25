package com.kibootia.backend.reference.application;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.academic.AcademicPermissions;
import com.kibootia.backend.audit.AuditEntry;
import com.kibootia.backend.audit.AuditLog;
import com.kibootia.backend.platform.authorization.RequiresPermission;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.errors.ApiFieldError;
import com.kibootia.backend.platform.errors.ProblemCodes;

/**
 * The use cases that manage the campus structure: departments, divisions and batches. These are the places a Timetia
 * role is assigned in, so creating and removing them is Super Admin configuration ({@link AcademicPermissions#CONFIG_MANAGE},
 * a flat permission the built-in Administrator holds). Every mutation is audited with ids only (ADR 0046). A code is set
 * once and never changes, so an assignment made by that code keeps resolving; only the name is editable.
 */
@Service
public class CampusService {

	public static final String DEPARTMENT_NOT_FOUND = "reference.department_not_found";

	public static final String DIVISION_NOT_FOUND = "reference.division_not_found";

	public static final String BATCH_NOT_FOUND = "reference.batch_not_found";

	public static final String DUPLICATE_CODE = "reference.duplicate_code";

	private final CampusStore campus;

	private final AuditLog auditLog;

	CampusService(CampusStore campus, AuditLog auditLog) {
		this.campus = campus;
		this.auditLog = auditLog;
	}

	// --- departments ------------------------------------------------------------------------------------------------

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Department createDepartment(String code, String name) {
		String cleanCode = code.strip();
		if (this.campus.departmentCodeInUse(cleanCode)) {
			throw duplicateCode("A department with this code already exists.");
		}
		Department created = this.campus.insertDepartment(cleanCode, name.strip());
		this.auditLog.record(AuditEntry.of("reference.department.created", "department", created.id())
			.withChange("code", created.code()));
		return created;
	}

	@Transactional(readOnly = true)
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Department getDepartment(UUID id) {
		return this.campus.findDepartment(id)
			.orElseThrow(() -> ApiException.notFound(DEPARTMENT_NOT_FOUND, "There is no department with that ID."));
	}

	@Transactional(readOnly = true)
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Page<Department> listDepartments(String text, Pageable pageable) {
		return this.campus.searchDepartments(text, pageable);
	}

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Department renameDepartment(UUID id, long expectedVersion, String name) {
		requireDepartment(id);
		return this.campus.renameDepartment(id, expectedVersion, name.strip());
	}

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public void deleteDepartment(UUID id) {
		requireDepartment(id);
		this.campus.deleteDepartment(id);
		this.auditLog.record(AuditEntry.of("reference.department.deleted", "department", id));
	}

	// --- divisions --------------------------------------------------------------------------------------------------

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Division createDivision(String code, String name, UUID departmentId) {
		requireDepartmentReference(departmentId);
		String cleanCode = code.strip();
		if (this.campus.divisionCodeInUse(cleanCode)) {
			throw duplicateCode("A division with this code already exists.");
		}
		Division created = this.campus.insertDivision(cleanCode, name.strip(), departmentId);
		this.auditLog.record(AuditEntry.of("reference.division.created", "division", created.id())
			.withChange("code", created.code()));
		return created;
	}

	@Transactional(readOnly = true)
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Division getDivision(UUID id) {
		return this.campus.findDivision(id)
			.orElseThrow(() -> ApiException.notFound(DIVISION_NOT_FOUND, "There is no division with that ID."));
	}

	@Transactional(readOnly = true)
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Page<Division> listDivisions(UUID department, String text, Pageable pageable) {
		return this.campus.searchDivisions(department, text, pageable);
	}

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Division renameDivision(UUID id, long expectedVersion, String name) {
		if (!this.campus.divisionExists(id)) {
			throw ApiException.notFound(DIVISION_NOT_FOUND, "There is no division with that ID.");
		}
		return this.campus.renameDivision(id, expectedVersion, name.strip());
	}

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public void deleteDivision(UUID id) {
		if (!this.campus.divisionExists(id)) {
			throw ApiException.notFound(DIVISION_NOT_FOUND, "There is no division with that ID.");
		}
		this.campus.deleteDivision(id);
		this.auditLog.record(AuditEntry.of("reference.division.deleted", "division", id));
	}

	// --- batches ----------------------------------------------------------------------------------------------------

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Batch createBatch(String code, String name, UUID divisionId) {
		requireDivisionReference(divisionId);
		String cleanCode = code.strip();
		if (this.campus.batchCodeInUse(cleanCode)) {
			throw duplicateCode("A batch with this code already exists.");
		}
		Batch created = this.campus.insertBatch(cleanCode, name.strip(), divisionId);
		this.auditLog
			.record(AuditEntry.of("reference.batch.created", "batch", created.id()).withChange("code", created.code()));
		return created;
	}

	@Transactional(readOnly = true)
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Batch getBatch(UUID id) {
		return this.campus.findBatch(id)
			.orElseThrow(() -> ApiException.notFound(BATCH_NOT_FOUND, "There is no batch with that ID."));
	}

	@Transactional(readOnly = true)
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Page<Batch> listBatches(UUID division, String text, Pageable pageable) {
		return this.campus.searchBatches(division, text, pageable);
	}

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Batch renameBatch(UUID id, long expectedVersion, String name) {
		if (this.campus.findBatch(id).isEmpty()) {
			throw ApiException.notFound(BATCH_NOT_FOUND, "There is no batch with that ID.");
		}
		return this.campus.renameBatch(id, expectedVersion, name.strip());
	}

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public void deleteBatch(UUID id) {
		if (this.campus.findBatch(id).isEmpty()) {
			throw ApiException.notFound(BATCH_NOT_FOUND, "There is no batch with that ID.");
		}
		this.campus.deleteBatch(id);
		this.auditLog.record(AuditEntry.of("reference.batch.deleted", "batch", id));
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	private void requireDepartment(UUID id) {
		if (!this.campus.departmentExists(id)) {
			throw ApiException.notFound(DEPARTMENT_NOT_FOUND, "There is no department with that ID.");
		}
	}

	/** A parent named in a request body: a missing one is a bad field (400), not a missing resource (404). */
	private void requireDepartmentReference(UUID departmentId) {
		if (departmentId == null || !this.campus.departmentExists(departmentId)) {
			throw ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.",
					List.of(new ApiFieldError("departmentId", "unknown_department", "There is no department with that ID.")));
		}
	}

	private void requireDivisionReference(UUID divisionId) {
		if (divisionId == null || !this.campus.divisionExists(divisionId)) {
			throw ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.",
					List.of(new ApiFieldError("divisionId", "unknown_division", "There is no division with that ID.")));
		}
	}

	private static ApiException duplicateCode(String message) {
		return ApiException.conflict(DUPLICATE_CODE, message);
	}

}
