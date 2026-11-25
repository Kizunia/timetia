package com.kibootia.backend.issues.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.academic.AcademicPermissions;
import com.kibootia.backend.academic.AcademicScopes;
import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.audit.AuditEntry;
import com.kibootia.backend.audit.AuditLog;
import com.kibootia.backend.issues.IssueMentioned;
import com.kibootia.backend.issues.application.IssueQuery.Visibility;
import com.kibootia.backend.issues.domain.IssueStatus;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.authorization.Reach;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.errors.ApiFieldError;
import com.kibootia.backend.platform.errors.ProblemCodes;
import com.kibootia.backend.platform.events.EventContextFactory;
import com.kibootia.backend.platform.tenancy.TenantContext;
import com.kibootia.backend.reference.AcademicDirectory;
import com.kibootia.backend.reference.DivisionDetails;
import com.kibootia.backend.reference.FacultyDetails;
import com.kibootia.backend.rescheduling.TicketVisibility;

/**
 * The use cases of issues (rules: issues from CR and BR). What the policy decides here:
 * <ul>
 * <li>opening one: a class representative of the division for a theory issue, a batch representative of the batch for a
 * lab or tutorial issue ({@code open});</li>
 * <li>closing it, and linking a ticket to it: the person who opened it, or a faculty member it mentions ({@code close});</li>
 * <li>mentioning more faculty: the person who opened it, as the representative of the place;</li>
 * <li>seeing it: the people on it, and the roles whose place covers it, with the session rule ({@code view},
 * {@code view-in-scope}).</li>
 * </ul>
 * An issue the caller may not see is answered as not found. An issue changes nothing in the timetable.
 */
@Service
public class IssueService {

	public static final String ISSUE_NOT_FOUND = "issues.issue_not_found";

	public static final String PLACE_NOT_FOUND = "issues.place_not_found";

	public static final String NOT_OPEN = "issues.issue_not_open";

	public static final String TICKET_NOT_FOUND = "issues.ticket_not_found";

	private final IssueStore issues;

	private final AcademicDirectory directory;

	private final Authorization authorization;

	private final AuditLog auditLog;

	private final Clock clock;

	private final TicketVisibility tickets;

	private final ApplicationEventPublisher events;

	private final EventContextFactory eventContexts;

	IssueService(IssueStore issues, AcademicDirectory directory, Authorization authorization, AuditLog auditLog, Clock clock,
			TicketVisibility tickets, ApplicationEventPublisher events, EventContextFactory eventContexts) {
		this.issues = issues;
		this.directory = directory;
		this.authorization = authorization;
		this.auditLog = auditLog;
		this.clock = clock;
		this.tickets = tickets;
		this.events = events;
		this.eventContexts = eventContexts;
	}

	/**
	 * What a person asks for when opening an issue.
	 *
	 * @param divisionId the division of a theory issue; not allowed for a lab or tutorial
	 * @param batchId the batch of a lab or tutorial issue; not allowed for theory
	 */
	public record Draft(SessionType sessionType, UUID divisionId, UUID batchId, String title, String description,
			Set<UUID> mentionedFacultyIds) {

	}

	/** Which issues a list asks for. {@code mine} narrows it to the issues the caller opened. */
	public record IssueFilter(IssueStatus status, boolean mine) {

	}

	// --- opening ----------------------------------------------------------------------------------------------------

