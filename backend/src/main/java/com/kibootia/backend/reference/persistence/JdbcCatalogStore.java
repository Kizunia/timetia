package com.kibootia.backend.reference.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.kibootia.backend.reference.application.AcademicTerm;
import com.kibootia.backend.reference.application.CatalogStore;
import com.kibootia.backend.reference.application.Faculty;
import com.kibootia.backend.reference.application.Room;
import com.kibootia.backend.reference.application.RoomType;
import com.kibootia.backend.reference.application.Subject;

/**
 * Keeps the reference catalog in MySQL with hand-written SQL over JDBC: subjects, rooms, faculty and academic terms. A new row
 * is sent at once so a duplicate code is reported by the call that adds it (MySQL error 1062).
 */
@Component
class JdbcCatalogStore extends JdbcReferenceSupport implements CatalogStore {

	private static final String SUBJECT = "subject";

	private static final String ROOM = "room";

	private static final String FACULTY = "faculty";

	private static final String TERM = "academic_term";

	/** The columns a page may be sorted on, by the property name the web layer uses. */
	private static final Map<String, String> SORT_COLUMNS = Map.of("code", "code", "name", "name", "createdAt",
			"created_at", "startDate", "start_date");

	private static final String SUBJECT_COLUMNS = "id, code, name, version, created_at, updated_at";

	private static final String ROOM_COLUMNS = "id, code, name, room_type, capacity, version, created_at, updated_at";

	private static final String FACULTY_COLUMNS = "id, code, name, department_id, user_id, version, created_at, updated_at";

	private static final String TERM_COLUMNS = "id, code, name, start_date, end_date, version, created_at, updated_at";

	// --- subjects ---------------------------------------------------------------------------------------------------

	private static final String INSERT_SUBJECT = """
			INSERT INTO subject (id, tenant_id, version, created_at, created_by, updated_at, updated_by, code, name)
			VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?)
			""";

	private static final String FIND_SUBJECT = "SELECT " + SUBJECT_COLUMNS + """
			 FROM subject WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL
			""";

	private static final String FIND_SUBJECTS = "SELECT " + SUBJECT_COLUMNS + """
			 FROM subject WHERE tenant_id = ? AND deleted_at IS NULL AND id IN (%s)
			 ORDER BY id
			""";

	// --- rooms ------------------------------------------------------------------------------------------------------

	private static final String INSERT_ROOM = """
			INSERT INTO room (id, tenant_id, version, created_at, created_by, updated_at, updated_by, code, name,
			                  room_type, capacity)
			VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?)
			""";

	private static final String FIND_ROOM = "SELECT " + ROOM_COLUMNS + """
			 FROM room WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL
			""";

	private static final String FIND_ROOMS = "SELECT " + ROOM_COLUMNS + """
			 FROM room WHERE tenant_id = ? AND deleted_at IS NULL AND id IN (%s)
			 ORDER BY id
			""";

	// --- faculty ----------------------------------------------------------------------------------------------------

	private static final String INSERT_FACULTY = """
			INSERT INTO faculty (id, tenant_id, version, created_at, created_by, updated_at, updated_by, code, name,
			                     department_id, user_id)
			VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?)
			""";

	private static final String FIND_FACULTY = "SELECT " + FACULTY_COLUMNS + """
			 FROM faculty WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL
			""";

	private static final String FIND_FACULTY_BY_IDS = "SELECT " + FACULTY_COLUMNS + """
			 FROM faculty WHERE tenant_id = ? AND deleted_at IS NULL AND id IN (%s)
			 ORDER BY id
			""";

	// The live faculty records that belong to one user account (normally one).
	private static final String FACULTY_IDS_OF_USER = """
			SELECT id FROM faculty WHERE tenant_id = ? AND user_id = ? AND deleted_at IS NULL
			""";

	// --- terms ------------------------------------------------------------------------------------------------------

	private static final String INSERT_TERM = """
			INSERT INTO academic_term (id, tenant_id, version, created_at, created_by, updated_at, updated_by, code, name,
			                           start_date, end_date)
			VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?)
			""";

	private static final String FIND_TERM = "SELECT " + TERM_COLUMNS + """
			 FROM academic_term WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL
			""";

	JdbcCatalogStore(DataSource dataSource) {
		super(dataSource);
	}

