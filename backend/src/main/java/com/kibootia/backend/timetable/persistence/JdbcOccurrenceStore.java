package com.kibootia.backend.timetable.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.platform.persistence.JdbcSupport;
import com.kibootia.backend.timetable.LectureMode;
import com.kibootia.backend.timetable.LectureSlot;
import com.kibootia.backend.timetable.application.NewOccurrence;
import com.kibootia.backend.timetable.application.Occurrence;
import com.kibootia.backend.timetable.application.OccurrenceQuery;
import com.kibootia.backend.timetable.application.OccurrenceQuery.Visibility;
import com.kibootia.backend.timetable.application.OccurrenceStore;

/**
 * Keeps the dated lectures in MySQL with hand-written SQL over JDBC. A lecture row holds two slots: the base columns are the
 * slot the base timetable gave it and are never changed; the {@code effective_*} columns start equal to them and are what a
 * change lays over the lecture. The row's {@code version} is the revision a change is checked against.
 */
@Component
class JdbcOccurrenceStore extends JdbcSupport implements OccurrenceStore {

	/** The columns a page of lectures may be sorted on, by the property name the web layer uses. */
	private static final Map<String, String> SORT_COLUMNS = Map.of("effectiveDate", "effective_date",
			"effectiveStartTime", "effective_start_time");

	private static final String COLUMNS = """
			id, tenant_id, timetable_entry_id, timetable_version_id, assignment_id, department_id, division_id, batch_id,
			session_type, faculty_id, effective_faculty_id, occurrence_date, start_time, end_time, room_id, mode, meeting_link,
			effective_date, effective_start_time, effective_end_time, effective_room_id, effective_mode,
			effective_meeting_link, is_cancelled, changed_by_ticket_id, version
			""";

	// A new lecture is as the base timetable has it: the effective columns equal the base ones, and nothing is changed yet.
	private static final String INSERT = """
			INSERT INTO lecture_occurrence (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
			    timetable_entry_id, timetable_version_id, assignment_id, department_id, division_id, batch_id, session_type,
			    faculty_id, effective_faculty_id, occurrence_date, start_time, end_time, room_id, mode, meeting_link,
			    effective_date, effective_start_time, effective_end_time, effective_room_id, effective_mode,
			    effective_meeting_link, is_cancelled, changed_by_ticket_id, record_status)
			VALUES (?, ?, 0, ?, ?, ?, ?,
			    ?, ?, ?, ?, ?, ?, ?,
			    ?, ?, ?, ?, ?, ?, ?, ?,
			    ?, ?, ?, ?, ?,
			    ?, FALSE, ?, ?)
			""";

	// The ids of the division's active lectures whose base date is in the range: what a new version that starts there replaces.
	private static final String ACTIVE_IDS_BETWEEN = """
			SELECT id FROM lecture_occurrence
			WHERE tenant_id = ? AND division_id = ? AND record_status = ? AND occurrence_date >= ? AND occurrence_date < ?
			""";

	// Marks lectures superseded. The time is set but the version is left alone, as the bulk update it replaces did.
	private static final String SUPERSEDE = """
			UPDATE lecture_occurrence SET record_status = ?, updated_at = ?
			WHERE tenant_id = ? AND id IN (%s)
			""";

	// How many of those lectures have a change laid over them?
	private static final String COUNT_CHANGED = """
			SELECT COUNT(*) FROM lecture_occurrence
			WHERE tenant_id = ? AND division_id = ? AND record_status = ? AND occurrence_date >= ? AND occurrence_date < ?
			  AND changed_by_ticket_id IS NOT NULL
			""";

	// One lecture by id, of any status: the operations that change a lecture found it this way.
	private static final String FIND = "SELECT " + COLUMNS + """
			FROM lecture_occurrence
			WHERE tenant_id = ? AND id = ?
			""";

	// One lecture by id, only if it is still active.
	private static final String FIND_ACTIVE = "SELECT " + COLUMNS + """
			FROM lecture_occurrence
			WHERE tenant_id = ? AND id = ? AND record_status = ?
			""";

	// The active lectures a timetable entry produced, from a base date on, in date order.
	private static final String OF_ENTRY_FROM = "SELECT " + COLUMNS + """
			FROM lecture_occurrence
			WHERE tenant_id = ? AND timetable_entry_id = ? AND record_status = ? AND occurrence_date >= ?
			ORDER BY occurrence_date, id
			""";

