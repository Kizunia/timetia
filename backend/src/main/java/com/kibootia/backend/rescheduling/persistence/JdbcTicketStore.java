package com.kibootia.backend.rescheduling.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.platform.persistence.JdbcSupport;
import com.kibootia.backend.rescheduling.application.LectureRef;
import com.kibootia.backend.rescheduling.application.NewReview;
import com.kibootia.backend.rescheduling.application.NewTicket;
import com.kibootia.backend.rescheduling.application.Ticket;
import com.kibootia.backend.rescheduling.application.TicketChange;
import com.kibootia.backend.rescheduling.application.TicketQuery;
import com.kibootia.backend.rescheduling.application.TicketQuery.Visibility;
import com.kibootia.backend.rescheduling.application.TicketReview;
import com.kibootia.backend.rescheduling.application.TicketStore;
import com.kibootia.backend.rescheduling.domain.ApprovalPath;
import com.kibootia.backend.rescheduling.domain.ChangeOperation;
import com.kibootia.backend.rescheduling.domain.CheckTrigger;
import com.kibootia.backend.rescheduling.domain.ReviewStatus;
import com.kibootia.backend.rescheduling.domain.ReviewerCapacity;
import com.kibootia.backend.rescheduling.domain.TicketStatus;
import com.kibootia.backend.rescheduling.domain.TicketType;
import com.kibootia.backend.timetable.ConflictType;
import com.kibootia.backend.timetable.LectureConflict;
import com.kibootia.backend.timetable.LectureMode;
import com.kibootia.backend.timetable.LectureSlot;

/**
 * Keeps tickets, their changes, the reviews and the conflict checks made on them in MySQL with hand-written SQL over JDBC.
 * Five tables: {@code timetable_change_ticket}, {@code ticket_change_item}, {@code ticket_review},
 * {@code conflict_check_run} and {@code conflict_finding}.
 *
 * <p>A row that has a {@code version} is changed with {@code WHERE version = ?} and advances it, and a change that leaves
 * the row as it is writes nothing and leaves the version alone, as the JPA entities' dirty checking did. Every query names
 * the tenant, including each subquery.
 */
@Component
class JdbcTicketStore extends JdbcSupport implements TicketStore {

	/** The columns a page of tickets may be sorted on, by the property name the web layer uses. */
	private static final Map<String, String> SORT_COLUMNS = Map.of("createdAt", "created_at", "expiresAt", "expires_at");

	/** The longest message a finding stores; a longer sentence is cut rather than refused. */
	private static final int MESSAGE_LENGTH = 500;

	// ---------------------------------------------------------------------------------------------------------------
	// Tickets

	private static final String TICKET_COLUMNS = """
			t.id, t.tenant_id, t.ticket_type, t.status, t.approval_path, t.author_user_id, t.acting_faculty_id, t.description,
			t.revision_no, t.assignment_id, t.department_id, t.division_id, t.batch_id, t.session_type, t.faculty_id,
			t.expires_at, t.is_conflicted, t.conflicted_since, t.closed_at, t.version, t.created_at, t.updated_at,
			t.reverts_ticket_id, t.reverted_by_ticket_id
			""";

	private static final String INSERT_TICKET = """
			INSERT INTO timetable_change_ticket (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
			    ticket_type, status, author_user_id, acting_faculty_id, description, revision_no, assignment_id,
			    department_id, division_id, batch_id, session_type, faculty_id, expires_at, is_conflicted,
			    reverts_ticket_id)
			VALUES (?, ?, 0, ?, ?, ?, ?,
			    ?, ?, ?, ?, ?, 1, ?,
			    ?, ?, ?, ?, ?, ?, FALSE,
			    ?)
			""";

	private static final String FIND_TICKET = "SELECT " + TICKET_COLUMNS + """
			FROM timetable_change_ticket t
			WHERE t.tenant_id = ? AND t.id = ?
			""";

	// Writes what can change in a ticket. It succeeds only if the row still has the version the caller read.
	private static final String UPDATE_TICKET = """
			UPDATE timetable_change_ticket
			SET status = ?, approval_path = ?, description = ?, revision_no = ?, expires_at = ?, is_conflicted = ?,
			    conflicted_since = ?, closed_at = ?, reverted_by_ticket_id = ?,
			    version = version + 1, updated_at = ?, updated_by = ?
			WHERE tenant_id = ? AND id = ? AND version = ?
			""";