	// --- subjects ---------------------------------------------------------------------------------------------------

	@Override
	public Subject insertSubject(String code, String name) {
		UUID id = newId();
		Instant now = now();
		execute(INSERT_SUBJECT, id, tenantForWrite(), now, actor(), now, actor(), code, name);
		return new Subject(id, code, name, 0, now, now);
	}

	@Override
	public Optional<Subject> findSubject(UUID id) {
		return queryOne(FIND_SUBJECT, JdbcCatalogStore::toSubject, tenant(), id);
	}

	@Override
	public Page<Subject> searchSubjects(String text, Pageable pageable) {
		return pageOfLive(SUBJECT_COLUMNS, SUBJECT, new Conditions().andText(likePattern(text)), SORT_COLUMNS,
				JdbcCatalogStore::toSubject, pageable);
	}

	@Override
	public Subject renameSubject(UUID id, long expectedVersion, String name) {
		Subject current = findSubject(id).orElseThrow();
		boolean written = renameIfChanged(SUBJECT, id, current.version(), expectedVersion, current.name(), name);
		return written ? findSubject(id).orElseThrow() : current;
	}

	@Override
	public void deleteSubject(UUID id) {
		softDelete(SUBJECT, id);
	}

	@Override
	public boolean subjectExists(UUID id) {
		return liveRowExists(SUBJECT, id);
	}

	@Override
	public boolean subjectCodeInUse(String code) {
		return liveCodeInUse(SUBJECT, code);
	}

	// --- rooms ------------------------------------------------------------------------------------------------------

	@Override
	public Room insertRoom(String code, String name, RoomType roomType, Integer capacity) {
		UUID id = newId();
		Instant now = now();
		execute(INSERT_ROOM, id, tenantForWrite(), now, actor(), now, actor(), code, name, roomType, capacity);
		return new Room(id, code, name, roomType, capacity, 0, now, now);
	}

	@Override
	public Optional<Room> findRoom(UUID id) {
		return queryOne(FIND_ROOM, JdbcCatalogStore::toRoom, tenant(), id);
	}

	@Override
	public Page<Room> searchRooms(String text, Pageable pageable) {
		return pageOfLive(ROOM_COLUMNS, ROOM, new Conditions().andText(likePattern(text)), SORT_COLUMNS,
				JdbcCatalogStore::toRoom, pageable);
	}

	@Override
	public Room renameRoom(UUID id, long expectedVersion, String name) {
		Room current = findRoom(id).orElseThrow();
		boolean written = renameIfChanged(ROOM, id, current.version(), expectedVersion, current.name(), name);
		return written ? findRoom(id).orElseThrow() : current;
	}

	@Override
	public void deleteRoom(UUID id) {
		softDelete(ROOM, id);
	}

	@Override
	public boolean roomExists(UUID id) {
		return liveRowExists(ROOM, id);
	}

	@Override
	public boolean roomCodeInUse(String code) {
		return liveCodeInUse(ROOM, code);
	}

	// --- faculty ----------------------------------------------------------------------------------------------------

	@Override
	public Faculty insertFaculty(String code, String name, UUID departmentId, UUID userId) {
		UUID id = newId();
		Instant now = now();
		execute(INSERT_FACULTY, id, tenantForWrite(), now, actor(), now, actor(), code, name, departmentId, userId);
		return new Faculty(id, code, name, departmentId, userId, 0, now, now);
	}

	@Override
	public Optional<Faculty> findFaculty(UUID id) {
		return queryOne(FIND_FACULTY, JdbcCatalogStore::toFaculty, tenant(), id);
	}

	@Override
	public Page<Faculty> searchFaculty(UUID department, String text, Pageable pageable) {
		Conditions conditions = new Conditions();
		if (department != null) {
			conditions.and("department_id = ?", department);
		}
		return pageOfLive(FACULTY_COLUMNS, FACULTY, conditions.andText(likePattern(text)), SORT_COLUMNS,
				JdbcCatalogStore::toFaculty, pageable);
	}

	@Override
	public Faculty renameFaculty(UUID id, long expectedVersion, String name) {
		Faculty current = findFaculty(id).orElseThrow();
		boolean written = renameIfChanged(FACULTY, id, current.version(), expectedVersion, current.name(), name);
		return written ? findFaculty(id).orElseThrow() : current;
	}