	// The active lectures among some ids. %s is the list of ?.
	private static final String FIND_ALL = "SELECT " + COLUMNS + """
			FROM lecture_occurrence
			WHERE tenant_id = ? AND record_status = ? AND id IN (%s)
			""";

	// The same, locked until the end of the transaction, in id order so that changes queue instead of deadlocking.
	private static final String LOCK_ALL = "SELECT " + COLUMNS + """
			FROM lecture_occurrence
			WHERE tenant_id = ? AND record_status = ? AND id IN (%s)
			ORDER BY id
			FOR UPDATE
			""";

	// Lays a state over a lecture. It succeeds only if the row still has the version the change was decided on.
	private static final String CHANGE = """
			UPDATE lecture_occurrence
			SET effective_date = ?, effective_start_time = ?, effective_end_time = ?, effective_room_id = ?,
			    effective_mode = ?, effective_meeting_link = ?, effective_faculty_id = ?, is_cancelled = ?,
			    changed_by_ticket_id = ?, version = version + 1, updated_at = ?, updated_by = ?
			WHERE tenant_id = ? AND id = ? AND version = ?
			""";

	// The active lectures a list asks for. %s is the extra conditions of the query.
	private static final String SEARCH = "SELECT " + COLUMNS + """
			FROM lecture_occurrence
			WHERE tenant_id = ? AND record_status = ? AND effective_date BETWEEN ? AND ?%s""";

	private static final String COUNT_SEARCH = """
			SELECT COUNT(*) FROM lecture_occurrence
			WHERE tenant_id = ? AND record_status = ? AND effective_date BETWEEN ? AND ?%s
			""";

	private final Clock clock;

	JdbcOccurrenceStore(DataSource dataSource, Clock clock) {
		super(dataSource);
		this.clock = clock;
	}

	@Override
	public List<Occurrence> insertAll(List<NewOccurrence> newOccurrences) {
		List<Occurrence> inserted = new ArrayList<>();
		for (NewOccurrence occurrence : newOccurrences) {
			inserted.add(insert(occurrence, null));
		}
		return inserted;
	}

	@Override
	public Occurrence insertExtra(NewOccurrence newOccurrence, UUID ticketId) {
		return insert(newOccurrence, ticketId);
	}

	private Occurrence insert(NewOccurrence occurrence, UUID changedByTicketId) {
		UUID id = newId();
		UUID tenant = tenantForWrite();
		Instant now = now();
		LectureSlot slot = occurrence.slot();
		execute(INSERT, id, tenant, now, actor(), now, actor(), occurrence.entryId(), occurrence.timetableVersionId(),
				occurrence.assignmentId(), occurrence.departmentId(), occurrence.divisionId(), occurrence.batchId(),
				occurrence.sessionType(), occurrence.facultyId(), occurrence.facultyId(), slot.date(), slot.startTime(),
				slot.endTime(), slot.roomId(), slot.mode(), slot.meetingLink(), slot.date(), slot.startTime(),
				slot.endTime(), slot.roomId(), slot.mode(), slot.meetingLink(), changedByTicketId,
				OccurrenceStatus.ACTIVE);
		return new Occurrence(id, tenant, occurrence.entryId(), occurrence.timetableVersionId(),
				occurrence.assignmentId(), occurrence.departmentId(), occurrence.divisionId(), occurrence.batchId(),
				occurrence.sessionType(), occurrence.facultyId(), occurrence.facultyId(), slot, slot, false,
				changedByTicketId, 0);
	}

	@Override
	public List<UUID> supersede(UUID divisionId, LocalDate from, LocalDate toExclusive) {
		List<UUID> ids = queryList(ACTIVE_IDS_BETWEEN, (row) -> uuid(row, "id"), tenant(), divisionId,
				OccurrenceStatus.ACTIVE, from, toExclusive);
		if (!ids.isEmpty()) {
			List<Object> parameters = new ArrayList<>();
			parameters.add(OccurrenceStatus.SUPERSEDED);
			parameters.add(this.clock.instant());
			parameters.add(tenant());
			parameters.addAll(ids);
			execute(SUPERSEDE.formatted(placeholders(ids.size())), parameters.toArray());
		}
		return ids;
	}

