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

import com.kibootia.backend.reference.application.Batch;
import com.kibootia.backend.reference.application.CampusStore;
import com.kibootia.backend.reference.application.Department;
import com.kibootia.backend.reference.application.Division;

/**
 * Keeps the campus structure in MySQL with hand-written SQL over JDBC: departments, the divisions in them and the batches in
 * a division. A new row is sent at once so a duplicate code is reported by the call that adds it (MySQL error 1062).
 */
@Component
class JdbcCampusStore extends JdbcReferenceSupport implements CampusStore {

	private static final String DEPARTMENT = "department";

	private static final String DIVISION = "division";

	private static final String BATCH = "batch";

	/** The columns a page may be sorted on, by the property name the web layer uses. */
	private static final Map<String, String> SORT_COLUMNS = Map.of("code", "code", "name", "name", "createdAt",
			"created_at");

	private static final String DEPARTMENT_COLUMNS = "id, code, name, version, created_at, updated_at";

	private static final String DIVISION_COLUMNS = "id, code, name, department_id, version, created_at, updated_at";

	private static final String BATCH_COLUMNS = "id, code, name, division_id, version, created_at, updated_at";

	// --- departments ------------------------------------------------------------------------------------------------

	private static final String INSERT_DEPARTMENT = """
			INSERT INTO department (id, tenant_id, version, created_at, created_by, updated_at, updated_by, code, name)
			VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?)
			""";

	private static final String FIND_DEPARTMENT = "SELECT " + DEPARTMENT_COLUMNS + """
			 FROM department WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL
			""";

	// --- divisions --------------------------------------------------------------------------------------------------

	private static final String INSERT_DIVISION = """
			INSERT INTO division (id, tenant_id, version, created_at, created_by, updated_at, updated_by, code, name,
			                      department_id)
			VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?, ?)
			""";

	private static final String FIND_DIVISION = "SELECT " + DIVISION_COLUMNS + """
			 FROM division WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL
			""";

	// The live divisions among some ids. %s is the list of ?.
	private static final String FIND_DIVISIONS = "SELECT " + DIVISION_COLUMNS + """
			 FROM division WHERE tenant_id = ? AND deleted_at IS NULL AND id IN (%s)
			 ORDER BY id
			""";

	// --- batches ----------------------------------------------------------------------------------------------------

	private static final String INSERT_BATCH = """
			INSERT INTO batch (id, tenant_id, version, created_at, created_by, updated_at, updated_by, code, name,
			                   division_id)
			VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?, ?)
			""";

	private static final String FIND_BATCH = "SELECT " + BATCH_COLUMNS + """
			 FROM batch WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL
			""";

	private static final String FIND_BATCHES = "SELECT " + BATCH_COLUMNS + """
			 FROM batch WHERE tenant_id = ? AND deleted_at IS NULL AND id IN (%s)
			 ORDER BY id
			""";

	// Every live batch of some divisions. %s is the list of ?.
	private static final String FIND_BATCHES_OF_DIVISIONS = "SELECT " + BATCH_COLUMNS + """
			 FROM batch WHERE tenant_id = ? AND deleted_at IS NULL AND division_id IN (%s)
			 ORDER BY id
			""";

	JdbcCampusStore(DataSource dataSource) {
		super(dataSource);
	}

	// --- departments ------------------------------------------------------------------------------------------------

	@Override
	public Department insertDepartment(String code, String name) {
		UUID id = newId();
		Instant now = now();
		execute(INSERT_DEPARTMENT, id, tenantForWrite(), now, actor(), now, actor(), code, name);
		return new Department(id, code, name, 0, now, now);
	}

	@Override
	public Optional<Department> findDepartment(UUID id) {
		return queryOne(FIND_DEPARTMENT, JdbcCampusStore::toDepartment, tenant(), id);
	}

	@Override
	public Page<Department> searchDepartments(String text, Pageable pageable) {
		return pageOfLive(DEPARTMENT_COLUMNS, DEPARTMENT, new Conditions().andText(likePattern(text)), SORT_COLUMNS,
				JdbcCampusStore::toDepartment, pageable);
	}

	@Override
	public Department renameDepartment(UUID id, long expectedVersion, String name) {
		Department current = findDepartment(id).orElseThrow();
		boolean written = renameIfChanged(DEPARTMENT, id, current.version(), expectedVersion, current.name(), name);
		return written ? findDepartment(id).orElseThrow() : current;
	}

	@Override
	public void deleteDepartment(UUID id) {
		softDelete(DEPARTMENT, id);
	}

	@Override
	public boolean departmentExists(UUID id) {
		return liveRowExists(DEPARTMENT, id);
	}

	@Override
	public boolean departmentCodeInUse(String code) {
		return liveCodeInUse(DEPARTMENT, code);
	}

