package com.kibootia.backend.rescheduling.application;

import java.util.Set;
import java.util.UUID;

import com.kibootia.backend.academic.LectureAccessFacts;
import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.reference.AssignmentDetails;

/**
 * What the policy needs to know about a lecture that does not exist yet: the place, the kind and the teacher of the teaching
 * assignment it would be a lecture of. It lets an extra lecture be authorized as any lecture is: the teacher may add one and
 * so may the HOD of the department for them. A missing teacher account never grants anything.
 */
record AssignmentFacts(UUID tenantId, UUID departmentId, UUID divisionId, Set<UUID> batchIds, SessionType sessionType,
		Set<UUID> teachingFacultyUserIds) implements LectureAccessFacts {

	static AssignmentFacts of(UUID tenantId, AssignmentDetails assignment) {
		Set<UUID> teachers = (assignment.facultyUserId() == null) ? Set.of() : Set.of(assignment.facultyUserId());
		return new AssignmentFacts(tenantId, assignment.departmentId(), assignment.divisionId(), assignment.batchIds(),
				assignment.sessionType(), teachers);
	}

}