	@Override
	public long countChanged(UUID divisionId, LocalDate from, LocalDate toExclusive) {
		return queryCount(COUNT_CHANGED, tenant(), divisionId, OccurrenceStatus.ACTIVE, from, toExclusive);
	}

	@Override
	public Optional<Occurrence> find(UUID id) {
		return queryOne(FIND_ACTIVE, JdbcOccurrenceStore::toOccurrence, tenant(), id, OccurrenceStatus.ACTIVE);
	}

	@Override
	public List<Occurrence> findAll(Collection<UUID> ids) {
		return ids.isEmpty() ? List.of() : queryActiveAmong(FIND_ALL, ids);
	}

	@Override
	public List<Occurrence> findOfEntryFrom(UUID entryId, LocalDate baseDate) {
		return queryList(OF_ENTRY_FROM, JdbcOccurrenceStore::toOccurrence, tenant(), entryId, OccurrenceStatus.ACTIVE,
				baseDate);
	}

	@Override
	public List<Occurrence> lockAll(Collection<UUID> ids) {
		return ids.isEmpty() ? List.of() : queryActiveAmong(LOCK_ALL, ids);
	}

	private List<Occurrence> queryActiveAmong(String sqlWithIdList, Collection<UUID> ids) {
		List<Object> parameters = new ArrayList<>();
		parameters.add(tenant());
		parameters.add(OccurrenceStatus.ACTIVE);
		parameters.addAll(ids);
		return queryList(sqlWithIdList.formatted(placeholders(ids.size())), JdbcOccurrenceStore::toOccurrence,
				parameters.toArray());
	}

	@Override
	public Occurrence move(UUID id, LectureSlot slot, UUID ticketId) {
		Occurrence current = require(id);
		return change(current, slot, current.currentFacultyId(), current.cancelled(), ticketId);
	}

	@Override
	public Occurrence cancel(UUID id, UUID ticketId) {
		Occurrence current = require(id);
		return change(current, current.slot(), current.currentFacultyId(), true, ticketId);
	}

	@Override
	public Occurrence restore(UUID id, LectureSlot slot, UUID facultyId, UUID ticketId) {
		Occurrence current = require(id);
		// If that is how the base timetable has it, no change is laid over the lecture.
		boolean asTheBaseHasIt = slot.equals(current.baseSlot()) && facultyId.equals(current.facultyId());
		return change(current, slot, facultyId, false, asTheBaseHasIt ? null : ticketId);
	}

	@Override
	public Occurrence reassign(UUID id, UUID facultyId, UUID ticketId) {
		Occurrence current = require(id);
		return change(current, current.slot(), facultyId, current.cancelled(), ticketId);
	}

	private Occurrence require(UUID id) {
		return queryOne(FIND, JdbcOccurrenceStore::toOccurrence, tenant(), id).orElseThrow();
	}

	/**
	 * Lays the state over the lecture and returns the lecture as it is then. A change that leaves the lecture as it is writes
	 * nothing, so its revision stays: this is what JPA's dirty checking did.
	 */
	private Occurrence change(Occurrence current, LectureSlot slot, UUID facultyId, boolean cancelled,
			UUID changedByTicketId) {
		boolean unchanged = slot.equals(current.slot()) && facultyId.equals(current.currentFacultyId())
				&& cancelled == current.cancelled() && Objects.equals(changedByTicketId, current.changedByTicketId());
		if (unchanged) {
			return current;
		}
		int changed = execute(CHANGE, slot.date(), slot.startTime(), slot.endTime(), slot.roomId(), slot.mode(),
				slot.meetingLink(), facultyId, cancelled, changedByTicketId, now(), actor(), tenant(), current.id(),
				current.revision());
		requireOneRow(changed, "Occurrence", current.id());
		return new Occurrence(current.id(), current.tenantId(), current.entryId(), current.timetableVersionId(),
				current.assignmentId(), current.departmentId(), current.divisionId(), current.batchId(),
				current.sessionType(), current.facultyId(), facultyId, current.baseSlot(), slot, cancelled,
				changedByTicketId, current.revision() + 1);
	}

