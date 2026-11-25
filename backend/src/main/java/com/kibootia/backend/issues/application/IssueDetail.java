package com.kibootia.backend.issues.application;

import java.util.Set;
import java.util.UUID;

/** An issue with the faculty it mentions and the tickets linked to it. */
public record IssueDetail(Issue issue, Set<UUID> mentionedFacultyIds, Set<UUID> linkedTicketIds) {

}