	// The tickets a caller may see, narrowed by their filters. %s is the extra conditions.
	private static final String SEARCH_TICKETS = "SELECT " + TICKET_COLUMNS + """
			FROM timetable_change_ticket t
			WHERE t.tenant_id = ?%s""";

	private static final String COUNT_TICKETS = """
			SELECT COUNT(*) FROM timetable_change_ticket t
			WHERE t.tenant_id = ?%s
			""";

	// Is there a revert of this ticket that is not over? Its states are listed in the parameters.
	private static final String COUNT_OPEN_REVERTS = """
			SELECT COUNT(*) FROM timetable_change_ticket
			WHERE tenant_id = ? AND reverts_ticket_id = ? AND status IN (?, ?)
			""";

	// The tickets under review that are about some lectures or want a slot on some dates. %s is the OR of those two tests.
	private static final String IN_REVIEW_AFFECTED_BY = "SELECT " + TICKET_COLUMNS + """
			FROM timetable_change_ticket t
			WHERE t.tenant_id = ? AND t.status = ? AND t.id IN (
			    SELECT c.ticket_id FROM ticket_change_item c WHERE c.tenant_id = ? AND (%s))
			ORDER BY t.id
			""";

	// Tickets not yet applied whose lecture has started by an instant, the oldest first.
	private static final String OPEN_EXPIRED_BY = "SELECT " + TICKET_COLUMNS + """
			FROM timetable_change_ticket t
			WHERE t.tenant_id = ? AND t.status IN (?, ?) AND t.expires_at <= ?
			ORDER BY t.expires_at ASC, t.id ASC
			LIMIT ?
			""";

	// ---------------------------------------------------------------------------------------------------------------
	// Changes

	private static final String CHANGE_COLUMNS = """
			id, ticket_id, item_no, operation_type, source_occurrence_id, source_revision, reason, target_date,
			target_start_time, target_end_time, target_mode, target_room_id, target_meeting_link, teaching_assignment_id,
			created_occurrence_id, target_faculty_id, faculty_id, before_faculty_id, before_date, before_start_time,
			before_end_time, before_mode, before_room_id, before_meeting_link, version
			""";

	private static final String INSERT_CHANGE = """
			INSERT INTO ticket_change_item (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
			    ticket_id, item_no, operation_type, source_occurrence_id, source_revision, reason, target_date,
			    target_start_time, target_end_time, target_mode, target_room_id, target_meeting_link, target_faculty_id,
			    teaching_assignment_id, faculty_id)
			VALUES (?, ?, 0, ?, ?, ?, ?,
			    ?, ?, ?, ?, ?, ?, ?,
			    ?, ?, ?, ?, ?, ?,
			    ?, ?)
			""";

	private static final String CHANGES_OF_TICKET = "SELECT " + CHANGE_COLUMNS + """
			FROM ticket_change_item
			WHERE tenant_id = ? AND ticket_id = ?
			ORDER BY item_no
			""";

	// The changes of several tickets in one query. %s is the list of ?.
	private static final String CHANGES_OF_TICKETS = "SELECT " + CHANGE_COLUMNS + """
			FROM ticket_change_item
			WHERE tenant_id = ? AND ticket_id IN (%s)
			ORDER BY item_no
			""";

	private static final String FIND_CHANGE = "SELECT " + CHANGE_COLUMNS + """
			FROM ticket_change_item
			WHERE tenant_id = ? AND id = ?
			""";

	// Replaces what a change does, for an edit of a draft.
	private static final String REPLACE_CHANGE = """
			UPDATE ticket_change_item
			SET operation_type = ?, source_occurrence_id = ?, source_revision = ?, reason = ?, target_faculty_id = ?,
			    teaching_assignment_id = ?, faculty_id = ?, target_date = ?, target_start_time = ?, target_end_time = ?,
			    target_mode = ?, target_room_id = ?, target_meeting_link = ?,
			    version = version + 1, updated_at = ?, updated_by = ?
			WHERE tenant_id = ? AND id = ? AND version = ?
			""";

	private static final String RECORD_BEFORE = """
			UPDATE ticket_change_item
			SET before_faculty_id = ?, before_date = ?, before_start_time = ?, before_end_time = ?, before_mode = ?,
			    before_room_id = ?, before_meeting_link = ?,
			    version = version + 1, updated_at = ?, updated_by = ?
			WHERE tenant_id = ? AND id = ? AND version = ?
			""";

