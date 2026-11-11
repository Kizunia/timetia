package com.kibootia.backend.academic.domain;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.kibootia.backend.academic.AcademicPermissions;
import com.kibootia.backend.academic.AcademicScopes;
import com.kibootia.backend.academic.Facts;
import com.kibootia.backend.academic.LectureAccessFacts;
import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.platform.authorization.Caller;
import com.kibootia.backend.platform.authorization.GrantedScope;

import static com.kibootia.backend.academic.Facts.BATCH_1;
import static com.kibootia.backend.academic.Facts.BATCH_2;
import static com.kibootia.backend.academic.Facts.DEPARTMENT_A;
import static com.kibootia.backend.academic.Facts.DEPARTMENT_B;
import static com.kibootia.backend.academic.Facts.DIVISION_1;
import static com.kibootia.backend.academic.Facts.DIVISION_2;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every rule of the role and scope model, allowed and denied, with the cross-department, cross-division and
 * cross-batch cases that the model exists to prevent. The tests state the documented rule, not the code's branches.
 */
class AcademicAccessPolicyTest {

	static final UUID FACULTY = UUID.fromString("f0000000-0000-7000-8000-000000000001");

	static final UUID OTHER_FACULTY = UUID.fromString("f0000000-0000-7000-8000-000000000002");

	static final UUID HOD = UUID.fromString("c0000000-0000-7000-8000-000000000001");

	static final UUID SOMEONE = UUID.fromString("99999999-0000-7000-8000-000000000009");

	static final GrantedScope GLOBAL = GrantedScope.global();

	static final GrantedScope HOD_OF_A = GrantedScope.of(AcademicScopes.DEPARTMENT, DEPARTMENT_A);

	static final GrantedScope HOD_OF_B = GrantedScope.of(AcademicScopes.DEPARTMENT, DEPARTMENT_B);

	static final GrantedScope CR_OF_1 = GrantedScope.of(AcademicScopes.DIVISION, DIVISION_1);

	static final GrantedScope CR_OF_2 = GrantedScope.of(AcademicScopes.DIVISION, DIVISION_2);

	static final GrantedScope BR_OF_1 = GrantedScope.of(AcademicScopes.BATCH, BATCH_1);

	static final GrantedScope BR_OF_2 = GrantedScope.of(AcademicScopes.BATCH, BATCH_2);

	private final AcademicAccessPolicy policy = new AcademicAccessPolicy();

	private boolean allows(String action, UUID user, Object facts, GrantedScope... grants) {
		return this.policy.allows(Caller.user(user, Facts.TENANT, true), action, facts, List.of(grants));
	}

	/** A lecture and nothing else: it has no author, no reviewers and no opener. */
	record LectureOnly(UUID tenantId, UUID departmentId, UUID divisionId, Set<UUID> batchIds, SessionType sessionType,
			Set<UUID> teachingFacultyUserIds) implements LectureAccessFacts {

	}

	static final LectureOnly PLAIN = new LectureOnly(Facts.TENANT, DEPARTMENT_A, DIVISION_1, Set.of(BATCH_1),
			SessionType.THEORY, Set.of(FACULTY));

	@Test
	@DisplayName("supports lectures and the records about them, and nothing else")
	void supports() {
		assertThat(this.policy.supports(Facts.lecture(SessionType.THEORY))).isTrue();
		assertThat(this.policy.supports(PLAIN)).isTrue();
		assertThat(this.policy.supports("a note")).isFalse();
	}

	@Test
	@DisplayName("denies an action it does not know, whatever the grants")
	void unknownAction() {
		assertThat(allows("rescheduling.tickets.override-conflict", HOD, Facts.lecture(SessionType.THEORY), GLOBAL)).isFalse();
		assertThat(allows("widgets.widgets.update", HOD, Facts.lecture(SessionType.THEORY), GLOBAL)).isFalse();
	}

	@Nested
	@DisplayName("seeing the live timetable")
	class LiveTimetable {

		Facts lecture = Facts.lecture(SessionType.THEORY);

