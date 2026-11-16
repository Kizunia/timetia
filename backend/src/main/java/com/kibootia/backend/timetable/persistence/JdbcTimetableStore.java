package com.kibootia.backend.timetable.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.kibootia.backend.platform.persistence.JdbcSupport;
import com.kibootia.backend.timetable.LectureMode;
import com.kibootia.backend.timetable.application.NewEntry;
import com.kibootia.backend.timetable.application.TimetableEntry;
import com.kibootia.backend.timetable.application.TimetableStore;
import com.kibootia.backend.timetable.application.TimetableVersion;
import com.kibootia.backend.timetable.application.VersionStatus;

/**
 * Keeps timetable versions and their entries in MySQL with hand-written SQL over JDBC. The generated {@code published_from}
 * column and the unique key on it are in the migration; they keep two published versions of a division from taking effect
 * on one day, and an attempt to publish a second one fails here as a data integrity violation.
 */
@Component
class JdbcTimetableStore extends JdbcSupport implements TimetableStore {

	/** The columns a page of versions may be sorted on, by the property name the web layer uses. */
	private static final Map<String, String> VERSION_SORT_COLUMNS = Map.of("effectiveFrom", "effective_from", "createdAt",
			"created_at");

	private static final String VERSION_COLUMNS = """
			id, division_id, term_id, effective_from, status, version, created_at, updated_at
			""";

	private static final String ENTRY_COLUMNS = """
			id, timetable_version_id, assignment_id, day_of_week, start_time, end_time, room_id, mode, meeting_link, version,
			created_at
			""";

	private static final String INSERT_VERSION = """
			INSERT INTO timetable_version (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
			                               division_id, term_id, effective_from, status)
			VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?)
			""";

	private static final String FIND_VERSION = "SELECT " + VERSION_COLUMNS + """
			FROM timetable_version
			WHERE tenant_id = ? AND id = ?
			""";

	// The versions of the tenant, narrowed by the conditions the caller asked for. %s is those conditions.
	private static final String SEARCH_VERSIONS = "SELECT " + VERSION_COLUMNS + """
			FROM timetable_version
			WHERE tenant_id = ?%s""";

	private static final String COUNT_VERSIONS = """
			SELECT COUNT(*) FROM timetable_version
			WHERE tenant_id = ?%s
			""";

	private static final String DELETE_ENTRIES_OF_VERSION = """
			DELETE FROM timetable_entry WHERE tenant_id = ? AND timetable_version_id = ?
			""";

	private static final String DELETE_VERSION = "DELETE FROM timetable_version WHERE tenant_id = ? AND id = ?";

	private static final String PUBLISH = """
			UPDATE timetable_version
			SET status = ?, version = version + 1, updated_at = ?, updated_by = ?
			WHERE tenant_id = ? AND id = ? AND version = ?
			""";

	// Is there already a published version of this division that takes effect on this date?
	private static final String COUNT_PUBLISHED_STARTING_ON = """
			SELECT COUNT(*) FROM timetable_version
			WHERE tenant_id = ? AND division_id = ? AND effective_from = ? AND status = ?
			""";

	// The published version in force on a date: the one with the latest start on or before it. The subquery finds that start.
	private static final String PUBLISHED_IN_FORCE = "SELECT " + VERSION_COLUMNS + """
			FROM timetable_version
			WHERE tenant_id = ? AND division_id = ? AND status = ? AND effective_from = (
			    SELECT MAX(w.effective_from) FROM timetable_version w
			    WHERE w.tenant_id = ? AND w.division_id = ? AND w.status = ? AND w.effective_from <= ?)
			""";

	// The first date after a date on which a published version takes effect (no row at all gives NULL).
	private static final String NEXT_PUBLISHED_AFTER = """
			SELECT MIN(effective_from) FROM timetable_version
			WHERE tenant_id = ? AND division_id = ? AND status = ? AND effective_from > ?
			""";

	private static final String INSERT_ENTRY = """
			INSERT INTO timetable_entry (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
			                             timetable_version_id, assignment_id, day_of_week, start_time, end_time, room_id,
			                             mode, meeting_link)
			VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""";

	// The entries of a version in a stable order: day, then start time.
	private static final String ENTRIES_OF = "SELECT " + ENTRY_COLUMNS + """
			FROM timetable_entry
			WHERE tenant_id = ? AND timetable_version_id = ?
			ORDER BY day_of_week, start_time, id
			""";

	private static final String FIND_ENTRY = "SELECT " + ENTRY_COLUMNS + """
			FROM timetable_entry
			WHERE tenant_id = ? AND id = ?
			""";

	private static final String DELETE_ENTRY = "DELETE FROM timetable_entry WHERE tenant_id = ? AND id = ?";

