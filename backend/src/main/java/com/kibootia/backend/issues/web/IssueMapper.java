package com.kibootia.backend.issues.web;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.kibootia.backend.issues.application.Issue;
import com.kibootia.backend.issues.application.IssueDetail;
import com.kibootia.backend.issues.application.IssueService.Draft;

/** Converts between API types and application types by hand (ADR 0005). */
final class IssueMapper {

	private IssueMapper() {
	}

	static Draft toDraft(CreateIssueRequest request) {
		return new Draft(request.sessionType(), request.divisionId(), request.batchId(), request.title(),
				request.description(), request.mentionedFacultyIds());
	}

	static IssueResponse toResponse(IssueDetail detail) {
		Issue issue = detail.issue();
		return new IssueResponse(issue.id(), issue.status(), issue.sessionType(), issue.departmentId(), issue.divisionId(),
				issue.batchId(), issue.title(), issue.description(), issue.openerUserId(), sorted(detail.mentionedFacultyIds()),
				sorted(detail.linkedTicketIds()), issue.closedAt(), issue.closedByUserId(), issue.version(), issue.createdAt(),
				issue.updatedAt());
	}

	/** A set has no order, and a response should not change between two reads. */
	private static List<UUID> sorted(Set<UUID> ids) {
		return ids.stream().sorted(Comparator.comparing(UUID::toString)).toList();
	}

}