		@Test
		@DisplayName("a department grant covers its own department and no other")
		void department() {
			assertThat(allows(AcademicPermissions.LIVE_VIEW, HOD, this.lecture, HOD_OF_A)).isTrue();
			assertThat(allows(AcademicPermissions.LIVE_VIEW, HOD, this.lecture.inDepartmentB(), HOD_OF_A)).isFalse();
			assertThat(allows(AcademicPermissions.LIVE_VIEW, HOD, this.lecture.inDepartmentB(), HOD_OF_B)).isTrue();
		}

		@Test
		@DisplayName("a division grant covers its own division only, in any kind of session")
		void division() {
			assertThat(allows(AcademicPermissions.LIVE_VIEW, SOMEONE, this.lecture, CR_OF_1)).isTrue();
			assertThat(allows(AcademicPermissions.LIVE_VIEW, SOMEONE, Facts.lecture(SessionType.LAB), CR_OF_1)).isTrue();
			assertThat(allows(AcademicPermissions.LIVE_VIEW, SOMEONE, this.lecture, CR_OF_2)).isFalse();
		}

		@Test
		@DisplayName("a batch grant covers a lecture that lists the batch, and not one that lists another")
		void batch() {
			assertThat(allows(AcademicPermissions.LIVE_VIEW, SOMEONE, this.lecture, BR_OF_1)).isTrue();
			assertThat(allows(AcademicPermissions.LIVE_VIEW, SOMEONE, this.lecture, BR_OF_2)).isFalse();
		}

		@Test
		@DisplayName("a student of a batch sees a shared lecture that lists their batch among others")
		void sharedLecture() {
			Facts shared = new Facts(Facts.TENANT, DEPARTMENT_A, DIVISION_1, Set.of(BATCH_1, BATCH_2), SessionType.THEORY,
					Set.of(), null, Set.of(), Set.of(), false, null, Set.of());

			assertThat(allows(AcademicPermissions.LIVE_VIEW, SOMEONE, shared, BR_OF_2)).isTrue();
		}

		@Test
		@DisplayName("a global grant covers everything, and a grant of another place covers nothing")
		void globalAndForeign() {
			assertThat(allows(AcademicPermissions.LIVE_VIEW, SOMEONE, this.lecture.inDepartmentB(), GLOBAL)).isTrue();
			assertThat(allows(AcademicPermissions.LIVE_VIEW, SOMEONE, this.lecture, CR_OF_2, BR_OF_2, HOD_OF_B)).isFalse();
		}

		@Test
		@DisplayName("a lecture that says nothing about where it is matches no place, only a global grant")
		void missingFacts() {
			Facts nowhere = this.lecture.withoutPlace();

			assertThat(allows(AcademicPermissions.LIVE_VIEW, SOMEONE, nowhere, HOD_OF_A, CR_OF_1, BR_OF_1)).isFalse();
			assertThat(allows(AcademicPermissions.LIVE_VIEW, SOMEONE, nowhere, GLOBAL)).isTrue();
		}

		@Test
		@DisplayName("a faculty member sees a lecture they teach, in any division, and not one they do not")
		void faculty() {
			assertThat(allows(AcademicPermissions.LIVE_VIEW_OWN, FACULTY, this.lecture.taughtBy(FACULTY), GLOBAL)).isTrue();
			assertThat(allows(AcademicPermissions.LIVE_VIEW_OWN, FACULTY, this.lecture.inDepartmentB().taughtBy(FACULTY),
					GLOBAL)).isTrue();
			assertThat(allows(AcademicPermissions.LIVE_VIEW_OWN, FACULTY, this.lecture.taughtBy(OTHER_FACULTY), GLOBAL))
				.isFalse();
		}

	}

	@Nested
	@DisplayName("raising and cancelling")
	class RaisingAndCancelling {

		Facts lecture = Facts.lecture(SessionType.THEORY).taughtBy(FACULTY);

