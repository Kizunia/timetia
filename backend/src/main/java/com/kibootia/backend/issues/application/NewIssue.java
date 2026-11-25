package com.kibootia.backend.issues.application;

import java.util.Set;
import java.util.UUID;

import com.kibootia.backend.academic.SessionType;

/** What is needed to open an issue: where, what and who, with the faculty it mentions. */
public record NewIssue(SessionType sessionType, UUID departmentId, UUID divisionId, UUID batchId, String title,
		String description, UUID openerUserId, Set<UUID> mentionedFacultyIds) {

}
