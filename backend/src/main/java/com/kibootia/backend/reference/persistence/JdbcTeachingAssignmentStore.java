package com.kibootia.backend.reference.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.reference.application.TeachingAssignment;
import com.kibootia.backend.reference.application.TeachingAssignmentStore;

/**
 * Keeps teaching assignments in MySQL with hand-written SQL over JDBC. The generated {@code batch_scope} and
 * {@code active_marker} columns, the unique key over them and the batch rule are in the migration. A new row is sent at once
 * so a duplicate is reported by the call that adds it.
 */
@Component
class JdbcTeachingAssignmentStore extends JdbcReferenceSupport implements TeachingAssignmentStore {

	private static final String TABLE = "teaching_assignment";

	/** The columns a page may be sorted on, by the property name the web layer uses. */
	private static final Map<String, String> SORT_COLUMNS = Map.of("createdAt", "created_at");

	private static final String COLUMNS = """
			id, division_id, batch_id, subject_id, faculty_id, session_type, version, created_at, updated_at""";

	private static final String INSERT = """
			INSERT INTO teaching_assignment (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
			                                 division_id, batch_id, subject_id, faculty_id, session_type)
			VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""";

	private static final String FIND = "SELECT " + COLUMNS + """

			FROM teaching_assignment WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL
			""";

	// The live assignments among some ids. %s is the list of ?.
	private static final String FIND_ALL = "SELECT " + COLUMNS + """

			FROM teaching_assignment WHERE tenant_id = ? AND deleted_at IS NULL AND id IN (%s)
			ORDER BY id
			""";

	// The live assignments of some faculty members. %s is the list of ?.
	private static final String FIND_BY_FACULTY = "SELECT " + COLUMNS + """

			FROM teaching_assignment WHERE tenant_id = ? AND deleted_at IS NULL AND faculty_id IN (%s)
			ORDER BY id
			""";

	// The live assignments in some divisions. %s is the list of ?.
	private static final String FIND_BY_DIVISIONS = "SELECT " + COLUMNS + """

			FROM teaching_assignment WHERE tenant_id = ? AND deleted_at IS NULL AND division_id IN (%s)
			ORDER BY id
			""";

	// Does a live assignment already cover this division, subject and session type for a batch? %s is the batch test: a
	// theory assignment has no batch, and NULL never equals NULL, so it has a test of its own.
	private static final String COUNT_DUPLICATES = """
			SELECT COUNT(*) FROM teaching_assignment
			WHERE tenant_id = ? AND deleted_at IS NULL AND division_id = ? AND subject_id = ? AND session_type = ?
			  AND %s
			""";

	JdbcTeachingAssignmentStore(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public TeachingAssignment insert(UUID divisionId, UUID batchId, UUID subjectId, UUID facultyId,
			SessionType sessionType) {
		UUID id = newId();
		Instant now = now();
		execute(INSERT, id, tenantForWrite(), now, actor(), now, actor(), divisionId, batchId, subjectId, facultyId,
				sessionType);
		return new TeachingAssignment(id, divisionId, batchId, subjectId, facultyId, sessionType, 0, now, now);
	}

	@Override
	public Optional<TeachingAssignment> find(UUID id) {
		return queryOne(FIND, JdbcTeachingAssignmentStore::toAssignment, tenant(), id);
	}

	@Override
	public List<TeachingAssignment> findAll(Collection<UUID> ids) {
		return ids.isEmpty() ? List.of() : queryAmong(FIND_ALL, JdbcTeachingAssignmentStore::toAssignment, ids);
	}

	@Override
	public List<TeachingAssignment> findByFaculty(Collection<UUID> facultyIds) {
		return facultyIds.isEmpty() ? List.of()
				: queryAmong(FIND_BY_FACULTY, JdbcTeachingAssignmentStore::toAssignment, facultyIds);
	}

	@Override
	public List<TeachingAssignment> findByDivisions(Collection<UUID> divisionIds) {
		return divisionIds.isEmpty() ? List.of()
				: queryAmong(FIND_BY_DIVISIONS, JdbcTeachingAssignmentStore::toAssignment, divisionIds);
	}

	@Override
	public Page<TeachingAssignment> search(UUID division, UUID faculty, UUID subject, Pageable pageable) {
		Conditions conditions = new Conditions();
		if (division != null) {
			conditions.and("division_id = ?", division);
		}
		if (faculty != null) {
			conditions.and("faculty_id = ?", faculty);
		}
		if (subject != null) {
			conditions.and("subject_id = ?", subject);
		}
		return pageOfLive(COLUMNS, TABLE, conditions, SORT_COLUMNS, JdbcTeachingAssignmentStore::toAssignment,
				pageable);
	}

	@Override
	public void delete(UUID id) {
		softDelete(TABLE, id);
	}

	@Override
	public boolean exists(UUID id) {
		return liveRowExists(TABLE, id);
	}

	@Override
	public boolean duplicateExists(UUID divisionId, UUID subjectId, SessionType sessionType, UUID batchId) {
		if (batchId == null) {
			return queryCount(COUNT_DUPLICATES.formatted("batch_id IS NULL"), tenant(), divisionId, subjectId,
					sessionType) > 0;
		}
		return queryCount(COUNT_DUPLICATES.formatted("batch_id = ?"), tenant(), divisionId, subjectId, sessionType,
				batchId) > 0;
	}

	private static TeachingAssignment toAssignment(ResultSet row) throws SQLException {
		return new TeachingAssignment(uuid(row, "id"), uuid(row, "division_id"), uuidOrNull(row, "batch_id"),
				uuid(row, "subject_id"), uuid(row, "faculty_id"), SessionType.valueOf(row.getString("session_type")),
				row.getLong("version"), instant(row, "created_at"), instant(row, "updated_at"));
	}

}