	JdbcTimetableStore(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public TimetableVersion insertVersion(UUID divisionId, UUID termId, LocalDate effectiveFrom) {
		UUID id = newId();
		Instant now = now();
		execute(INSERT_VERSION, id, tenantForWrite(), now, actor(), now, actor(), divisionId, termId, effectiveFrom,
				VersionStatus.DRAFT);
		return new TimetableVersion(id, divisionId, termId, effectiveFrom, VersionStatus.DRAFT, 0, now, now);
	}

	@Override
	public Optional<TimetableVersion> findVersion(UUID id) {
		return queryOne(FIND_VERSION, JdbcTimetableStore::toVersion, tenant(), id);
	}

	@Override
	public Page<TimetableVersion> searchVersions(UUID division, VersionStatus status, Pageable pageable) {
		StringBuilder conditions = new StringBuilder();
		List<Object> parameters = new ArrayList<>();
		parameters.add(tenant());
		if (division != null) {
			conditions.append(" AND division_id = ?");
			parameters.add(division);
		}
		if (status != null) {
			conditions.append(" AND status = ?");
			parameters.add(status);
		}
		String select = SEARCH_VERSIONS.formatted(conditions) + orderBy(pageable.getSort(), VERSION_SORT_COLUMNS);
		return queryPage(select, COUNT_VERSIONS.formatted(conditions), JdbcTimetableStore::toVersion, pageable,
				parameters.toArray());
	}

	@Override
	public void deleteVersion(UUID id) {
		execute(DELETE_ENTRIES_OF_VERSION, tenant(), id);
		execute(DELETE_VERSION, tenant(), id);
	}

	@Override
	public TimetableVersion markPublished(UUID id) {
		TimetableVersion current = findVersion(id).orElseThrow();
		if (current.status() == VersionStatus.PUBLISHED) {
			return current;
		}
		Instant now = now();
		int changed = execute(PUBLISH, VersionStatus.PUBLISHED, now, actor(), tenant(), id, current.version());
		requireOneRow(changed, "TimetableVersion", id);
		return new TimetableVersion(current.id(), current.divisionId(), current.termId(), current.effectiveFrom(),
				VersionStatus.PUBLISHED, current.version() + 1, current.createdAt(), now);
	}

	@Override
	public boolean publishedStartingOn(UUID divisionId, LocalDate effectiveFrom) {
		return queryCount(COUNT_PUBLISHED_STARTING_ON, tenant(), divisionId, effectiveFrom, VersionStatus.PUBLISHED) > 0;
	}

	@Override
	public Optional<TimetableVersion> publishedInForce(UUID divisionId, LocalDate date) {
		return queryOne(PUBLISHED_IN_FORCE, JdbcTimetableStore::toVersion, tenant(), divisionId, VersionStatus.PUBLISHED,
				tenant(), divisionId, VersionStatus.PUBLISHED, date);
	}

	@Override
	public Optional<LocalDate> nextPublishedAfter(UUID divisionId, LocalDate date) {
		return queryOne(NEXT_PUBLISHED_AFTER, (row) -> row.getObject(1, LocalDate.class), tenant(), divisionId,
				VersionStatus.PUBLISHED, date);
	}

	@Override
	public TimetableEntry insertEntry(UUID versionId, NewEntry entry) {
		UUID id = newId();
		Instant now = now();
		execute(INSERT_ENTRY, id, tenantForWrite(), now, actor(), now, actor(), versionId, entry.assignmentId(),
				entry.dayOfWeek(), entry.startTime(), entry.endTime(), entry.roomId(), entry.mode(), entry.meetingLink());
		return new TimetableEntry(id, versionId, entry.assignmentId(), entry.dayOfWeek(), entry.startTime(),
				entry.endTime(), entry.roomId(), entry.mode(), entry.meetingLink(), 0, now);
	}

	@Override
	public List<TimetableEntry> entriesOf(UUID versionId) {
		return queryList(ENTRIES_OF, JdbcTimetableStore::toEntry, tenant(), versionId);
	}

	@Override
	public Optional<TimetableEntry> findEntry(UUID id) {
		return queryOne(FIND_ENTRY, JdbcTimetableStore::toEntry, tenant(), id);
	}

	@Override
	public void deleteEntry(UUID id) {
		execute(DELETE_ENTRY, tenant(), id);
	}

	private static TimetableVersion toVersion(ResultSet row) throws SQLException {
		return new TimetableVersion(uuid(row, "id"), uuid(row, "division_id"), uuid(row, "term_id"),
				row.getObject("effective_from", LocalDate.class), VersionStatus.valueOf(row.getString("status")),
				row.getLong("version"), instant(row, "created_at"), instant(row, "updated_at"));
	}

	private static TimetableEntry toEntry(ResultSet row) throws SQLException {
		return new TimetableEntry(uuid(row, "id"), uuid(row, "timetable_version_id"), uuid(row, "assignment_id"),
				row.getInt("day_of_week"), row.getObject("start_time", LocalTime.class),
				row.getObject("end_time", LocalTime.class), uuidOrNull(row, "room_id"),
				LectureMode.valueOf(row.getString("mode")), row.getString("meeting_link"), row.getLong("version"),
				instant(row, "created_at"));
	}

}