	@Transactional
	public IssueDetail open(Draft draft) {
		UUID me = this.authorization.currentUserId();
		List<ApiFieldError> errors = new ArrayList<>();
		boolean theory = draft.sessionType() == SessionType.THEORY;
		if (theory && draft.divisionId() == null) {
			errors.add(new ApiFieldError("divisionId", "required", "A theory issue is opened for a division."));
		}
		if (theory && draft.batchId() != null) {
			errors.add(new ApiFieldError("batchId", "not_allowed", "A theory issue is for the whole division, not a batch."));
		}
		if (!theory && draft.batchId() == null) {
			errors.add(new ApiFieldError("batchId", "required", "A lab or tutorial issue is opened for a batch."));
		}
		if (!theory && draft.divisionId() != null) {
			errors.add(new ApiFieldError("divisionId", "not_allowed", "A lab or tutorial issue is for a batch, not a division."));
		}
		Set<UUID> mentioned = (draft.mentionedFacultyIds() == null) ? Set.of() : draft.mentionedFacultyIds();
		Map<UUID, FacultyDetails> faculty = this.directory.faculties(mentioned);
		if (!faculty.keySet().containsAll(mentioned)) {
			errors.add(new ApiFieldError("mentionedFacultyIds", "unknown_faculty", "A faculty member mentioned does not exist."));
		}
		requireValid(errors);

		UUID divisionId = theory ? draft.divisionId() : this.directory.divisionsOfBatches(Set.of(draft.batchId())).get(draft.batchId());
		DivisionDetails division = (divisionId == null) ? null : this.directory.division(divisionId).orElse(null);
		if (division == null) {
			throw ApiException.notFound(PLACE_NOT_FOUND, "There is no such " + (theory ? "division" : "batch") + ".");
		}
		Set<UUID> batchIds = theory ? Set.of() : Set.of(draft.batchId());
		IssueFacts facts = new IssueFacts(TenantContext.require(), division.departmentId(), division.id(), batchIds,
				draft.sessionType(), me, usersOf(faculty.values()));
		this.authorization.require(AcademicPermissions.ISSUES_OPEN, facts);

		Issue issue = this.issues.insert(new NewIssue(draft.sessionType(), division.departmentId(), division.id(),
				draft.batchId(), draft.title().strip(), clean(draft.description()), me, mentioned));
		this.auditLog.record(AuditEntry.of("issues.issue.opened", "issue", issue.id())
			.withChange("sessionType", draft.sessionType().name())
			.withChange("mentions", String.valueOf(mentioned.size())));
		told(issue, me, faculty.values());
		return detail(issue);
	}

