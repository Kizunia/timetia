package com.kibootia.backend.academic.domain;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.kibootia.backend.academic.AcademicPermissions;
import com.kibootia.backend.academic.AcademicScopes;
import com.kibootia.backend.academic.IssueAccessFacts;
import com.kibootia.backend.academic.LectureAccessFacts;
import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.academic.TicketAccessFacts;
import com.kibootia.backend.platform.authorization.Caller;
import com.kibootia.backend.platform.authorization.GrantedScope;
import com.kibootia.backend.platform.authorization.ScopedResourcePolicy;

/**
 * The rules of the role and scope model, one per permission (Timetia ADR 0004). It is called only when the caller
 * holds the permission in at least one place, and it can only narrow that: a rule that does not match answers no.
 *
 * <p>A permission is either relation-based (the caller must be one of the named people on the record), scope-based (a
 * grant must cover the record) or both. {@link #covers} says when a grant covers a lecture:
 * <ul>
 * <li>a global grant covers every lecture;</li>
 * <li>a department grant covers a lecture of that department;</li>
 * <li>a division grant covers a lecture of that division, and a batch grant a lecture that lists the batch;</li>
 * <li>for the actions that CR and BR perform, a division grant applies to theory lectures only and a batch grant to lab
 * and tutorial lectures only. Department and global grants have no such limit.</li>
 * </ul>
 *
 * <p>A missing id, set or session type in the facts never matches. An action this policy does not know is denied. No
 * permission lets anyone override a conflict, so none appears here: that is a rule of the ticket module.
 */
public final class AcademicAccessPolicy implements ScopedResourcePolicy {

	@Override
	public boolean supports(Object resource) {
		return resource instanceof LectureAccessFacts;
	}

	@Override
	public boolean allows(Caller caller, String action, Object resource, List<GrantedScope> grants) {
		LectureAccessFacts lecture = (LectureAccessFacts) resource;
		UUID me = caller.userId();
		return switch (action) {
			case AcademicPermissions.LIVE_VIEW -> covers(grants, lecture, false);
			case AcademicPermissions.LIVE_VIEW_OWN, AcademicPermissions.TICKETS_CREATE,
					AcademicPermissions.TICKETS_CANCEL -> teaches(me, lecture);
			case AcademicPermissions.TICKETS_CREATE_ON_BEHALF, AcademicPermissions.TICKETS_CANCEL_ON_BEHALF -> covers(
					grants, lecture, false);
			case AcademicPermissions.TICKETS_SELF_APPROVE -> resource instanceof TicketAccessFacts ticket
					&& me.equals(ticket.authorUserId()) && teaches(me, lecture);
			case AcademicPermissions.TICKETS_REVIEW, AcademicPermissions.TICKETS_REJECT -> resource instanceof TicketAccessFacts ticket
					&& contains(ticket.requestedReviewerUserIds(), me) && covers(grants, lecture, true);
			case AcademicPermissions.TICKETS_APPROVE_PERMANENT, AcademicPermissions.TICKETS_REVERT_IN_SCOPE ->
				resource instanceof TicketAccessFacts && covers(grants, lecture, false);
			case AcademicPermissions.TICKETS_REVERT -> resource instanceof TicketAccessFacts ticket
					&& !ticket.permanentChange()
					&& (me.equals(ticket.authorUserId()) || contains(ticket.affectedFacultyUserIds(), me));
			case AcademicPermissions.TICKETS_EDIT_OWN -> resource instanceof TicketAccessFacts ticket
					&& me.equals(ticket.authorUserId());
			case AcademicPermissions.TICKETS_VIEW, AcademicPermissions.TICKET_AUDIT_VIEW ->
				resource instanceof TicketAccessFacts ticket && isOnTicket(me, ticket);
			case AcademicPermissions.TICKETS_VIEW_IN_SCOPE, AcademicPermissions.TICKET_AUDIT_VIEW_IN_SCOPE ->
				resource instanceof TicketAccessFacts && covers(grants, lecture, true);
			case AcademicPermissions.ISSUES_OPEN -> covers(grants, lecture, true);
			case AcademicPermissions.ISSUES_CLOSE -> resource instanceof IssueAccessFacts issue
					&& (me.equals(issue.openerUserId()) || contains(issue.mentionedFacultyUserIds(), me));
			default -> false;
		};
	}

	private static boolean teaches(UUID user, LectureAccessFacts lecture) {
		return contains(lecture.teachingFacultyUserIds(), user);
	}

	private static boolean isOnTicket(UUID user, TicketAccessFacts ticket) {
		return user.equals(ticket.authorUserId()) || contains(ticket.affectedFacultyUserIds(), user)
				|| contains(ticket.requestedReviewerUserIds(), user);
	}

	/**
	 * Whether one of the grants covers the lecture. With {@code sessionRule}, a division grant needs a theory lecture and a
	 * batch grant a lab or tutorial lecture.
	 */
	static boolean covers(List<GrantedScope> grants, LectureAccessFacts lecture, boolean sessionRule) {
		for (GrantedScope grant : grants) {
			if (grant.isGlobal()) {
				return true;
			}
			switch (grant.kind()) {
				case AcademicScopes.DEPARTMENT -> {
					if (grant.id().equals(lecture.departmentId())) {
						return true;
					}
				}
				case AcademicScopes.DIVISION -> {
					if (grant.id().equals(lecture.divisionId())
							&& (!sessionRule || lecture.sessionType() == SessionType.THEORY)) {
						return true;
					}
				}
				case AcademicScopes.BATCH -> {
					if (contains(lecture.batchIds(), grant.id()) && (!sessionRule || isLabOrTutorial(lecture))) {
						return true;
					}
				}
				default -> {
					// A kind this policy does not know covers nothing.
				}
			}
		}
		return false;
	}

	private static boolean isLabOrTutorial(LectureAccessFacts lecture) {
		return lecture.sessionType() == SessionType.LAB || lecture.sessionType() == SessionType.TUTORIAL;
	}

	private static boolean contains(Set<UUID> ids, UUID id) {
		return ids != null && id != null && ids.contains(id);
	}

}