		@Test
		@DisplayName("a faculty member acts only on a lecture they teach, whatever their grant covers")
		void teacher() {
			for (String action : List.of(AcademicPermissions.TICKETS_CREATE, AcademicPermissions.TICKETS_CANCEL)) {
				assertThat(allows(action, FACULTY, this.lecture, GLOBAL)).as(action).isTrue();
				assertThat(allows(action, OTHER_FACULTY, this.lecture, GLOBAL)).as(action).isFalse();
			}
		}

		@Test
		@DisplayName("an HOD acts on behalf only within their department; a Super HOD anywhere")
		void onBehalf() {
			for (String action : List.of(AcademicPermissions.TICKETS_CREATE_ON_BEHALF,
					AcademicPermissions.TICKETS_CANCEL_ON_BEHALF)) {
				Facts ticket = this.lecture.byAuthor(HOD);
				assertThat(allows(action, HOD, ticket, HOD_OF_A)).as(action).isTrue();
				assertThat(allows(action, HOD, ticket.inDepartmentB(), HOD_OF_A)).as(action).isFalse();
				assertThat(allows(action, HOD, ticket.inDepartmentB(), GLOBAL)).as(action).isTrue();
			}
		}

		@Test
		@DisplayName("a faculty member applies their own change only when they are its author and teach the lecture")
		void selfApproval() {
			assertThat(allows(AcademicPermissions.TICKETS_SELF_APPROVE, FACULTY, this.lecture.byAuthor(FACULTY), GLOBAL))
				.isTrue();
			assertThat(allows(AcademicPermissions.TICKETS_SELF_APPROVE, FACULTY, this.lecture.byAuthor(OTHER_FACULTY), GLOBAL))
				.isFalse();
			// An HOD acting for the teacher authors the ticket but does not teach: not a self-approval.
			assertThat(allows(AcademicPermissions.TICKETS_SELF_APPROVE, HOD, this.lecture.byAuthor(HOD), HOD_OF_A)).isFalse();
			assertThat(allows(AcademicPermissions.TICKETS_SELF_APPROVE, FACULTY, PLAIN, GLOBAL)).isFalse();
		}

	}

	@Nested
	@DisplayName("reviewing and rejecting")
	class Reviewing {

		@Test
		@DisplayName("only a person who was asked can review or reject")
		void mustBeAsked() {
			Facts ticket = Facts.lecture(SessionType.THEORY).reviewedBy(OTHER_FACULTY);
			for (String action : List.of(AcademicPermissions.TICKETS_REVIEW, AcademicPermissions.TICKETS_REJECT)) {
				assertThat(allows(action, OTHER_FACULTY, ticket, GLOBAL)).as(action).isTrue();
				assertThat(allows(action, FACULTY, ticket, GLOBAL)).as(action).isFalse();
				assertThat(allows(action, HOD, ticket, HOD_OF_A)).as("an HOD who was not asked, " + action).isFalse();
			}
		}

		@Test
		@DisplayName("an HOD who was asked reviews only a lecture of their own department")
		void hodDepartment() {
			Facts ticket = Facts.lecture(SessionType.THEORY).reviewedBy(HOD);

			assertThat(allows(AcademicPermissions.TICKETS_REVIEW, HOD, ticket, HOD_OF_A)).isTrue();
			assertThat(allows(AcademicPermissions.TICKETS_REVIEW, HOD, ticket.inDepartmentB(), HOD_OF_A)).isFalse();
			assertThat(allows(AcademicPermissions.TICKETS_REJECT, HOD, ticket.inDepartmentB(), GLOBAL)).isTrue();
		}