	private static final String RECORD_CREATED = """
			UPDATE ticket_change_item
			SET created_occurrence_id = ?, version = version + 1, updated_at = ?, updated_by = ?
			WHERE tenant_id = ? AND id = ? AND version = ?
			""";

	// ---------------------------------------------------------------------------------------------------------------
	// Conflict checks

	private static final String INSERT_RUN = """
			INSERT INTO conflict_check_run (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
			                                ticket_id, check_trigger, result)
			VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?, ?)
			""";

	private static final String INSERT_FINDING = """
			INSERT INTO conflict_finding (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
			                              run_id, ticket_id, conflict_type, message, occurrence_id,
			                              conflicting_occurrence_id, conflicting_ticket_id)
			VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""";

	// The findings of the latest check of a ticket. The subquery finds the latest run; ids are time ordered, so they break
	// a tie between runs made in the same instant.
	private static final String LATEST_CHECK = """
			SELECT conflict_type, message, occurrence_id, conflicting_occurrence_id, conflicting_ticket_id
			FROM conflict_finding
			WHERE tenant_id = ? AND run_id = (
			    SELECT r.id FROM conflict_check_run r
			    WHERE r.tenant_id = ? AND r.ticket_id = ?
			    ORDER BY r.created_at DESC, r.id DESC
			    LIMIT 1)
			ORDER BY id ASC
			""";

	// ---------------------------------------------------------------------------------------------------------------
	// Reviews

	private static final String REVIEW_COLUMNS = """
			id, ticket_id, ticket_revision_no, reviewer_user_id, reviewer_capacity, review_status, decision_reason,
			decided_at, is_escalation, escalated_from_review_id, version
			""";

	private static final String INSERT_REVIEW = """
			INSERT INTO ticket_review (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
			                           ticket_id, ticket_revision_no, reviewer_user_id, reviewer_capacity, review_status,
			                           is_escalation, escalated_from_review_id)
			VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""";

	private static final String REVIEWS_OF_TICKET = "SELECT " + REVIEW_COLUMNS + """
			FROM ticket_review
			WHERE tenant_id = ? AND ticket_id = ?
			ORDER BY ticket_revision_no ASC, id ASC
			""";

	private static final String REVIEWS_OF_REVISION = "SELECT " + REVIEW_COLUMNS + """
			FROM ticket_review
			WHERE tenant_id = ? AND ticket_id = ? AND ticket_revision_no = ?
			""";

	private static final String FIND_REVIEW = "SELECT " + REVIEW_COLUMNS + """
			FROM ticket_review
			WHERE tenant_id = ? AND id = ?
			""";

	private static final String DECIDE_REVIEW = """
			UPDATE ticket_review
			SET review_status = ?, decision_reason = ?, decided_at = ?, version = version + 1, updated_at = ?, updated_by = ?
			WHERE tenant_id = ? AND id = ? AND version = ?
			""";

	// Back to waiting for an answer, for every review of the revision that has been answered.
	private static final String RESET_REVIEWS = """
			UPDATE ticket_review
			SET review_status = ?, decision_reason = NULL, decided_at = NULL,
			    version = version + 1, updated_at = ?, updated_by = ?
			WHERE tenant_id = ? AND ticket_id = ? AND ticket_revision_no = ?
			  AND (review_status <> ? OR decision_reason IS NOT NULL OR decided_at IS NOT NULL)
			""";

	private static final String DELETE_REVIEW = "DELETE FROM ticket_review WHERE tenant_id = ? AND id = ?";

