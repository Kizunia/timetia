package com.kibootia.backend.academic.domain;

import java.util.List;
import java.util.Set;

import com.kibootia.backend.academic.AcademicPermissions;
import com.kibootia.backend.academic.AcademicScopes;
import com.kibootia.backend.platform.authorization.GrantedScope;

/**
 * The role templates of the role and scope model (Timetia ADR 0004): a name, the kind of place it is assigned in and the
 * permissions it carries. Super Admin is not here: it is the built-in Administrator role.
 */
public final class AcademicRoles {

	/** One role to seed. */
	public record Template(String name, String description, String scopeType, Set<String> permissions) {

	}

	/** Everything an HOD can do within a scope, for the HOD role and the Super HOD role. */
	private static final Set<String> HEAD_OF_DEPARTMENT = Set.of(AcademicPermissions.LIVE_VIEW,
			AcademicPermissions.TICKETS_CREATE_ON_BEHALF, AcademicPermissions.TICKETS_CANCEL_ON_BEHALF,
			AcademicPermissions.TICKETS_REVIEW, AcademicPermissions.TICKETS_REJECT,
			AcademicPermissions.TICKETS_APPROVE_PERMANENT, AcademicPermissions.TICKETS_REVERT_IN_SCOPE,
			AcademicPermissions.TICKETS_EDIT_OWN, AcademicPermissions.TICKETS_VIEW_IN_SCOPE,
			AcademicPermissions.TICKET_AUDIT_VIEW_IN_SCOPE);

	/** What a CR does for a division's theory lectures and a BR for a batch's lab and tutorial lectures. */
	private static final Set<String> REPRESENTATIVE = Set.of(AcademicPermissions.LIVE_VIEW,
			AcademicPermissions.TICKETS_REVIEW, AcademicPermissions.TICKETS_REJECT,
			AcademicPermissions.TICKETS_VIEW_IN_SCOPE, AcademicPermissions.TICKET_AUDIT_VIEW_IN_SCOPE,
			AcademicPermissions.ISSUES_OPEN, AcademicPermissions.ISSUES_CLOSE);

	public static final List<Template> TEMPLATES = List.of(
			new Template("Super HOD", "Everything an HOD can do, across every department.", GrantedScope.GLOBAL,
					HEAD_OF_DEPARTMENT),
			new Template("HOD", "Head of one department: approves, rejects and reverts within it.",
					AcademicScopes.DEPARTMENT, HEAD_OF_DEPARTMENT),
			new Template("Faculty", "Raises and reverts tickets for the lectures they teach.", GrantedScope.GLOBAL,
					Set.of(AcademicPermissions.LIVE_VIEW_OWN, AcademicPermissions.TICKETS_CREATE,
							AcademicPermissions.TICKETS_CANCEL, AcademicPermissions.TICKETS_SELF_APPROVE,
							AcademicPermissions.TICKETS_REVIEW, AcademicPermissions.TICKETS_REJECT,
							AcademicPermissions.TICKETS_REVERT, AcademicPermissions.TICKETS_EDIT_OWN,
							AcademicPermissions.TICKETS_VIEW, AcademicPermissions.TICKET_AUDIT_VIEW,
							AcademicPermissions.ISSUES_CLOSE)),
			new Template("CR", "Class representative of one division, for its theory lectures.",
					AcademicScopes.DIVISION, REPRESENTATIVE),
			new Template("BR", "Batch representative of one batch, for its lab and tutorial lectures.",
					AcademicScopes.BATCH, REPRESENTATIVE),
			new Template("Student", "Reads the live timetable of their batch.", AcademicScopes.BATCH,
					Set.of(AcademicPermissions.LIVE_VIEW)));

	private AcademicRoles() {
	}

}
