package com.kibootia.backend.issues.application;

import java.util.Set;
import java.util.UUID;

import com.kibootia.backend.issues.domain.IssueStatus;

/** What a list of issues is narrowed by, and which issues the caller may see at all. */
public record IssueQuery(IssueStatus status, UUID openedBy, Visibility visibility) {

	/**
	 * Which issues the caller may see, as conditions on the issue's own columns; one holding is enough. The session rule of
	 * the model applies to scopes: a CR sees the theory issues of the division and a BR the lab and tutorial issues of the
	 * batch, while a department sees all of its issues.
	 *
	 * @param openedBy issues this user opened, or null
	 * @param mentionedFaculty issues that mention one of these faculty members
	 */
	public record Visibility(boolean everywhere, Set<UUID> departments, Set<UUID> theoryOfDivisions, Set<UUID> labsOfBatches,
			UUID openedBy, Set<UUID> mentionedFaculty) {

		public boolean isEmpty() {
			return !this.everywhere && this.departments.isEmpty() && this.theoryOfDivisions.isEmpty()
					&& this.labsOfBatches.isEmpty() && this.openedBy == null && this.mentionedFaculty.isEmpty();
		}

	}

}