	JdbcTicketStore(DataSource dataSource) {
		super(dataSource);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Tickets

	@Override
	public Ticket insert(NewTicket ticket) {
		UUID id = newId();
		UUID tenant = tenantForWrite();
		Instant now = now();
		LectureRef lecture = ticket.lecture();
		execute(INSERT_TICKET, id, tenant, now, actor(), now, actor(), ticket.type(), TicketStatus.DRAFT,
				ticket.authorUserId(), ticket.actingFacultyId(), ticket.description(), lecture.assignmentId(),
				lecture.departmentId(), lecture.divisionId(), lecture.batchId(), lecture.sessionType(),
				lecture.facultyId(), ticket.expiresAt(), ticket.revertsTicketId());
		List<NewTicket.Change> planned = ticket.changes();
		for (int i = 0; i < planned.size(); i++) {
			insertChange(id, i + 1, planned.get(i));
		}
		return new Ticket(id, tenant, ticket.type(), TicketStatus.DRAFT, null, ticket.authorUserId(),
				ticket.actingFacultyId(), ticket.description(), 1, lecture, ticket.expiresAt(), false, null, null, 0, now,
				now, ticket.revertsTicketId(), null);
	}

	private void insertChange(UUID ticketId, int itemNo, NewTicket.Change change) {
		Instant now = now();
		LectureSlot target = change.target();
		execute(INSERT_CHANGE, newId(), tenantForWrite(), now, actor(), now, actor(), ticketId, itemNo,
				change.operation(), change.sourceOccurrenceId(), change.sourceRevision(), change.reason(),
				(target == null) ? null : target.date(), (target == null) ? null : target.startTime(),
				(target == null) ? null : target.endTime(), (target == null) ? null : target.mode(),
				(target == null) ? null : target.roomId(), (target == null) ? null : target.meetingLink(),
				change.substituteFacultyId(), change.assignmentId(), change.facultyId());
	}

	@Override
	public Optional<Ticket> find(UUID id) {
		return queryOne(FIND_TICKET, JdbcTicketStore::toTicket, tenant(), id);
	}

	@Override
	public Ticket update(Ticket ticket) {
		Ticket current = find(ticket.id()).orElseThrow();
		if (current.version() != ticket.version()) {
			throw new ObjectOptimisticLockingFailureException("Ticket", ticket.id());
		}
		boolean unchanged = current.status() == ticket.status() && current.approvalPath() == ticket.approvalPath()
				&& Objects.equals(current.description(), ticket.description())
				&& current.revisionNo() == ticket.revisionNo() && Objects.equals(current.expiresAt(), ticket.expiresAt())
				&& current.conflicted() == ticket.conflicted()
				&& Objects.equals(current.conflictedSince(), ticket.conflictedSince())
				&& Objects.equals(current.closedAt(), ticket.closedAt())
				&& Objects.equals(current.revertedByTicketId(), ticket.revertedByTicketId());
		if (unchanged) {
			return current;
		}
		Instant now = now();
		int changed = execute(UPDATE_TICKET, ticket.status(), ticket.approvalPath(), ticket.description(),
				ticket.revisionNo(), ticket.expiresAt(), ticket.conflicted(), ticket.conflictedSince(),
				ticket.closedAt(), ticket.revertedByTicketId(), now, actor(), tenant(), ticket.id(), ticket.version());
		requireOneRow(changed, "Ticket", ticket.id());
		return new Ticket(current.id(), current.tenantId(), current.type(), ticket.status(), ticket.approvalPath(),
				current.authorUserId(), current.actingFacultyId(), ticket.description(), ticket.revisionNo(),
				current.lecture(), ticket.expiresAt(), ticket.conflicted(), ticket.conflictedSince(), ticket.closedAt(),
				current.version() + 1, current.createdAt(), now, current.revertsTicketId(),
				ticket.revertedByTicketId());
	}

	@Override
	public Page<Ticket> search(TicketQuery query, Pageable pageable) {
		StringBuilder conditions = new StringBuilder();
		List<Object> parameters = new ArrayList<>();
		parameters.add(tenant());
		if (query.status() != null) {
			conditions.append(" AND t.status = ?");
			parameters.add(query.status());
		}
		if (query.authoredBy() != null) {
			conditions.append(" AND t.author_user_id = ?");
			parameters.add(query.authoredBy());
		}
		if (query.lectureId() != null) {
			// Tickets with a change that is about this lecture, or that made it.
			conditions.append(" AND t.id IN (SELECT c.ticket_id FROM ticket_change_item c WHERE c.tenant_id = ?")
				.append(" AND (c.source_occurrence_id = ? OR c.created_occurrence_id = ?))");
			parameters.add(tenant());
			parameters.add(query.lectureId());
			parameters.add(query.lectureId());
		}
		if (!query.visibility().everywhere()) {
			conditions.append(visibilityCondition(query.visibility(), parameters));
		}
		if (query.awaitingAnswerOf() != null) {
			// Asked to review it in the revision it is in now, and has not answered: the "to review" list.
			conditions.append(" AND EXISTS (SELECT 1 FROM ticket_review w WHERE w.tenant_id = ? AND w.reviewer_user_id = ?")
				.append(" AND w.ticket_id = t.id AND w.ticket_revision_no = t.revision_no AND w.review_status = ?)");
			parameters.add(tenant());
			parameters.add(query.awaitingAnswerOf());
			parameters.add(ReviewStatus.PENDING);
		}
		String where = conditions.toString();
		String select = SEARCH_TICKETS.formatted(where) + orderBy(pageable.getSort(), SORT_COLUMNS);
		return queryPage(select, COUNT_TICKETS.formatted(where), JdbcTicketStore::toTicket, pageable,
				parameters.toArray());
	}

	/**
	 * A ticket is visible when any one of the ways the caller may see it holds (see {@link Visibility}). The parameters are
	 * added in the order their {@code ?} appear. No way at all allows nothing.
	 */
	private static String visibilityCondition(Visibility visibility, List<Object> parameters) {
		List<String> any = new ArrayList<>();
		if (!visibility.departments().isEmpty()) {
			any.add("t.department_id IN (" + placeholders(visibility.departments().size()) + ")");
			parameters.addAll(visibility.departments());
		}
		if (!visibility.theoryOfDivisions().isEmpty()) {
			any.add("(t.division_id IN (" + placeholders(visibility.theoryOfDivisions().size()) + ") AND t.session_type = ?)");
			parameters.addAll(visibility.theoryOfDivisions());
			parameters.add(SessionType.THEORY);
		}
		if (!visibility.labsOfBatches().isEmpty()) {
			any.add("(t.batch_id IN (" + placeholders(visibility.labsOfBatches().size()) + ") AND t.session_type IN (?, ?))");
			parameters.addAll(visibility.labsOfBatches());
			parameters.add(SessionType.LAB);
			parameters.add(SessionType.TUTORIAL);
		}
		if (visibility.writtenBy() != null) {
			any.add("t.author_user_id = ?");
			parameters.add(visibility.writtenBy());
		}
		if (!visibility.teachingFaculty().isEmpty()) {
			String faculty = placeholders(visibility.teachingFaculty().size());
			any.add("t.faculty_id IN (" + faculty + ")");
			parameters.addAll(visibility.teachingFaculty());
			// So is a faculty member whose lecture is changed along with it (an exchange), and one who takes a lecture as a
			// substitute.
			any.add("EXISTS (SELECT 1 FROM ticket_change_item i WHERE i.tenant_id = ? AND i.ticket_id = t.id"
					+ " AND (i.faculty_id IN (" + faculty + ") OR i.target_faculty_id IN (" + faculty + ")))");
			parameters.add(tenant());
			parameters.addAll(visibility.teachingFaculty());
			parameters.addAll(visibility.teachingFaculty());
		}
		if (visibility.reviewing() != null) {
			// Asked to review it, in the revision it is in now: an earlier revision's request no longer counts.
			any.add("EXISTS (SELECT 1 FROM ticket_review r WHERE r.tenant_id = ? AND r.reviewer_user_id = ?"
					+ " AND r.ticket_id = t.id AND r.ticket_revision_no = t.revision_no)");
			parameters.add(tenant());
			parameters.add(visibility.reviewing());
		}
		return any.isEmpty() ? " AND 1 = 0" : " AND (" + String.join(" OR ", any) + ")";
	}

	@Override
	public boolean hasOpenRevert(UUID ticketId) {
		return queryCount(COUNT_OPEN_REVERTS, tenant(), ticketId, TicketStatus.DRAFT, TicketStatus.IN_REVIEW) > 0;
	}

	@Override
	public List<Ticket> inReviewAffectedBy(Set<UUID> lectureIds, Set<LocalDate> dates) {
		List<String> tests = new ArrayList<>();
		List<Object> parameters = new ArrayList<>();
		parameters.add(tenant());
		parameters.add(TicketStatus.IN_REVIEW);
		parameters.add(tenant());
		if (!lectureIds.isEmpty()) {
			tests.add("c.source_occurrence_id IN (" + placeholders(lectureIds.size()) + ")");
			parameters.addAll(lectureIds);
		}
		if (!dates.isEmpty()) {
			tests.add("c.target_date IN (" + placeholders(dates.size()) + ")");
			parameters.addAll(dates);
		}
		if (tests.isEmpty()) {
			return List.of();
		}
		return queryList(IN_REVIEW_AFFECTED_BY.formatted(String.join(" OR ", tests)), JdbcTicketStore::toTicket,
				parameters.toArray());
	}

	@Override
	public List<Ticket> openExpiredBy(Instant instant, int limit) {
		return queryList(OPEN_EXPIRED_BY, JdbcTicketStore::toTicket, tenant(), TicketStatus.DRAFT,
				TicketStatus.IN_REVIEW, instant, limit);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Changes

	@Override
	public List<TicketChange> changesOf(UUID ticketId) {
		return queryList(CHANGES_OF_TICKET, (row) -> toChange(row), tenant(), ticketId);
	}

	@Override
	public Map<UUID, List<TicketChange>> changesOf(Collection<UUID> ticketIds) {
		if (ticketIds.isEmpty()) {
			return Map.of();
		}
		List<Object> parameters = new ArrayList<>();
		parameters.add(tenant());
		parameters.addAll(ticketIds);
		Map<UUID, List<TicketChange>> byTicket = new LinkedHashMap<>();
		for (TicketChange change : queryList(CHANGES_OF_TICKETS.formatted(placeholders(ticketIds.size())),
				JdbcTicketStore::toChange, parameters.toArray())) {
			byTicket.computeIfAbsent(change.ticketId(), (key) -> new ArrayList<>()).add(change);
		}
		return byTicket;
	}

	@Override
	public void replaceChanges(UUID ticketId, List<NewTicket.Change> replacements) {
		List<StoredChange> stored = queryList(CHANGES_OF_TICKET, JdbcTicketStore::toStoredChange, tenant(), ticketId);
		if (stored.size() != replacements.size()) {
			throw new IllegalStateException("A ticket keeps the number of changes it was raised with");
		}
		for (int i = 0; i < stored.size(); i++) {
			TicketChange current = stored.get(i).change();
			NewTicket.Change replacement = replacements.get(i);
			if (sameAs(current, replacement)) {
				continue;
			}
			LectureSlot target = replacement.target();
			int changed = execute(REPLACE_CHANGE, replacement.operation(), replacement.sourceOccurrenceId(),
					replacement.sourceRevision(), replacement.reason(), replacement.substituteFacultyId(),
					replacement.assignmentId(), replacement.facultyId(), (target == null) ? null : target.date(),
					(target == null) ? null : target.startTime(), (target == null) ? null : target.endTime(),
					(target == null) ? null : target.mode(), (target == null) ? null : target.roomId(),
					(target == null) ? null : target.meetingLink(), now(), actor(), tenant(), current.id(),
					stored.get(i).version());
			requireOneRow(changed, "TicketChange", current.id());
		}
	}

	private static boolean sameAs(TicketChange current, NewTicket.Change replacement) {
		return current.operation() == replacement.operation()
				&& Objects.equals(current.sourceOccurrenceId(), replacement.sourceOccurrenceId())
				&& current.sourceRevision() == replacement.sourceRevision()
				&& Objects.equals(current.target(), replacement.target())
				&& Objects.equals(current.reason(), replacement.reason())
				&& Objects.equals(current.substituteFacultyId(), replacement.substituteFacultyId())
				&& Objects.equals(current.assignmentId(), replacement.assignmentId())
				&& Objects.equals(current.facultyId(), replacement.facultyId());
	}

	@Override
	public void recordBefore(UUID changeId, LectureSlot before, UUID beforeFacultyId) {
		StoredChange stored = queryOne(FIND_CHANGE, JdbcTicketStore::toStoredChange, tenant(), changeId).orElseThrow();
		if (Objects.equals(stored.change().before(), before)
				&& Objects.equals(stored.change().beforeFacultyId(), beforeFacultyId)) {
			return;
		}
		int changed = execute(RECORD_BEFORE, beforeFacultyId, before.date(), before.startTime(), before.endTime(),
				before.mode(), before.roomId(), before.meetingLink(), now(), actor(), tenant(), changeId,
				stored.version());
		requireOneRow(changed, "TicketChange", changeId);
	}

	@Override
	public void recordCreated(UUID changeId, UUID occurrenceId) {
		StoredChange stored = queryOne(FIND_CHANGE, JdbcTicketStore::toStoredChange, tenant(), changeId).orElseThrow();
		if (Objects.equals(stored.change().createdOccurrenceId(), occurrenceId)) {
			return;
		}
		int changed = execute(RECORD_CREATED, occurrenceId, now(), actor(), tenant(), changeId, stored.version());
		requireOneRow(changed, "TicketChange", changeId);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Conflict checks

	@Override
	public void recordCheck(UUID ticketId, CheckTrigger trigger, List<LectureConflict> conflicts) {
		UUID runId = newId();
		Instant now = now();
		execute(INSERT_RUN, runId, tenantForWrite(), now, actor(), now, actor(), ticketId, trigger,
				conflicts.isEmpty() ? "CLEAR" : "BLOCKED");
		for (LectureConflict conflict : conflicts) {
			// A message is a sentence for a person; it is cut rather than refused if it is ever longer than the column.
			String message = (conflict.message().length() > MESSAGE_LENGTH)
					? conflict.message().substring(0, MESSAGE_LENGTH) : conflict.message();
			execute(INSERT_FINDING, newId(), tenantForWrite(), now, actor(), now, actor(), runId, ticketId,
					conflict.type(), message, conflict.occurrenceId(), conflict.conflictingOccurrenceId(),
					conflict.conflictingTicketId());
		}
	}

	@Override
	public List<LectureConflict> latestCheck(UUID ticketId) {
		return queryList(LATEST_CHECK,
				(row) -> new LectureConflict(ConflictType.valueOf(row.getString("conflict_type")),
						uuidOrNull(row, "occurrence_id"), row.getString("message"),
						uuidOrNull(row, "conflicting_occurrence_id"), uuidOrNull(row, "conflicting_ticket_id")),
				tenant(), tenant(), ticketId);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Reviews

	@Override
	public List<TicketReview> reviewsOf(UUID ticketId) {
		return queryList(REVIEWS_OF_TICKET, (row) -> toReview(row), tenant(), ticketId);
	}

	@Override
	public Set<UUID> reviewerUserIds(UUID ticketId, int revisionNo) {
		Set<UUID> asked = new HashSet<>();
		queryList(REVIEWS_OF_REVISION, (row) -> toReview(row), tenant(), ticketId, revisionNo)
			.forEach((review) -> asked.add(review.reviewerUserId()));
		return asked;
	}

	@Override
	public List<TicketReview> insertReviews(UUID ticketId, int revisionNo, List<NewReview> asked) {
		List<TicketReview> inserted = new ArrayList<>();
		for (NewReview review : asked) {
			UUID id = newId();
			Instant now = now();
			execute(INSERT_REVIEW, id, tenantForWrite(), now, actor(), now, actor(), ticketId, revisionNo,
					review.reviewerUserId(), review.capacity(), ReviewStatus.PENDING, review.escalation(),
					review.escalatedFromReviewId());
			inserted.add(new TicketReview(id, ticketId, revisionNo, review.reviewerUserId(), review.capacity(),
					ReviewStatus.PENDING, null, null, review.escalation(), review.escalatedFromReviewId()));
		}
		return inserted;
	}

	@Override
	public TicketReview decide(UUID reviewId, ReviewStatus status, String reason, Instant at) {
		StoredReview stored = queryOne(FIND_REVIEW, JdbcTicketStore::toStoredReview, tenant(), reviewId).orElseThrow();
		TicketReview current = stored.review();
		TicketReview decided = new TicketReview(current.id(), current.ticketId(), current.revisionNo(),
				current.reviewerUserId(), current.capacity(), status, reason, at, current.escalation(),
				current.escalatedFromReviewId());
		if (current.status() == status && Objects.equals(current.reason(), reason)
				&& Objects.equals(current.decidedAt(), at)) {
			return current;
		}
		int changed = execute(DECIDE_REVIEW, status, reason, at, now(), actor(), tenant(), reviewId,
				stored.version());
		requireOneRow(changed, "TicketReview", reviewId);
		return decided;
	}

	@Override
	public void resetDecisions(UUID ticketId, int revisionNo) {
		execute(RESET_REVIEWS, ReviewStatus.PENDING, now(), actor(), tenant(), ticketId, revisionNo,
				ReviewStatus.PENDING);
	}

	@Override
	public void syncReviewers(UUID ticketId, int revisionNo, List<NewReview> reviewers) {
		List<TicketReview> existing = queryList(REVIEWS_OF_REVISION, (row) -> toReview(row), tenant(), ticketId,
				revisionNo);
		Set<String> wanted = new HashSet<>();
		reviewers.forEach((review) -> wanted.add(review.reviewerUserId() + ":" + review.capacity()));
		Set<String> kept = new HashSet<>();
		for (TicketReview review : existing) {
			String key = review.reviewerUserId() + ":" + review.capacity();
			if (wanted.contains(key)) {
				kept.add(key);
			}
			else {
				execute(DELETE_REVIEW, tenant(), review.id());
			}
		}
		insertReviews(ticketId, revisionNo, reviewers.stream()
			.filter((review) -> !kept.contains(review.reviewerUserId() + ":" + review.capacity()))
			.toList());
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Rows

	private static Ticket toTicket(ResultSet row) throws SQLException {
		String approvalPath = row.getString("approval_path");
		LectureRef lecture = new LectureRef(uuid(row, "assignment_id"), uuid(row, "department_id"),
				uuid(row, "division_id"), uuidOrNull(row, "batch_id"),
				SessionType.valueOf(row.getString("session_type")), uuid(row, "faculty_id"));
		return new Ticket(uuid(row, "id"), uuid(row, "tenant_id"), TicketType.valueOf(row.getString("ticket_type")),
				TicketStatus.valueOf(row.getString("status")),
				(approvalPath == null) ? null : ApprovalPath.valueOf(approvalPath), uuid(row, "author_user_id"),
				uuidOrNull(row, "acting_faculty_id"), row.getString("description"), row.getInt("revision_no"), lecture,
				instant(row, "expires_at"), row.getBoolean("is_conflicted"), instantOrNull(row, "conflicted_since"),
				instantOrNull(row, "closed_at"), row.getLong("version"), instant(row, "created_at"),
				instant(row, "updated_at"), uuidOrNull(row, "reverts_ticket_id"),
				uuidOrNull(row, "reverted_by_ticket_id"));
	}

	private static TicketChange toChange(ResultSet row) throws SQLException {
		return toStoredChange(row).change();
	}

	private static StoredChange toStoredChange(ResultSet row) throws SQLException {
		LectureSlot target = (row.getObject("target_date", LocalDate.class) == null) ? null
				: new LectureSlot(row.getObject("target_date", LocalDate.class),
						row.getObject("target_start_time", LocalTime.class),
						row.getObject("target_end_time", LocalTime.class),
						LectureMode.valueOf(row.getString("target_mode")), uuidOrNull(row, "target_room_id"),
						row.getString("target_meeting_link"));
		LectureSlot before = (row.getObject("before_date", LocalDate.class) == null) ? null
				: new LectureSlot(row.getObject("before_date", LocalDate.class),
						row.getObject("before_start_time", LocalTime.class),
						row.getObject("before_end_time", LocalTime.class),
						LectureMode.valueOf(row.getString("before_mode")), uuidOrNull(row, "before_room_id"),
						row.getString("before_meeting_link"));
		TicketChange change = new TicketChange(uuid(row, "id"), uuid(row, "ticket_id"),
				ChangeOperation.valueOf(row.getString("operation_type")), uuidOrNull(row, "source_occurrence_id"),
				row.getLong("source_revision"), target, row.getString("reason"), uuidOrNull(row, "target_faculty_id"),
				uuidOrNull(row, "teaching_assignment_id"), uuidOrNull(row, "created_occurrence_id"),
				uuidOrNull(row, "faculty_id"), before, uuidOrNull(row, "before_faculty_id"));
		return new StoredChange(change, row.getLong("version"));
	}

	private static TicketReview toReview(ResultSet row) throws SQLException {
		return toStoredReview(row).review();
	}

	private static StoredReview toStoredReview(ResultSet row) throws SQLException {
		TicketReview review = new TicketReview(uuid(row, "id"), uuid(row, "ticket_id"), row.getInt("ticket_revision_no"),
				uuid(row, "reviewer_user_id"), ReviewerCapacity.valueOf(row.getString("reviewer_capacity")),
				ReviewStatus.valueOf(row.getString("review_status")), row.getString("decision_reason"),
				instantOrNull(row, "decided_at"), row.getBoolean("is_escalation"),
				uuidOrNull(row, "escalated_from_review_id"));
		return new StoredReview(review, row.getLong("version"));
	}

	/** A change with the version of its row, which an update is checked against. */
	private record StoredChange(TicketChange change, long version) {

	}

	/** A review with the version of its row, which an update is checked against. */
	private record StoredReview(TicketReview review, long version) {

	}

}