	@Override
	public Page<Occurrence> search(OccurrenceQuery query, Pageable pageable) {
		StringBuilder conditions = new StringBuilder();
		List<Object> parameters = new ArrayList<>();
		parameters.add(tenant());
		parameters.add(OccurrenceStatus.ACTIVE);
		parameters.add(query.from());
		parameters.add(query.to());
		if (query.division() != null) {
			conditions.append(" AND division_id = ?");
			parameters.add(query.division());
		}
		if (query.faculty() != null) {
			conditions.append(" AND effective_faculty_id = ?");
			parameters.add(query.faculty());
		}
		if (query.batch() != null) {
			// A lab or tutorial names its batch; a theory lecture is for every batch of its division.
			if (query.batchDivision() == null) {
				conditions.append(" AND batch_id = ?");
				parameters.add(query.batch());
			}
			else {
				conditions.append(" AND (batch_id = ? OR (batch_id IS NULL AND division_id = ?))");
				parameters.add(query.batch());
				parameters.add(query.batchDivision());
			}
		}
		if (!query.visibility().everywhere()) {
			conditions.append(visibilityCondition(query.visibility(), parameters));
		}
		String where = conditions.toString();
		String select = SEARCH.formatted(where) + orderBy(pageable.getSort(), SORT_COLUMNS);
		return queryPage(select, COUNT_SEARCH.formatted(where), JdbcOccurrenceStore::toOccurrence, pageable,
				parameters.toArray());
	}

	/**
	 * A lecture is visible when any one of the places the caller may see holds. A kind of place with nothing in it adds no
	 * condition; no place at all allows nothing. Only fixed text and the number of values go into the SQL.
	 */
	private static String visibilityCondition(Visibility visibility, List<Object> parameters) {
		List<String> any = new ArrayList<>();
		if (!visibility.departments().isEmpty()) {
			any.add("department_id IN (" + placeholders(visibility.departments().size()) + ")");
			parameters.addAll(visibility.departments());
		}
		if (!visibility.divisions().isEmpty()) {
			any.add("division_id IN (" + placeholders(visibility.divisions().size()) + ")");
			parameters.addAll(visibility.divisions());
		}
		if (!visibility.batches().isEmpty()) {
			any.add("batch_id IN (" + placeholders(visibility.batches().size()) + ")");
			parameters.addAll(visibility.batches());
		}
		if (!visibility.divisionsOfBatches().isEmpty()) {
			any.add("(batch_id IS NULL AND division_id IN (" + placeholders(visibility.divisionsOfBatches().size())
					+ "))");
			parameters.addAll(visibility.divisionsOfBatches());
		}
		if (!visibility.faculty().isEmpty()) {
			any.add("effective_faculty_id IN (" + placeholders(visibility.faculty().size()) + ")");
			parameters.addAll(visibility.faculty());
		}
		return any.isEmpty() ? " AND 1 = 0" : " AND (" + String.join(" OR ", any) + ")";
	}

	private static Occurrence toOccurrence(ResultSet row) throws SQLException {
		LectureSlot baseSlot = new LectureSlot(row.getObject("occurrence_date", LocalDate.class),
				row.getObject("start_time", LocalTime.class), row.getObject("end_time", LocalTime.class),
				LectureMode.valueOf(row.getString("mode")), uuidOrNull(row, "room_id"), row.getString("meeting_link"));
		LectureSlot slot = new LectureSlot(row.getObject("effective_date", LocalDate.class),
				row.getObject("effective_start_time", LocalTime.class),
				row.getObject("effective_end_time", LocalTime.class),
				LectureMode.valueOf(row.getString("effective_mode")), uuidOrNull(row, "effective_room_id"),
				row.getString("effective_meeting_link"));
		return new Occurrence(uuid(row, "id"), uuid(row, "tenant_id"), uuidOrNull(row, "timetable_entry_id"),
				uuid(row, "timetable_version_id"), uuid(row, "assignment_id"), uuid(row, "department_id"),
				uuid(row, "division_id"), uuidOrNull(row, "batch_id"),
				SessionType.valueOf(row.getString("session_type")), uuid(row, "faculty_id"),
				uuid(row, "effective_faculty_id"), baseSlot, slot, row.getBoolean("is_cancelled"),
				uuidOrNull(row, "changed_by_ticket_id"), row.getLong("version"));
	}

}
