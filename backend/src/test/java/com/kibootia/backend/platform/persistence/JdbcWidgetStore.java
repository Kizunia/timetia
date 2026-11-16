package com.kibootia.backend.platform.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * A test-only adapter written the way the real ones are, over the {@code test_widget} table, so {@link JdbcSupportIT} can
 * check what {@link JdbcSupport} promises. It is not a Spring bean: the test creates it.
 */
class JdbcWidgetStore extends JdbcSupport {

	record Widget(UUID id, UUID tenantId, long version, String name, Instant createdAt, UUID createdBy, Instant updatedAt,
			UUID updatedBy) {

	}

	private static final String INSERT = """
			INSERT INTO test_widget (id, tenant_id, version, created_at, created_by, updated_at, updated_by, name)
			VALUES (?, ?, 0, ?, ?, ?, ?, ?)
			""";

	// One live widget of this tenant by its key.
	private static final String FIND = """
			SELECT id, tenant_id, version, name, created_at, created_by, updated_at, updated_by
			FROM test_widget
			WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL
			""";

	private static final String RENAME = """
			UPDATE test_widget SET name = ?, version = version + 1, updated_at = ?, updated_by = ?
			WHERE tenant_id = ? AND id = ? AND version = ? AND deleted_at IS NULL
			""";

	private static final String SOFT_DELETE = """
			UPDATE test_widget SET deleted_at = ?, updated_at = ?, updated_by = ?
			WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL
			""";

	private static final String PAGE = """
			SELECT id, tenant_id, version, name, created_at, created_by, updated_at, updated_by
			FROM test_widget
			WHERE tenant_id = ? AND deleted_at IS NULL
			ORDER BY name
			""";

	private static final String COUNT = "SELECT COUNT(*) FROM test_widget WHERE tenant_id = ? AND deleted_at IS NULL";

	JdbcWidgetStore(DataSource dataSource) {
		super(dataSource);
	}

	UUID insert(String name) {
		UUID id = newId();
		Instant now = now();
		execute(INSERT, id, tenantForWrite(), now, actor(), now, actor(), name);
		return id;
	}

	Optional<Widget> find(UUID id) {
		return queryOne(FIND, (row) -> new Widget(uuid(row, "id"), uuid(row, "tenant_id"), row.getLong("version"),
				row.getString("name"), instant(row, "created_at"), uuid(row, "created_by"), instant(row, "updated_at"),
				uuid(row, "updated_by")), tenant(), id);
	}

	void rename(UUID id, long expectedVersion, String name) {
		int changed = execute(RENAME, name, now(), actor(), tenantForWrite(), id, expectedVersion);
		requireOneRow(changed, "Widget", id);
	}

	void softDelete(UUID id) {
		Instant now = now();
		execute(SOFT_DELETE, now, now, actor(), tenantForWrite(), id);
	}

	Page<Widget> page(Pageable pageable) {
		return queryPage(PAGE, COUNT, (row) -> new Widget(uuid(row, "id"), uuid(row, "tenant_id"), row.getLong("version"),
				row.getString("name"), instant(row, "created_at"), uuid(row, "created_by"), instant(row, "updated_at"),
				uuid(row, "updated_by")), pageable, tenant());
	}

	void runBrokenStatement() {
		queryList("SELECT no_such_column FROM test_widget", (row) -> row.getString(1));
	}

}
