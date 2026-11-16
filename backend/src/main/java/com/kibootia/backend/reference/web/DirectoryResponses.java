package com.kibootia.backend.reference.web;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.reference.application.RoomType;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** The answers of the campus directory: names and ids only, the same for everyone who may read it. */
final class DirectoryResponses {

	private DirectoryResponses() {
	}

	record DirectoryDepartmentResponse(
			@Schema(requiredMode = REQUIRED) UUID id,
			@Schema(requiredMode = REQUIRED) String code,
			@Schema(requiredMode = REQUIRED) String name) {

	}

	record DirectoryDivisionResponse(
			@Schema(requiredMode = REQUIRED) UUID id,
			@Schema(requiredMode = REQUIRED) String code,
			@Schema(requiredMode = REQUIRED) String name,
			@Schema(requiredMode = REQUIRED) UUID departmentId) {

	}

	record DirectoryBatchResponse(
			@Schema(requiredMode = REQUIRED) UUID id,
			@Schema(requiredMode = REQUIRED) String code,
			@Schema(requiredMode = REQUIRED) String name,
			@Schema(requiredMode = REQUIRED) UUID divisionId) {

	}

	/** {@code capacity} is null when the number of seats is unknown. */
	record DirectoryRoomResponse(
			@Schema(requiredMode = REQUIRED) UUID id,
			@Schema(requiredMode = REQUIRED) String code,
			@Schema(requiredMode = REQUIRED) String name,
			@Schema(requiredMode = REQUIRED) RoomType roomType,
			@Schema(nullable = true) Integer capacity) {

	}

	/** {@code hasAccount} says whether a sign-in account is linked to the faculty member. */
	record DirectoryFacultyResponse(
			@Schema(requiredMode = REQUIRED) UUID id,
			@Schema(requiredMode = REQUIRED) String code,
			@Schema(requiredMode = REQUIRED) String name,
			@Schema(requiredMode = REQUIRED) UUID departmentId,
			@Schema(requiredMode = REQUIRED) boolean hasAccount) {

	}

	/** One thing the caller teaches; {@code batchId} and {@code batchCode} are null for theory. */
	record MyAssignmentResponse(
			@Schema(requiredMode = REQUIRED) UUID id,
			@Schema(requiredMode = REQUIRED) UUID divisionId,
			@Schema(requiredMode = REQUIRED) String divisionCode,
			@Schema(nullable = true) UUID batchId,
			@Schema(nullable = true) String batchCode,
			@Schema(requiredMode = REQUIRED) UUID subjectId,
			@Schema(requiredMode = REQUIRED) String subjectCode,
			@Schema(requiredMode = REQUIRED) String subjectName,
			@Schema(requiredMode = REQUIRED) UUID facultyId,
			@Schema(requiredMode = REQUIRED) SessionType sessionType) {

	}

	/** The faculty records linked to the caller's account (normally none or one) and what those faculty teach. */
	record MyCampusResponse(
			@Schema(requiredMode = REQUIRED) List<DirectoryFacultyResponse> faculty,
			@Schema(requiredMode = REQUIRED) List<MyAssignmentResponse> assignments) {

	}

}
