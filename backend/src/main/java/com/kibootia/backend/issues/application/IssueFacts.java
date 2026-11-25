package com.kibootia.backend.issues.application;

import java.util.Set;
import java.util.UUID;

import com.kibootia.backend.academic.IssueAccessFacts;
import com.kibootia.backend.academic.SessionType;

/**
 * What the authorization policy needs to know about an issue (integration contract): the place and kind stored with it, who
 * opened it, and the accounts of the faculty it mentions as they are now. Nobody teaches an issue, and a missing value
 * never grants anything.
 */
record IssueFacts(UUID tenantId, UUID departmentId, UUID divisionId, Set<UUID> batchIds, SessionType sessionType,
		UUID openerUserId, Set<UUID> mentionedFacultyUserIds) implements IssueAccessFacts {

	@Override
	public Set<UUID> teachingFacultyUserIds() {
		return Set.of();
	}

}