		@Test
		@DisplayName("a CR reviews a theory lecture of their division, not a lab or tutorial, not another division")
		void crIsTheoryOnly() {
			Facts theory = Facts.lecture(SessionType.THEORY).reviewedBy(SOMEONE);

			assertThat(allows(AcademicPermissions.TICKETS_REVIEW, SOMEONE, theory, CR_OF_1)).isTrue();
			assertThat(allows(AcademicPermissions.TICKETS_REJECT, SOMEONE, theory, CR_OF_1)).isTrue();
			for (SessionType session : List.of(SessionType.LAB, SessionType.TUTORIAL)) {
				assertThat(allows(AcademicPermissions.TICKETS_REVIEW, SOMEONE, Facts.lecture(session).reviewedBy(SOMEONE),
						CR_OF_1)).as(session.name()).isFalse();
			}
			assertThat(allows(AcademicPermissions.TICKETS_REVIEW, SOMEONE, theory.inDepartmentB(), CR_OF_1)).isFalse();
		}

		@Test
		@DisplayName("a BR reviews a lab or tutorial lecture of their batch, not theory, not another batch")
		void brIsLabAndTutorialOnly() {
			for (SessionType session : List.of(SessionType.LAB, SessionType.TUTORIAL)) {
				Facts ticket = Facts.lecture(session).reviewedBy(SOMEONE);
				assertThat(allows(AcademicPermissions.TICKETS_REVIEW, SOMEONE, ticket, BR_OF_1)).as(session.name()).isTrue();
				assertThat(allows(AcademicPermissions.TICKETS_REJECT, SOMEONE, ticket, BR_OF_1)).as(session.name()).isTrue();
				assertThat(allows(AcademicPermissions.TICKETS_REVIEW, SOMEONE, ticket, BR_OF_2)).as(session.name()).isFalse();
			}
			assertThat(allows(AcademicPermissions.TICKETS_REVIEW, SOMEONE, Facts.lecture(SessionType.THEORY).reviewedBy(SOMEONE),
					BR_OF_1)).isFalse();
		}

		@Test
		@DisplayName("a lecture with no session type is matched by no CR or BR grant")
		void unknownSessionType() {
			Facts ticket = new Facts(Facts.TENANT, DEPARTMENT_A, DIVISION_1, Set.of(BATCH_1), null, Set.of(), null, Set.of(),
					Set.of(SOMEONE), false, null, Set.of());

			assertThat(allows(AcademicPermissions.TICKETS_REVIEW, SOMEONE, ticket, CR_OF_1)).isFalse();
			assertThat(allows(AcademicPermissions.TICKETS_REVIEW, SOMEONE, ticket, BR_OF_1)).isFalse();
			assertThat(allows(AcademicPermissions.TICKETS_REVIEW, SOMEONE, ticket, HOD_OF_A)).isTrue();
		}

	}

	@Nested
	@DisplayName("approving a permanent change and reverting")
	class ApprovingAndReverting {

		Facts ticket = Facts.lecture(SessionType.THEORY).taughtBy(FACULTY).byAuthor(FACULTY).affecting(OTHER_FACULTY);

		@Test
		@DisplayName("an HOD approves a permanent change of their own department only; a Super HOD any")
		void approvePermanent() {
			Facts permanent = this.ticket.permanent();

			assertThat(allows(AcademicPermissions.TICKETS_APPROVE_PERMANENT, HOD, permanent, HOD_OF_A)).isTrue();
			assertThat(allows(AcademicPermissions.TICKETS_APPROVE_PERMANENT, HOD, permanent.inDepartmentB(), HOD_OF_A)).isFalse();
			assertThat(allows(AcademicPermissions.TICKETS_APPROVE_PERMANENT, HOD, permanent.inDepartmentB(), GLOBAL)).isTrue();
			assertThat(allows(AcademicPermissions.TICKETS_APPROVE_PERMANENT, HOD, PLAIN, HOD_OF_A)).isFalse();
		}

		@Test
		@DisplayName("the author and an affected faculty revert a change that is not permanent, and no one else")
		void revertByRelation() {
			assertThat(allows(AcademicPermissions.TICKETS_REVERT, FACULTY, this.ticket, GLOBAL)).isTrue();
			assertThat(allows(AcademicPermissions.TICKETS_REVERT, OTHER_FACULTY, this.ticket, GLOBAL)).isTrue();
			assertThat(allows(AcademicPermissions.TICKETS_REVERT, SOMEONE, this.ticket, GLOBAL)).isFalse();
		}