	@Override
	public void deleteFaculty(UUID id) {
		softDelete(FACULTY, id);
	}

	@Override
	public boolean facultyExists(UUID id) {
		return liveRowExists(FACULTY, id);
	}

	@Override
	public boolean facultyCodeInUse(String code) {
		return liveCodeInUse(FACULTY, code);
	}

	// --- terms ------------------------------------------------------------------------------------------------------

	@Override
	public AcademicTerm insertTerm(String code, String name, LocalDate startDate, LocalDate endDate) {
		UUID id = newId();
		Instant now = now();
		execute(INSERT_TERM, id, tenantForWrite(), now, actor(), now, actor(), code, name, startDate, endDate);
		return new AcademicTerm(id, code, name, startDate, endDate, 0, now, now);
	}

	@Override
	public Optional<AcademicTerm> findTerm(UUID id) {
		return queryOne(FIND_TERM, JdbcCatalogStore::toTerm, tenant(), id);
	}

	@Override
	public Page<AcademicTerm> searchTerms(String text, Pageable pageable) {
		return pageOfLive(TERM_COLUMNS, TERM, new Conditions().andText(likePattern(text)), SORT_COLUMNS,
				JdbcCatalogStore::toTerm, pageable);
	}

	@Override
	public AcademicTerm renameTerm(UUID id, long expectedVersion, String name) {
		AcademicTerm current = findTerm(id).orElseThrow();
		boolean written = renameIfChanged(TERM, id, current.version(), expectedVersion, current.name(), name);
		return written ? findTerm(id).orElseThrow() : current;
	}

	@Override
	public void deleteTerm(UUID id) {
		softDelete(TERM, id);
	}

	@Override
	public boolean termExists(UUID id) {
		return liveRowExists(TERM, id);
	}

	@Override
	public boolean termCodeInUse(String code) {
		return liveCodeInUse(TERM, code);
	}

	// --- bulk lookups -----------------------------------------------------------------------------------------------

	@Override
	public List<Subject> findSubjects(Collection<UUID> ids) {
		return ids.isEmpty() ? List.of() : queryAmong(FIND_SUBJECTS, JdbcCatalogStore::toSubject, ids);
	}

	@Override
	public List<Room> findRooms(Collection<UUID> ids) {
		return ids.isEmpty() ? List.of() : queryAmong(FIND_ROOMS, JdbcCatalogStore::toRoom, ids);
	}

	@Override
	public List<Faculty> findFacultyByIds(Collection<UUID> ids) {
		return ids.isEmpty() ? List.of() : queryAmong(FIND_FACULTY_BY_IDS, JdbcCatalogStore::toFaculty, ids);
	}

	@Override
	public List<UUID> findFacultyIdsOfUser(UUID userId) {
		return queryList(FACULTY_IDS_OF_USER, (row) -> uuid(row, "id"), tenant(), userId);
	}

	// --- rows -------------------------------------------------------------------------------------------------------

	private static Subject toSubject(ResultSet row) throws SQLException {
		return new Subject(uuid(row, "id"), row.getString("code"), row.getString("name"), row.getLong("version"),
				instant(row, "created_at"), instant(row, "updated_at"));
	}

	private static Room toRoom(ResultSet row) throws SQLException {
		return new Room(uuid(row, "id"), row.getString("code"), row.getString("name"),
				RoomType.valueOf(row.getString("room_type")), row.getObject("capacity", Integer.class),
				row.getLong("version"), instant(row, "created_at"), instant(row, "updated_at"));
	}

	private static Faculty toFaculty(ResultSet row) throws SQLException {
		return new Faculty(uuid(row, "id"), row.getString("code"), row.getString("name"), uuid(row, "department_id"),
				uuidOrNull(row, "user_id"), row.getLong("version"), instant(row, "created_at"),
				instant(row, "updated_at"));
	}

	private static AcademicTerm toTerm(ResultSet row) throws SQLException {
		return new AcademicTerm(uuid(row, "id"), row.getString("code"), row.getString("name"),
				row.getObject("start_date", LocalDate.class), row.getObject("end_date", LocalDate.class),
				row.getLong("version"), instant(row, "created_at"), instant(row, "updated_at"));
	}

}