	// --- reading ----------------------------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public IssueDetail get(UUID id) {
		Issue issue = this.issues.find(id).orElseThrow(IssueService::issueNotFound);
		IssueFacts facts = facts(issue);
		if (!this.authorization.can(AcademicPermissions.ISSUES_VIEW, facts)
				&& !this.authorization.can(AcademicPermissions.ISSUES_VIEW_IN_SCOPE, facts)) {
			throw issueNotFound();
		}
		return detail(issue);
	}

	/**
	 * The issues the caller may see: those they opened or are mentioned on, and those whose place their role covers (a CR
	 * the theory issues of the division, a BR the lab and tutorial issues of the batch, an HOD the department's). Filtered in
	 * the query, from {@link Authorization#reach}, before paging.
	 */
	@Transactional(readOnly = true)
	public Page<IssueDetail> list(IssueFilter filter, Pageable pageable) {
		UUID me = this.authorization.currentUserId();
		Reach inScope = this.authorization.reach(AcademicPermissions.ISSUES_VIEW_IN_SCOPE);
		boolean onOwn = !this.authorization.reach(AcademicPermissions.ISSUES_VIEW).isEmpty();
		Visibility visibility = new Visibility(inScope.everywhere(), inScope.idsOf(AcademicScopes.DEPARTMENT),
				inScope.idsOf(AcademicScopes.DIVISION), inScope.idsOf(AcademicScopes.BATCH), onOwn ? me : null,
				onOwn ? this.directory.facultyIdsOf(me) : Set.of());
		if (visibility.isEmpty()) {
			return Page.empty(pageable);
		}
		Page<Issue> page = this.issues.search(new IssueQuery(filter.status(), filter.mine() ? me : null, visibility), pageable);
		List<UUID> ids = page.getContent().stream().map(Issue::id).collect(Collectors.toList());
		Map<UUID, Set<UUID>> mentions = this.issues.mentionedFacultyIdsOf(ids);
		Map<UUID, Set<UUID>> links = this.issues.linkedTicketIdsOf(ids);
		return page.map((issue) -> new IssueDetail(issue, mentions.getOrDefault(issue.id(), Set.of()),
				links.getOrDefault(issue.id(), Set.of())));
	}

	// --- changing ---------------------------------------------------------------------------------------------------

	/** Closes the issue: by the person who opened it, or a faculty member it mentions. */
	@Transactional
	public IssueDetail close(UUID id) {
		Issue issue = this.issues.find(id).orElseThrow(IssueService::issueNotFound);
		IssueFacts facts = facts(issue);
		requireSeen(facts);
		this.authorization.require(AcademicPermissions.ISSUES_CLOSE, facts);
		requireOpen(issue);
		Issue closed = this.issues.update(issue.closed(this.authorization.currentUserId(), this.clock.instant()));
		this.auditLog.record(AuditEntry.of("issues.issue.closed", "issue", id));
		return detail(closed);
	}

	/** Mentions another faculty member. Only the person who opened the issue may, and a faculty member already mentioned is left as is. */
	@Transactional
	public IssueDetail mention(UUID id, UUID facultyId) {
		UUID me = this.authorization.currentUserId();
		Issue issue = this.issues.find(id).orElseThrow(IssueService::issueNotFound);
		IssueFacts facts = facts(issue);
		requireSeen(facts);
		this.authorization.require(AcademicPermissions.ISSUES_OPEN, facts);
		if (!me.equals(issue.openerUserId())) {
			throw new org.springframework.security.access.AccessDeniedException("Only the person who opened the issue can mention faculty");
		}
		requireOpen(issue);
		Map<UUID, FacultyDetails> faculty = this.directory.faculties(Set.of(facultyId));
		if (!faculty.containsKey(facultyId)) {
			throw invalid(List.of(new ApiFieldError("facultyId", "unknown_faculty", "There is no faculty member with that ID.")));
		}
		if (this.issues.addMention(id, facultyId)) {
			this.auditLog.record(AuditEntry.of("issues.issue.mentioned", "issue", id));
			told(issue, me, faculty.values());
		}
		return detail(issue);
	}

	/** Links the ticket that answers the issue. By the person who opened it or a faculty member it mentions, and only a ticket they can see. */
	@Transactional
	public IssueDetail link(UUID id, UUID ticketId) {
		Issue issue = this.issues.find(id).orElseThrow(IssueService::issueNotFound);
		IssueFacts facts = facts(issue);
		requireSeen(facts);
		this.authorization.require(AcademicPermissions.ISSUES_CLOSE, facts);
		requireOpen(issue);
		if (!this.tickets.visibleToCaller(ticketId)) {
			throw ApiException.notFound(TICKET_NOT_FOUND, "There is no ticket with that ID.");
		}
		if (this.issues.addLink(id, ticketId, this.authorization.currentUserId())) {
			this.auditLog.record(AuditEntry.of("issues.issue.linked", "issue", id).withChange("ticket", ticketId.toString()));
		}
		return detail(issue);
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	private IssueFacts facts(Issue issue) {
		Map<UUID, FacultyDetails> faculty = this.directory.faculties(this.issues.mentionedFacultyIds(issue.id()));
		Set<UUID> batches = (issue.batchId() == null) ? Set.of() : Set.of(issue.batchId());
		return new IssueFacts(issue.tenantId(), issue.departmentId(), issue.divisionId(), batches, issue.sessionType(),
				issue.openerUserId(), usersOf(faculty.values()));
	}

	/** An issue the caller may not see is not found, whatever they ask to do with it. */
	private void requireSeen(IssueFacts facts) {
		if (!this.authorization.can(AcademicPermissions.ISSUES_VIEW, facts)
				&& !this.authorization.can(AcademicPermissions.ISSUES_VIEW_IN_SCOPE, facts)) {
			throw issueNotFound();
		}
	}

	private static void requireOpen(Issue issue) {
		if (issue.status() != IssueStatus.OPEN) {
			throw ApiException.conflict(NOT_OPEN, "The issue is closed.");
		}
	}

	private IssueDetail detail(Issue issue) {
		return new IssueDetail(issue, this.issues.mentionedFacultyIds(issue.id()), this.issues.linkedTicketIds(issue.id()));
	}

	/** Tells the notifications module, once this commits, who was mentioned. */
	private void told(Issue issue, UUID opener, java.util.Collection<FacultyDetails> mentioned) {
		Set<UUID> users = usersOf(mentioned);
		if (!users.isEmpty()) {
			this.events.publishEvent(new IssueMentioned(issue.id(), opener, users, this.eventContexts.capture()));
		}
	}

	private static Set<UUID> usersOf(java.util.Collection<FacultyDetails> faculty) {
		Set<UUID> users = new HashSet<>();
		faculty.forEach((member) -> {
			if (member.userId() != null) {
				users.add(member.userId());
			}
		});
		return Set.copyOf(users);
	}

	private static String clean(String text) {
		return (text == null || text.isBlank()) ? null : text.strip();
	}

	private static void requireValid(List<ApiFieldError> errors) {
		if (!errors.isEmpty()) {
			throw invalid(errors);
		}
	}

	private static ApiException invalid(List<ApiFieldError> errors) {
		return ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.", errors);
	}

	private static ApiException issueNotFound() {
		return ApiException.notFound(ISSUE_NOT_FOUND, "There is no issue with that ID.");
	}

}
