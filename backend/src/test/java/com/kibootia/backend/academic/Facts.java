package com.kibootia.backend.academic;

import java.util.Set;
import java.util.UUID;

/**
 * Facts for the tests: one record that is a lecture, a ticket and an issue at once, so a test can build the situation it
 * needs with {@link #lecture} and the {@code with...} methods and ask about it. Ids are fixed so that tests read as
 * stories: department A holds division 1 with batch 1, department B holds division 2 with batch 2.
 */
public record Facts(UUID tenantId, UUID departmentId, UUID divisionId, Set<UUID> batchIds, SessionType sessionType,
		Set<UUID> teachingFacultyUserIds, UUID authorUserId, Set<UUID> affectedFacultyUserIds,
		Set<UUID> requestedReviewerUserIds, boolean permanentChange, UUID openerUserId,
		Set<UUID> mentionedFacultyUserIds) implements TicketAccessFacts, IssueAccessFacts {

	public static final UUID TENANT = UUID.fromString("00000000-0000-7000-8000-000000000001");

	public static final UUID DEPARTMENT_A = UUID.fromString("aaaaaaaa-0000-7000-8000-00000000000a");

	public static final UUID DEPARTMENT_B = UUID.fromString("bbbbbbbb-0000-7000-8000-00000000000b");

	public static final UUID DIVISION_1 = UUID.fromString("d1d1d1d1-0000-7000-8000-000000000001");

	public static final UUID DIVISION_2 = UUID.fromString("d2d2d2d2-0000-7000-8000-000000000002");

	public static final UUID BATCH_1 = UUID.fromString("b1b1b1b1-0000-7000-8000-000000000001");

	public static final UUID BATCH_2 = UUID.fromString("b2b2b2b2-0000-7000-8000-000000000002");

	/** A lecture of division 1 in department A, for batch 1, that nobody teaches yet and no ticket concerns. */
	public static Facts lecture(SessionType sessionType) {
		return new Facts(TENANT, DEPARTMENT_A, DIVISION_1, Set.of(BATCH_1), sessionType, Set.of(), null, Set.of(), Set.of(),
				false, null, Set.of());
	}

	/** The same lecture in department B, division 2, batch 2. */
	public Facts inDepartmentB() {
		return new Facts(this.tenantId, DEPARTMENT_B, DIVISION_2, Set.of(BATCH_2), this.sessionType,
				this.teachingFacultyUserIds, this.authorUserId, this.affectedFacultyUserIds, this.requestedReviewerUserIds,
				this.permanentChange, this.openerUserId, this.mentionedFacultyUserIds);
	}

	public Facts taughtBy(UUID... users) {
		return new Facts(this.tenantId, this.departmentId, this.divisionId, this.batchIds, this.sessionType, Set.of(users),
				this.authorUserId, this.affectedFacultyUserIds, this.requestedReviewerUserIds, this.permanentChange,
				this.openerUserId, this.mentionedFacultyUserIds);
	}

	public Facts byAuthor(UUID author) {
		return new Facts(this.tenantId, this.departmentId, this.divisionId, this.batchIds, this.sessionType,
				this.teachingFacultyUserIds, author, this.affectedFacultyUserIds, this.requestedReviewerUserIds,
				this.permanentChange, this.openerUserId, this.mentionedFacultyUserIds);
	}

	public Facts affecting(UUID... users) {
		return new Facts(this.tenantId, this.departmentId, this.divisionId, this.batchIds, this.sessionType,
				this.teachingFacultyUserIds, this.authorUserId, Set.of(users), this.requestedReviewerUserIds,
				this.permanentChange, this.openerUserId, this.mentionedFacultyUserIds);
	}

	public Facts reviewedBy(UUID... users) {
		return new Facts(this.tenantId, this.departmentId, this.divisionId, this.batchIds, this.sessionType,
				this.teachingFacultyUserIds, this.authorUserId, this.affectedFacultyUserIds, Set.of(users),
				this.permanentChange, this.openerUserId, this.mentionedFacultyUserIds);
	}

	public Facts permanent() {
		return new Facts(this.tenantId, this.departmentId, this.divisionId, this.batchIds, this.sessionType,
				this.teachingFacultyUserIds, this.authorUserId, this.affectedFacultyUserIds, this.requestedReviewerUserIds,
				true, this.openerUserId, this.mentionedFacultyUserIds);
	}

	public Facts openedBy(UUID opener, UUID... mentioned) {
		return new Facts(this.tenantId, this.departmentId, this.divisionId, this.batchIds, this.sessionType,
				this.teachingFacultyUserIds, this.authorUserId, this.affectedFacultyUserIds, this.requestedReviewerUserIds,
				this.permanentChange, opener, Set.of(mentioned));
	}

	/** A lecture that says nothing about where it is, to prove that missing facts never match a grant. */
	public Facts withoutPlace() {
		return new Facts(this.tenantId, null, null, null, this.sessionType, this.teachingFacultyUserIds, this.authorUserId,
				this.affectedFacultyUserIds, this.requestedReviewerUserIds, this.permanentChange, this.openerUserId,
				this.mentionedFacultyUserIds);
	}

}
