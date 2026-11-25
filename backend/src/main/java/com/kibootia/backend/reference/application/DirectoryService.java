package com.kibootia.backend.reference.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.reference.AcademicDirectory;
import com.kibootia.backend.reference.AssignmentDetails;

/**
 * The campus names any verified person may read: departments, divisions, batches, rooms and faculty, and what the caller
 * themselves teaches. Names of places are not confidential, and every screen needs them to turn an id into a word, so
 * these reads ask for no permission beyond a verified email. Each list holds at most {@value #LIMIT} rows.
 */
@Service
public class DirectoryService {

	/** The most rows any one list returns. */
	public static final int LIMIT = 500;

	private static final Sort BY_CODE = Sort.by(Sort.Direction.ASC, "code");

	private static final Sort BY_NAME = Sort.by(Sort.Direction.ASC, "name");

	public record DepartmentRef(UUID id, String code, String name) {

	}

	public record DivisionRef(UUID id, String code, String name, UUID departmentId) {

	}

	public record BatchRef(UUID id, String code, String name, UUID divisionId) {

	}

	public record RoomRef(UUID id, String code, String name, RoomType roomType, Integer capacity) {

	}

	/** A faculty member; {@code hasAccount} says whether a sign-in account is linked, without naming it. */
	public record FacultyRef(UUID id, String code, String name, UUID departmentId, boolean hasAccount) {

	}

	/** One thing a faculty member teaches. {@code batchId} and {@code batchCode} are null for theory. */
	public record MyAssignment(UUID id, UUID divisionId, String divisionCode, UUID batchId, String batchCode,
			UUID subjectId, String subjectCode, String subjectName, UUID facultyId, SessionType sessionType) {

	}

	public record MyCampus(List<FacultyRef> faculty, List<MyAssignment> assignments) {

	}

	private final CampusStore campus;

	private final CatalogStore catalog;

	private final TeachingAssignmentStore assignments;

	private final AcademicDirectory directory;

	private final Authorization authorization;

	DirectoryService(CampusStore campus, CatalogStore catalog, TeachingAssignmentStore assignments,
			AcademicDirectory directory, Authorization authorization) {
		this.campus = campus;
		this.catalog = catalog;
		this.assignments = assignments;
		this.directory = directory;
		this.authorization = authorization;
	}

	@Transactional(readOnly = true)
	@NoAuthorization("campus names are not confidential; any verified person reads them")
	public List<DepartmentRef> departments() {
		this.authorization.requireVerified();
		return this.campus.searchDepartments(null, page(BY_CODE))
			.getContent()
			.stream()
			.map((department) -> new DepartmentRef(department.id(), department.code(), department.name()))
			.toList();
	}

	@Transactional(readOnly = true)
	@NoAuthorization("campus names are not confidential; any verified person reads them")
	public List<DivisionRef> divisions() {
		this.authorization.requireVerified();
		return this.campus.searchDivisions(null, null, page(BY_CODE))
			.getContent()
			.stream()
			.map((division) -> new DivisionRef(division.id(), division.code(), division.name(), division.departmentId()))
			.toList();
	}

	/** @param divisionId only the batches of this division; null for all of them */
	@Transactional(readOnly = true)
	@NoAuthorization("campus names are not confidential; any verified person reads them")
	public List<BatchRef> batches(UUID divisionId) {
		this.authorization.requireVerified();
		return this.campus.searchBatches(divisionId, null, page(BY_CODE))
			.getContent()
			.stream()
			.map((batch) -> new BatchRef(batch.id(), batch.code(), batch.name(), batch.divisionId()))
			.toList();
	}

	@Transactional(readOnly = true)
	@NoAuthorization("campus names are not confidential; any verified person reads them")
	public List<RoomRef> rooms() {
		this.authorization.requireVerified();
		return this.catalog.searchRooms(null, page(BY_CODE))
			.getContent()
			.stream()
			.map((room) -> new RoomRef(room.id(), room.code(), room.name(), room.roomType(), room.capacity()))
			.toList();
	}

	/** @param departmentId only the faculty of this department; null for all of them */
	@Transactional(readOnly = true)
	@NoAuthorization("campus names are not confidential; any verified person reads them")
	public List<FacultyRef> faculties(UUID departmentId) {
		this.authorization.requireVerified();
		return this.catalog.searchFaculty(departmentId, null, page(BY_NAME))
			.getContent()
			.stream()
			.map(DirectoryService::toRef)
			.toList();
	}

	/** The faculty records linked to the caller's account and what they teach; both empty for a person who is not faculty. */
	@Transactional(readOnly = true)
	@NoAuthorization("a person reads the faculty records and teaching assignments that belong to their own account")
	public MyCampus myCampus() {
		this.authorization.requireVerified();
		List<UUID> facultyIds = this.catalog.findFacultyIdsOfUser(this.authorization.currentUserId());
		if (facultyIds.isEmpty()) {
			return new MyCampus(List.of(), List.of());
		}
		List<FacultyRef> faculty = this.catalog.findFacultyByIds(facultyIds)
			.stream()
			.map(DirectoryService::toRef)
			.sorted(Comparator.comparing(FacultyRef::name).thenComparing(FacultyRef::id))
			.toList();
		List<UUID> assignmentIds = this.assignments.findByFaculty(facultyIds).stream().map(TeachingAssignment::id).toList();
		Map<UUID, AssignmentDetails> details = this.directory.assignments(assignmentIds);
		List<MyAssignment> mine = details.values()
			.stream()
			.sorted(Comparator.comparing(AssignmentDetails::divisionCode)
				.thenComparing(AssignmentDetails::subjectCode)
				.thenComparing(AssignmentDetails::sessionType)
				.thenComparing(AssignmentDetails::batchCode, Comparator.nullsFirst(Comparator.naturalOrder()))
				.thenComparing(AssignmentDetails::id))
			.map((detail) -> new MyAssignment(detail.id(), detail.divisionId(), detail.divisionCode(), detail.batchId(),
					detail.batchCode(), detail.subjectId(), detail.subjectCode(), detail.subjectName(), detail.facultyId(),
					detail.sessionType()))
			.toList();
		return new MyCampus(faculty, mine);
	}

	private static FacultyRef toRef(Faculty faculty) {
		return new FacultyRef(faculty.id(), faculty.code(), faculty.name(), faculty.departmentId(), faculty.userId() != null);
	}

	private static PageRequest page(Sort sort) {
		return PageRequest.of(0, LIMIT, sort);
	}

}
