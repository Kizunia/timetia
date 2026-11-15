package com.kibootia.backend.reference.application;

import java.time.LocalDate;
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
 * The use cases that manage the timetable's reference catalog: subjects, rooms, faculty and academic terms. Like the
 * campus structure these are Super Admin configuration ({@link AcademicPermissions#CONFIG_MANAGE}); every mutation is
 * audited with ids only, a code is unique per kind and set once, and only the name is editable here.
 */
@Service
public class CatalogService {

	public static final String SUBJECT_NOT_FOUND = "reference.subject_not_found";

	public static final String ROOM_NOT_FOUND = "reference.room_not_found";

	public static final String FACULTY_NOT_FOUND = "reference.faculty_not_found";

	public static final String TERM_NOT_FOUND = "reference.term_not_found";

	public static final String DUPLICATE_CODE = "reference.duplicate_code";

	private final CatalogStore catalog;

	private final CampusStore campus;

	private final AuditLog auditLog;

	CatalogService(CatalogStore catalog, CampusStore campus, AuditLog auditLog) {
		this.catalog = catalog;
		this.campus = campus;
		this.auditLog = auditLog;
	}

	// --- subjects ---------------------------------------------------------------------------------------------------

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Subject createSubject(String code, String name) {
		String cleanCode = requireFreeCode(code, this.catalog.subjectCodeInUse(code.strip()));
		Subject created = this.catalog.insertSubject(cleanCode, name.strip());
		audit("reference.subject.created", "subject", created.id(), created.code());
		return created;
	}

	@Transactional(readOnly = true)
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Subject getSubject(UUID id) {
		return this.catalog.findSubject(id).orElseThrow(() -> notFound(SUBJECT_NOT_FOUND, "subject"));
	}

	@Transactional(readOnly = true)
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Page<Subject> listSubjects(String text, Pageable pageable) {
		return this.catalog.searchSubjects(text, pageable);
	}

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Subject renameSubject(UUID id, long expectedVersion, String name) {
		if (!this.catalog.subjectExists(id)) {
			throw notFound(SUBJECT_NOT_FOUND, "subject");
		}
		return this.catalog.renameSubject(id, expectedVersion, name.strip());
	}

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public void deleteSubject(UUID id) {
		if (!this.catalog.subjectExists(id)) {
			throw notFound(SUBJECT_NOT_FOUND, "subject");
		}
		this.catalog.deleteSubject(id);
		this.auditLog.record(AuditEntry.of("reference.subject.deleted", "subject", id));
	}

	// --- rooms ------------------------------------------------------------------------------------------------------

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Room createRoom(String code, String name, RoomType roomType, Integer capacity) {
		String cleanCode = requireFreeCode(code, this.catalog.roomCodeInUse(code.strip()));
		Room created = this.catalog.insertRoom(cleanCode, name.strip(), roomType, capacity);
		audit("reference.room.created", "room", created.id(), created.code());
		return created;
	}

	@Transactional(readOnly = true)
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Room getRoom(UUID id) {
		return this.catalog.findRoom(id).orElseThrow(() -> notFound(ROOM_NOT_FOUND, "room"));
	}

	@Transactional(readOnly = true)
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Page<Room> listRooms(String text, Pageable pageable) {
		return this.catalog.searchRooms(text, pageable);
	}

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Room renameRoom(UUID id, long expectedVersion, String name) {
		if (!this.catalog.roomExists(id)) {
			throw notFound(ROOM_NOT_FOUND, "room");
		}
		return this.catalog.renameRoom(id, expectedVersion, name.strip());
	}

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public void deleteRoom(UUID id) {
		if (!this.catalog.roomExists(id)) {
			throw notFound(ROOM_NOT_FOUND, "room");
		}
		this.catalog.deleteRoom(id);
		this.auditLog.record(AuditEntry.of("reference.room.deleted", "room", id));
	}

	// --- faculty ----------------------------------------------------------------------------------------------------

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Faculty createFaculty(String code, String name, UUID departmentId, UUID userId) {
		requireDepartment(departmentId);
		String cleanCode = requireFreeCode(code, this.catalog.facultyCodeInUse(code.strip()));
		Faculty created = this.catalog.insertFaculty(cleanCode, name.strip(), departmentId, userId);
		audit("reference.faculty.created", "faculty", created.id(), created.code());
		return created;
	}

	@Transactional(readOnly = true)
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Faculty getFaculty(UUID id) {
		return this.catalog.findFaculty(id).orElseThrow(() -> notFound(FACULTY_NOT_FOUND, "faculty"));
	}

	@Transactional(readOnly = true)
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Page<Faculty> listFaculty(UUID department, String text, Pageable pageable) {
		return this.catalog.searchFaculty(department, text, pageable);
	}

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Faculty renameFaculty(UUID id, long expectedVersion, String name) {
		if (!this.catalog.facultyExists(id)) {
			throw notFound(FACULTY_NOT_FOUND, "faculty");
		}
		return this.catalog.renameFaculty(id, expectedVersion, name.strip());
	}

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public void deleteFaculty(UUID id) {
		if (!this.catalog.facultyExists(id)) {
			throw notFound(FACULTY_NOT_FOUND, "faculty");
		}
		this.catalog.deleteFaculty(id);
		this.auditLog.record(AuditEntry.of("reference.faculty.deleted", "faculty", id));
	}

	// --- terms ------------------------------------------------------------------------------------------------------

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public AcademicTerm createTerm(String code, String name, LocalDate startDate, LocalDate endDate) {
		if (endDate.isBefore(startDate)) {
			throw ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.",
					List.of(new ApiFieldError("endDate", "end_before_start", "The term's end date is before its start date.")));
		}
		String cleanCode = requireFreeCode(code, this.catalog.termCodeInUse(code.strip()));
		AcademicTerm created = this.catalog.insertTerm(cleanCode, name.strip(), startDate, endDate);
		audit("reference.term.created", "term", created.id(), created.code());
		return created;
	}

	@Transactional(readOnly = true)
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public AcademicTerm getTerm(UUID id) {
		return this.catalog.findTerm(id).orElseThrow(() -> notFound(TERM_NOT_FOUND, "term"));
	}

	@Transactional(readOnly = true)
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Page<AcademicTerm> listTerms(String text, Pageable pageable) {
		return this.catalog.searchTerms(text, pageable);
	}

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public AcademicTerm renameTerm(UUID id, long expectedVersion, String name) {
		if (!this.catalog.termExists(id)) {
			throw notFound(TERM_NOT_FOUND, "term");
		}
		return this.catalog.renameTerm(id, expectedVersion, name.strip());
	}

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public void deleteTerm(UUID id) {
		if (!this.catalog.termExists(id)) {
			throw notFound(TERM_NOT_FOUND, "term");
		}
		this.catalog.deleteTerm(id);
		this.auditLog.record(AuditEntry.of("reference.term.deleted", "term", id));
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	private String requireFreeCode(String code, boolean inUse) {
		if (inUse) {
			throw ApiException.conflict(DUPLICATE_CODE, "Something with this code already exists.");
		}
		return code.strip();
	}

	private void requireDepartment(UUID departmentId) {
		if (departmentId == null || !this.campus.departmentExists(departmentId)) {
			throw ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.",
					List.of(new ApiFieldError("departmentId", "unknown_department", "There is no department with that ID.")));
		}
	}

	private void audit(String action, String type, UUID id, String code) {
		this.auditLog.record(AuditEntry.of(action, type, id).withChange("code", code));
	}

	private static ApiException notFound(String code, String what) {
		return ApiException.notFound(code, "There is no " + what + " with that ID.");
	}

}
