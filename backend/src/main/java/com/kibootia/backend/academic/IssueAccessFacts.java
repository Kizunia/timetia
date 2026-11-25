package com.kibootia.backend.academic;

import java.util.Set;
import java.util.UUID;

/** What the policy needs to know about an issue a CR or BR opened about a lecture. */
public interface IssueAccessFacts extends LectureAccessFacts {

	UUID openerUserId();

	/** The faculty mentioned on the issue, as user ids. */
	Set<UUID> mentionedFacultyUserIds();

}