	// --- divisions --------------------------------------------------------------------------------------------------

	@Override
	public Division insertDivision(String code, String name, UUID departmentId) {
		UUID id = newId();
		Instant now = now();
		execute(INSERT_DIVISION, id, tenantForWrite(), now, actor(), now, actor(), code, name, departmentId);
		return new Division(id, code, name, departmentId, 0, now, now);
	}

	@Override
	public Optional<Division> findDivision(UUID id) {
		return queryOne(FIND_DIVISION, JdbcCampusStore::toDivision, tenant(), id);
	}

	@Override
	public Page<Division> searchDivisions(UUID department, String text, Pageable pageable) {
		Conditions conditions = new Conditions();
		if (department != null) {
			conditions.and("department_id = ?", department);
		}
		return pageOfLive(DIVISION_COLUMNS, DIVISION, conditions.andText(likePattern(text)), SORT_COLUMNS,
				JdbcCampusStore::toDivision, pageable);
	}

	@Override
	public Division renameDivision(UUID id, long expectedVersion, String name) {
		Division current = findDivision(id).orElseThrow();
		boolean written = renameIfChanged(DIVISION, id, current.version(), expectedVersion, current.name(), name);
		return written ? findDivision(id).orElseThrow() : current;
	}

	@Override
	public void deleteDivision(UUID id) {
		softDelete(DIVISION, id);
	}

	@Override
	public boolean divisionExists(UUID id) {
		return liveRowExists(DIVISION, id);
	}

	@Override
	public boolean divisionCodeInUse(String code) {
		return liveCodeInUse(DIVISION, code);
	}

	// --- batches ----------------------------------------------------------------------------------------------------

	@Override
	public Batch insertBatch(String code, String name, UUID divisionId) {
		UUID id = newId();
		Instant now = now();
		execute(INSERT_BATCH, id, tenantForWrite(), now, actor(), now, actor(), code, name, divisionId);
		return new Batch(id, code, name, divisionId, 0, now, now);
	}

	@Override
	public Optional<Batch> findBatch(UUID id) {
		return queryOne(FIND_BATCH, JdbcCampusStore::toBatch, tenant(), id);
	}

	@Override
	public Page<Batch> searchBatches(UUID division, String text, Pageable pageable) {
		Conditions conditions = new Conditions();
		if (division != null) {
			conditions.and("division_id = ?", division);
		}
		return pageOfLive(BATCH_COLUMNS, BATCH, conditions.andText(likePattern(text)), SORT_COLUMNS,
				JdbcCampusStore::toBatch, pageable);
	}

	@Override
	public Batch renameBatch(UUID id, long expectedVersion, String name) {
		Batch current = findBatch(id).orElseThrow();
		boolean written = renameIfChanged(BATCH, id, current.version(), expectedVersion, current.name(), name);
		return written ? findBatch(id).orElseThrow() : current;
	}

	@Override
	public void deleteBatch(UUID id) {
		softDelete(BATCH, id);
	}

	@Override
	public boolean batchCodeInUse(String code) {
		return liveCodeInUse(BATCH, code);
	}

	// --- bulk lookups -----------------------------------------------------------------------------------------------

	@Override
	public List<Division> findDivisions(Collection<UUID> ids) {
		return ids.isEmpty() ? List.of() : queryAmong(FIND_DIVISIONS, JdbcCampusStore::toDivision, ids);
	}

	@Override
	public List<Batch> findBatches(Collection<UUID> ids) {
		return ids.isEmpty() ? List.of() : queryAmong(FIND_BATCHES, JdbcCampusStore::toBatch, ids);
	}

	@Override
	public List<Batch> findBatchesOfDivisions(Collection<UUID> divisionIds) {
		return divisionIds.isEmpty() ? List.of()
				: queryAmong(FIND_BATCHES_OF_DIVISIONS, JdbcCampusStore::toBatch, divisionIds);
	}

	// --- rows -------------------------------------------------------------------------------------------------------

	private static Department toDepartment(ResultSet row) throws SQLException {
		return new Department(uuid(row, "id"), row.getString("code"), row.getString("name"), row.getLong("version"),
				instant(row, "created_at"), instant(row, "updated_at"));
	}

	private static Division toDivision(ResultSet row) throws SQLException {
		return new Division(uuid(row, "id"), row.getString("code"), row.getString("name"), uuid(row, "department_id"),
				row.getLong("version"), instant(row, "created_at"), instant(row, "updated_at"));
	}

	private static Batch toBatch(ResultSet row) throws SQLException {
		return new Batch(uuid(row, "id"), row.getString("code"), row.getString("name"), uuid(row, "division_id"),
				row.getLong("version"), instant(row, "created_at"), instant(row, "updated_at"));
	}

}