		@Test
		@DisplayName("a permanent change is not reverted by its author or an affected faculty, only in scope")
		void permanentChangesAreRevertedInScope() {
			Facts permanent = this.ticket.permanent();

			assertThat(allows(AcademicPermissions.TICKETS_REVERT, FACULTY, permanent, GLOBAL)).isFalse();
			assertThat(allows(AcademicPermissions.TICKETS_REVERT, OTHER_FACULTY, permanent, GLOBAL)).isFalse();
			assertThat(allows(AcademicPermissions.TICKETS_REVERT_IN_SCOPE, HOD, permanent, HOD_OF_A)).isTrue();
		}

		@Test
		@DisplayName("an HOD reverts any change in their department and none in another")
		void revertInScope() {
			assertThat(allows(AcademicPermissions.TICKETS_REVERT_IN_SCOPE, HOD, this.ticket, HOD_OF_A)).isTrue();
			assertThat(allows(AcademicPermissions.TICKETS_REVERT_IN_SCOPE, HOD, this.ticket.inDepartmentB(), HOD_OF_A))
				.isFalse();
			assertThat(allows(AcademicPermissions.TICKETS_REVERT_IN_SCOPE, HOD, this.ticket.inDepartmentB(), GLOBAL)).isTrue();
		}

		@Test
		@DisplayName("only the author edits, resubmits, escalates or withdraws a ticket")
		void editOwn() {
			assertThat(allows(AcademicPermissions.TICKETS_EDIT_OWN, FACULTY, this.ticket, GLOBAL)).isTrue();
			assertThat(allows(AcademicPermissions.TICKETS_EDIT_OWN, OTHER_FACULTY, this.ticket, GLOBAL)).isFalse();
			assertThat(allows(AcademicPermissions.TICKETS_EDIT_OWN, HOD, this.ticket, HOD_OF_A)).isFalse();
		}

	}

	@Nested
	@DisplayName("seeing a ticket and its audit log")
	class Visibility {

		Facts ticket = Facts.lecture(SessionType.THEORY).byAuthor(FACULTY).affecting(OTHER_FACULTY).reviewedBy(HOD);

		@Test
		@DisplayName("the author, an affected faculty and a requested reviewer see the ticket and its log; a stranger does not")
		void people() {
			for (String action : List.of(AcademicPermissions.TICKETS_VIEW, AcademicPermissions.TICKET_AUDIT_VIEW)) {
				assertThat(allows(action, FACULTY, this.ticket, GLOBAL)).as(action).isTrue();
				assertThat(allows(action, OTHER_FACULTY, this.ticket, GLOBAL)).as(action).isTrue();
				assertThat(allows(action, HOD, this.ticket, GLOBAL)).as(action).isTrue();
				assertThat(allows(action, SOMEONE, this.ticket, GLOBAL)).as(action).isFalse();
			}
		}

		@Test
		@DisplayName("a faculty member's global grant does not show them every ticket")
		void globalGrantIsNotAnyTicket() {
			assertThat(allows(AcademicPermissions.TICKETS_VIEW, SOMEONE, this.ticket, GLOBAL)).isFalse();
		}

		@Test
		@DisplayName("an HOD sees the tickets and logs of their department and not of another")
		void hodInScope() {
			for (String action : List.of(AcademicPermissions.TICKETS_VIEW_IN_SCOPE,
					AcademicPermissions.TICKET_AUDIT_VIEW_IN_SCOPE)) {
				assertThat(allows(action, SOMEONE, this.ticket, HOD_OF_A)).as(action).isTrue();
				assertThat(allows(action, SOMEONE, this.ticket.inDepartmentB(), HOD_OF_A)).as(action).isFalse();
				assertThat(allows(action, SOMEONE, this.ticket.inDepartmentB(), GLOBAL)).as(action).isTrue();
			}
		}

