package com.kibootia.backend.reference.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.kibootia.backend.platform.persistence.JdbcSupport;

/**
 * What the reference tables have in common. Every one of them (department, division, batch, subject, room, faculty,
 * academic term, teaching assignment) is a soft-deleted business table: a live row has {@code deleted_at IS NULL}, deleting
 * sets {@code deleted_at} and keeps the row, and a code is unique among the live rows of a tenant (the unique key includes
 * the generated {@code active_marker}). The statements here take the table name as an argument; it is always a constant of
 * the calling adapter, never text from a request.
 */
abstract class JdbcReferenceSupport extends JdbcSupport {

	// Is there a live row with this id?
	private static final String COUNT_LIVE = "SELECT COUNT(*) FROM %s WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL";

	// Is there a live row with this code? The database compares codes with the column's collation, as it always did.
	private static final String COUNT_CODE = "SELECT COUNT(*) FROM %s WHERE tenant_id = ? AND code = ? AND deleted_at IS NULL";

	// A delete keeps the row and records when it was deleted; the audit columns and the version are left alone.
	private static final String SOFT_DELETE = "UPDATE %s SET deleted_at = ? WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL";

	private static final String RENAME = """
			UPDATE %s SET name = ?, version = version + 1, updated_at = ?, updated_by = ?
			WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL AND version = ?
			""";

	JdbcReferenceSupport(DataSource dataSource) {
		super(dataSource);
	}

	protected final boolean liveRowExists(String table, UUID id) {
		return queryCount(COUNT_LIVE.formatted(table), tenant(), id) > 0;
	}

	protected final boolean liveCodeInUse(String table, String code) {
		return queryCount(COUNT_CODE.formatted(table), tenant(), code) > 0;
	}

	protected final void softDelete(String table, UUID id) {
		execute(SOFT_DELETE.formatted(table), now(), tenant(), id);
	}

	/**
	 * Gives a row a new name if the caller still has its version. A stale version is refused as an optimistic lock failure;
	 * a name that is already the row's name writes nothing and leaves the version alone. Returns whether it wrote.
	 */
	protected final boolean renameIfChanged(String table, UUID id, long actualVersion, long expectedVersion,
			String currentName, String newName) {
		if (actualVersion != expectedVersion) {
			throw staleVersion(table, id);
		}
		if (currentName.equals(newName)) {
			return false;
		}
		Instant now = now();
		int changed = execute(RENAME.formatted(table), newName, now, actor(), tenant(), id, actualVersion);
		requireOneRow(changed, table, id);
		return true;
	}

	/**
	 * One page of the live rows of a table, narrowed by the conditions. The select and the count share the same
	 * {@code WHERE}, so the page and its total agree.
	 */
	protected final <T> Page<T> pageOfLive(String columns, String table, Conditions conditions,
			Map<String, String> sortColumns, RowMapper<T> mapper, Pageable pageable) {
		String where = " FROM " + table + " WHERE tenant_id = ? AND deleted_at IS NULL" + conditions.sql();
		List<Object> parameters = new ArrayList<>();
		parameters.add(tenant());
		parameters.addAll(conditions.values());
		return queryPage("SELECT " + columns + where + orderBy(pageable.getSort(), sortColumns),
				"SELECT COUNT(*)" + where, mapper, pageable, parameters.toArray());
	}

	/**
	 * Runs a statement whose parameters are the tenant followed by a list of ids, and whose text has {@code %s} where the
	 * {@code ?} for those ids go.
	 */
	protected final <T> List<T> queryAmong(String sqlWithIdList, RowMapper<T> mapper, Collection<UUID> ids) {
		List<Object> parameters = new ArrayList<>();
		parameters.add(tenant());
		parameters.addAll(ids);
		return queryList(sqlWithIdList.formatted(placeholders(ids.size())), mapper, parameters.toArray());
	}

	/**
	 * A lower-case LIKE pattern that matches the text anywhere, with the user's own {@code %} and {@code _} made literal by
	 * {@code !}; null for no text filter.
	 */
	protected static String likePattern(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String escaped = text.strip().toLowerCase().replace("!", "!!").replace("%", "!%").replace("_", "!_");
		return "%" + escaped + "%";
	}

	/** The extra {@code AND} conditions of a search, built together with the values their {@code ?} stand for. */
	static final class Conditions {

		private final StringBuilder sql = new StringBuilder();

		private final List<Object> values = new ArrayList<>();

		/** Adds {@code AND condition}; {@code bound} are the values of the {@code ?} in it, in order. */
		Conditions and(String condition, Object... bound) {
			this.sql.append(" AND ").append(condition);
			Collections.addAll(this.values, bound);
			return this;
		}

		/** Adds the text filter on code or name, if there is a pattern (see {@link #likePattern}). */
		Conditions andText(String pattern) {
			if (pattern != null) {
				and("(LOWER(code) LIKE ? ESCAPE '!' OR LOWER(name) LIKE ? ESCAPE '!')", pattern, pattern);
			}
			return this;
		}

		String sql() {
			return this.sql.toString();
		}

		List<Object> values() {
			return this.values;
		}

	}

}
