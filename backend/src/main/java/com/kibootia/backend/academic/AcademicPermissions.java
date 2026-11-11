package com.kibootia.backend.academic;

import java.util.List;

import org.springframework.stereotype.Component;

import com.kibootia.backend.platform.authorization.Permission;
import com.kibootia.backend.platform.authorization.PermissionProvider;

/**
 * The permissions of Timetia's lecture rescheduling and live timetable, as the role and scope model document lists
 * them (Timetia ADR 0004). All but three are policy-decided: they are held through a role assigned within a scope, and
 * decided per record by {@link com.kibootia.backend.academic.domain.AcademicAccessPolicy}. The three flat ones are for
 * Super Admin, the built-in Administrator, which holds every flat permission and no policy-decided one.
 *
 * <p>The names are stored in role definitions, so when the ticket and timetable modules exist their owners can take over
 * these declarations without renaming anything.
 */
@Component
public class AcademicPermissions implements PermissionProvider {

	// --- the live timetable -----------------------------------------------------------------------------------------

	/** See a lecture within the scope of the role. Student, CR, BR, HOD, Super HOD. */
	public static final String LIVE_VIEW = "timetable.live.view";

	/** See the lectures one teaches, across divisions. Faculty. */
	public static final String LIVE_VIEW_OWN = "timetable.live.view-own";

	/** See every lecture. Super Admin. */
	public static final String LIVE_VIEW_ALL = "timetable.live.view-all";

	/** Rooms, slot grid, calendar, holidays, exam weeks and notification settings. Super Admin. */
	public static final String CONFIG_MANAGE = "timetable.config.manage";

	// --- tickets ----------------------------------------------------------------------------------------------------

	public static final String TICKETS_CREATE = "rescheduling.tickets.create";

	public static final String TICKETS_CREATE_ON_BEHALF = "rescheduling.tickets.create-on-behalf";

	public static final String TICKETS_CANCEL = "rescheduling.tickets.cancel";

	public static final String TICKETS_CANCEL_ON_BEHALF = "rescheduling.tickets.cancel-on-behalf";

	public static final String TICKETS_SELF_APPROVE = "rescheduling.tickets.self-approve";

	public static final String TICKETS_REVIEW = "rescheduling.tickets.review";

	public static final String TICKETS_REJECT = "rescheduling.tickets.reject";

	public static final String TICKETS_APPROVE_PERMANENT = "rescheduling.tickets.approve-permanent";

	public static final String TICKETS_REVERT = "rescheduling.tickets.revert";

	public static final String TICKETS_REVERT_IN_SCOPE = "rescheduling.tickets.revert-in-scope";

	public static final String TICKETS_EDIT_OWN = "rescheduling.tickets.edit-own";

	public static final String TICKETS_VIEW = "rescheduling.tickets.view";

	public static final String TICKETS_VIEW_IN_SCOPE = "rescheduling.tickets.view-in-scope";

	public static final String TICKET_AUDIT_VIEW = "rescheduling.ticket-audit.view";

	public static final String TICKET_AUDIT_VIEW_IN_SCOPE = "rescheduling.ticket-audit.view-in-scope";

	/** Read any ticket's audit log. Super Admin. */
	public static final String AUDIT_VIEW_ALL = "rescheduling.audit.view-all";

	// --- issues -----------------------------------------------------------------------------------------------------

	public static final String ISSUES_OPEN = "rescheduling.issues.open";

	public static final String ISSUES_CLOSE = "rescheduling.issues.close";

	@Override
	public List<Permission> permissions() {
		return List.of(
				Permission.policyDecided(LIVE_VIEW, "See a lecture within the scope of the role"),
				Permission.policyDecided(LIVE_VIEW_OWN, "See the lectures you teach"),
				Permission.restricted(LIVE_VIEW_ALL, "See every lecture"),
				Permission.restricted(CONFIG_MANAGE,
						"Configure rooms, the slot grid, the calendar, holidays, exam weeks and notification settings"),
				Permission.policyDecided(TICKETS_CREATE, "Raise a ticket for a lecture you teach"),
				Permission.policyDecided(TICKETS_CREATE_ON_BEHALF, "Raise a ticket for a lecture within your scope"),
				Permission.policyDecided(TICKETS_CANCEL, "Cancel a lecture you teach"),
				Permission.policyDecided(TICKETS_CANCEL_ON_BEHALF, "Cancel a lecture within your scope"),
				Permission.policyDecided(TICKETS_SELF_APPROVE, "Apply your own change without a review"),
				Permission.policyDecided(TICKETS_REVIEW, "Review a ticket you were asked to review"),
				Permission.policyDecided(TICKETS_REJECT, "Reject a ticket you were asked to review"),
				Permission.policyDecided(TICKETS_APPROVE_PERMANENT, "Approve a permanent change within your scope"),
				Permission.policyDecided(TICKETS_REVERT, "Revert your own change or one that affects you"),
				Permission.policyDecided(TICKETS_REVERT_IN_SCOPE, "Revert a change within your scope"),
				Permission.policyDecided(TICKETS_EDIT_OWN, "Edit, resubmit, escalate or withdraw your own ticket"),
				Permission.policyDecided(TICKETS_VIEW, "See a ticket you are on"),
				Permission.policyDecided(TICKETS_VIEW_IN_SCOPE, "See a ticket within your scope"),
				Permission.policyDecided(TICKET_AUDIT_VIEW, "Read the audit log of a ticket you are on"),
				Permission.policyDecided(TICKET_AUDIT_VIEW_IN_SCOPE, "Read the audit log of a ticket within your scope"),
				Permission.restricted(AUDIT_VIEW_ALL, "Read the audit log of any ticket"),
				Permission.policyDecided(ISSUES_OPEN, "Open an issue about a lecture within your scope"),
				Permission.policyDecided(ISSUES_CLOSE, "Close an issue you opened or are mentioned on"));
	}

}