		@Test
		@DisplayName("a CR sees a theory ticket of their division and not a lab one; a BR the reverse")
		void representativesInScope() {
			Facts lab = Facts.lecture(SessionType.LAB);
			for (String action : List.of(AcademicPermissions.TICKETS_VIEW_IN_SCOPE,
					AcademicPermissions.TICKET_AUDIT_VIEW_IN_SCOPE)) {
				assertThat(allows(action, SOMEONE, this.ticket, CR_OF_1)).as(action).isTrue();
				assertThat(allows(action, SOMEONE, lab, CR_OF_1)).as(action).isFalse();
				assertThat(allows(action, SOMEONE, lab, BR_OF_1)).as(action).isTrue();
				assertThat(allows(action, SOMEONE, this.ticket, BR_OF_1)).as(action).isFalse();
				assertThat(allows(action, SOMEONE, lab, BR_OF_2)).as(action).isFalse();
				assertThat(allows(action, SOMEONE, this.ticket.inDepartmentB(), CR_OF_1)).as(action).isFalse();
			}
		}

		@Test
		@DisplayName("a lecture alone is not a ticket, so a ticket right never applies to it")
		void lecturesAreNotTickets() {
			for (String action : List.of(AcademicPermissions.TICKETS_VIEW, AcademicPermissions.TICKETS_VIEW_IN_SCOPE,
					AcademicPermissions.TICKET_AUDIT_VIEW, AcademicPermissions.TICKET_AUDIT_VIEW_IN_SCOPE,
					AcademicPermissions.TICKETS_REVIEW, AcademicPermissions.TICKETS_REVERT,
					AcademicPermissions.TICKETS_REVERT_IN_SCOPE, AcademicPermissions.TICKETS_EDIT_OWN)) {
				assertThat(allows(action, FACULTY, PLAIN, GLOBAL)).as(action).isFalse();
			}
		}

	}

	@Nested
	@DisplayName("issues")
	class Issues {

		@Test
		@DisplayName("a CR opens an issue about a theory lecture of their division only, and a BR about a lab or tutorial of their batch")
		void opening() {
			Facts theory = Facts.lecture(SessionType.THEORY);
			Facts lab = Facts.lecture(SessionType.LAB);
			Facts tutorial = Facts.lecture(SessionType.TUTORIAL);

			assertThat(allows(AcademicPermissions.ISSUES_OPEN, SOMEONE, theory, CR_OF_1)).isTrue();
			assertThat(allows(AcademicPermissions.ISSUES_OPEN, SOMEONE, lab, CR_OF_1)).isFalse();
			assertThat(allows(AcademicPermissions.ISSUES_OPEN, SOMEONE, theory, CR_OF_2)).isFalse();
			assertThat(allows(AcademicPermissions.ISSUES_OPEN, SOMEONE, lab, BR_OF_1)).isTrue();
			assertThat(allows(AcademicPermissions.ISSUES_OPEN, SOMEONE, tutorial, BR_OF_1)).isTrue();
			assertThat(allows(AcademicPermissions.ISSUES_OPEN, SOMEONE, theory, BR_OF_1)).isFalse();
			assertThat(allows(AcademicPermissions.ISSUES_OPEN, SOMEONE, lab, BR_OF_2)).isFalse();
		}

		@Test
		@DisplayName("the opener and a mentioned faculty close an issue; no one else, and not a record that is not an issue")
		void closing() {
			Facts issue = Facts.lecture(SessionType.THEORY).openedBy(SOMEONE, FACULTY);

			assertThat(allows(AcademicPermissions.ISSUES_CLOSE, SOMEONE, issue, CR_OF_1)).isTrue();
			assertThat(allows(AcademicPermissions.ISSUES_CLOSE, FACULTY, issue, GLOBAL)).isTrue();
			assertThat(allows(AcademicPermissions.ISSUES_CLOSE, OTHER_FACULTY, issue, GLOBAL)).isFalse();
			assertThat(allows(AcademicPermissions.ISSUES_CLOSE, SOMEONE, PLAIN, CR_OF_1)).isFalse();
		}

	}

}
