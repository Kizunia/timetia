package com.kibootia.backend.platform.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.kibootia.backend.platform.tenancy.TenantContext;

/**
 * The base of every hand-written JDBC adapter. An adapter extends it, keeps its SQL as text blocks and calls the
 * {@code query...} and {@code execute} methods. Only {@code java.sql} types appear in the adapter; the base class does the
 * parts every adapter would repeat:
 *
 * <ul>
 * <li>It takes the connection with {@link DataSourceUtils}, so a use case's {@code @Transactional} covers the adapter's
 * statements: they use the same connection, commit together and roll back together. Without a transaction the statement
 * runs on its own connection and commits at once.</li>
 * <li>It binds {@code ?} parameters: a {@link UUID} becomes the 16 bytes of a {@code BINARY(16)} column, an
 * {@link Instant} becomes a UTC {@code DATETIME(6)}, an enum becomes its name.</li>
 * <li>It turns an {@link SQLException} into the exception the rest of the code expects (see {@link #translate}).</li>
 * <li>It gives the values every table needs: the tenant, the acting user, the time and a new UUIDv7 key.</li>
 * </ul>
 *
 * <p>What Hibernate did for a JPA entity is still the adapter's job, because only the adapter writes the SQL: put
 * {@code tenant_id = ?} in every {@code WHERE} and write it on every {@code INSERT}; set the four audit columns on insert
 * and the two {@code updated_*} columns on update; start {@code version} at 0 and change a row only
 * {@code WHERE version = ?}, passing the number of rows changed to {@link #requireOneRow}.
 */
public abstract class JdbcSupport {

	/** MySQL error numbers that {@link #translate} recognises. */
	private static final int MYSQL_DEADLOCK = 1213;

	private static final int MYSQL_LOCK_WAIT_TIMEOUT = 1205;

	private static final int MYSQL_CHECK_CONSTRAINT_VIOLATED = 3819;

	/** The millisecond and counter of the last key made, so that keys made in the same millisecond still increase. */
	private static final AtomicLong LAST_STAMP = new AtomicLong();

	private static final SecureRandom RANDOM = new SecureRandom();

	private final DataSource dataSource;

	protected JdbcSupport(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	/**
	 * Turns one row of a result into an object. It must not move the cursor. Public, so that a module's own base class can
	 * name it in a method signature without that counting as a use of something the platform keeps to itself.
	 */
	@FunctionalInterface
	public interface RowMapper<T> {

		T map(ResultSet row) throws SQLException;

	}

	// ---------------------------------------------------------------------------------------------------------------
	// Running statements

	/** Runs a {@code SELECT} and maps every row. */
	protected final <T> List<T> queryList(String sql, RowMapper<T> mapper, Object... parameters) {
		Connection connection = DataSourceUtils.getConnection(this.dataSource);
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			bind(statement, parameters);
			try (ResultSet rows = statement.executeQuery()) {
				List<T> result = new ArrayList<>();
				while (rows.next()) {
					result.add(mapper.map(rows));
				}
				return result;
			}
		}
		catch (SQLException ex) {
			throw translate(sql, ex);
		}
		finally {
			DataSourceUtils.releaseConnection(connection, this.dataSource);
		}
	}

	/**
	 * Runs a {@code SELECT} that finds at most one row. A row the mapper turns into {@code null} counts as no row: that is
	 * how {@code SELECT MIN(...)}, which always returns one row, answers "nothing".
	 */
	protected final <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... parameters) {
		List<T> rows = queryList(sql, mapper, parameters);
		return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
	}

	/** Runs a {@code SELECT} whose first column of the first row is a number, such as {@code COUNT(*)}. */
	protected final long queryCount(String sql, Object... parameters) {
		return queryOne(sql, (row) -> row.getLong(1), parameters).orElse(0L);
	}

	/** Runs an {@code INSERT}, {@code UPDATE} or {@code DELETE} and returns the number of rows it changed. */
	protected final int execute(String sql, Object... parameters) {
		Connection connection = DataSourceUtils.getConnection(this.dataSource);
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			bind(statement, parameters);
			return statement.executeUpdate();
		}
		catch (SQLException ex) {
			throw translate(sql, ex);
		}
		finally {
			DataSourceUtils.releaseConnection(connection, this.dataSource);
		}
	}

	/**
	 * Runs one page of a {@code SELECT} and the {@code COUNT} that gives the total. {@code select} has no {@code LIMIT};
	 * this adds {@code LIMIT ? OFFSET ?} from the page request. {@code parameters} are for both statements, so the two must
	 * have the same {@code WHERE}.
	 */
	protected final <T> Page<T> queryPage(String select, String count, RowMapper<T> mapper, Pageable pageable,
			Object... parameters) {
		if (pageable.isUnpaged()) {
			List<T> all = queryList(select, mapper, parameters);
			return new PageImpl<>(all, pageable, all.size());
		}
		Object[] withPaging = new Object[parameters.length + 2];
		System.arraycopy(parameters, 0, withPaging, 0, parameters.length);
		withPaging[parameters.length] = pageable.getPageSize();
		withPaging[parameters.length + 1] = pageable.getOffset();
		List<T> content = queryList(select + "\nLIMIT ? OFFSET ?", mapper, withPaging);
		return new PageImpl<>(content, pageable, queryCount(count, parameters));
	}

	/**
	 * The {@code ORDER BY} clause for a page request. The column names come from {@code columns}, the adapter's own map
	 * from a sortable property to a column, and never from the request, so a caller cannot put SQL into it. The key
	 * ({@code id}) is always allowed and is added last when the sort does not name it, so rows with equal values keep one
	 * order from page to page.
	 */
	protected static String orderBy(Sort sort, Map<String, String> columns) {
		StringBuilder clause = new StringBuilder("\nORDER BY ");
		boolean descending = false;
		boolean first = true;
		boolean sortsById = false;
		for (Sort.Order order : sort) {
			String column = "id".equals(order.getProperty()) ? "id" : columns.get(order.getProperty());
			if (column == null) {
				throw new IllegalArgumentException("Cannot sort by " + order.getProperty());
			}
			if (first) {
				descending = order.isDescending();
			}
			sortsById = sortsById || "id".equals(column);
			clause.append(first ? "" : ", ").append(column).append(order.isDescending() ? " DESC" : " ASC");
			first = false;
		}
		if (!sortsById) {
			clause.append(first ? "" : ", ").append("id").append(descending ? " DESC" : " ASC");
		}
		return clause.toString();
	}

	/**
	 * {@code "?, ?, ?"} for an {@code IN (...)} list of that many values. Only the number of values is put into the SQL
	 * text; the values themselves are still bound as parameters.
	 */
	protected static String placeholders(int count) {
		if (count < 1) {
			throw new IllegalArgumentException("An IN list needs at least one value");
		}
		return String.join(", ", java.util.Collections.nCopies(count, "?"));
	}

	private static void bind(PreparedStatement statement, Object[] parameters) throws SQLException {
		for (int i = 0; i < parameters.length; i++) {
			statement.setObject(i + 1, toJdbc(parameters[i]));
		}
	}

	private static Object toJdbc(Object value) {
		if (value instanceof UUID id) {
			return UuidBytes.toBytes(id);
		}
		if (value instanceof Instant instant) {
			return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
		}
		if (value instanceof Enum<?> constant) {
			return constant.name();
		}
		return value;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Reading columns

	protected static UUID uuid(ResultSet row, String column) throws SQLException {
		return UuidBytes.fromBytes(row.getBytes(column));
	}

	/** A nullable {@code BINARY(16)} column. */
	protected static UUID uuidOrNull(ResultSet row, String column) throws SQLException {
		byte[] bytes = row.getBytes(column);
		return (bytes == null) ? null : UuidBytes.fromBytes(bytes);
	}

	protected static Instant instant(ResultSet row, String column) throws SQLException {
		return row.getObject(column, LocalDateTime.class).toInstant(ZoneOffset.UTC);
	}

	/** A nullable {@code DATETIME(6)} column. */
	protected static Instant instantOrNull(ResultSet row, String column) throws SQLException {
		LocalDateTime stored = row.getObject(column, LocalDateTime.class);
		return (stored == null) ? null : stored.toInstant(ZoneOffset.UTC);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// What every table needs

	/**
	 * The tenant to filter reads by. With none bound it is the tenant nothing is stored under, so a read finds nothing,
	 * as Hibernate's tenant filter did.
	 */
	protected static UUID tenant() {
		return TenantContext.current().orElse(TenantContext.NO_TENANT);
	}

	/** The tenant to write rows under. A write with no tenant bound is refused, as the entity listener refused it. */
	protected static UUID tenantForWrite() {
		return TenantContext.current().orElseThrow(() -> new IllegalStateException(
				"Refusing to write: no tenant is bound to this thread"));
	}

	/** The signed-in user, or the system actor for work no user started. Recorded in {@code created_by/updated_by}. */
	protected static UUID actor() {
		return PersistenceConfiguration.currentActor();
	}

	/** The time to record in {@code created_at/updated_at}, to the microsecond a {@code DATETIME(6)} holds. */
	protected static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	/**
	 * A new UUIDv7 key. Each key sorts after the one before it, also within the same millisecond (RFC 9562, method 1: a
	 * 12-bit counter in {@code rand_a}), so ordering rows by {@code id} gives the order they were written in.
	 */
	protected static UUID newId() {
		long millis = System.currentTimeMillis();
		long stamp = LAST_STAMP.updateAndGet((previous) -> Math.max(millis << 12, previous + 1));
		long msb = ((stamp >>> 12) << 16) | 0x7000L | (stamp & 0xFFFL);
		long lsb = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
		return new UUID(msb, lsb);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Errors

	/**
	 * After {@code UPDATE ... WHERE id = ? AND version = ?}: if no row changed, someone else changed or deleted the row
	 * first, which the API answers with 409 (ADR 0043).
	 */
	protected static void requireOneRow(int rowsChanged, String entity, UUID id) {
		if (rowsChanged != 1) {
			throw staleVersion(entity, id);
		}
	}

	/** The failure for a change made on a row that someone else has changed since it was read (ADR 0043). */
	protected static ObjectOptimisticLockingFailureException staleVersion(String entity, UUID id) {
		return new ObjectOptimisticLockingFailureException(entity, id);
	}

	/**
	 * Turns a failed statement into the exception the use cases and the API expect:
	 * <ul>
	 * <li>a broken integrity constraint (duplicate key 1062, missing value, broken reference, failed check) becomes a
	 * {@link DataIntegrityViolationException}, as it was with JPA;</li>
	 * <li>a deadlock or lock wait timeout becomes a {@link CannotAcquireLockException}, which can be tried again;</li>
	 * <li>anything else becomes a {@link JdbcAccessException}.</li>
	 * </ul>
	 * The original {@link SQLException} stays attached as the cause, because the API looks for MySQL's error number in the
	 * chain. The statement text is in the message; values are not, because they may be personal.
	 */
	protected static DataAccessException translate(String sql, SQLException ex) {
		String message = "Statement failed: " + sql.strip().lines().findFirst().orElse("") + " (" + ex.getMessage() + ")";
		int code = ex.getErrorCode();
		if (ex instanceof SQLIntegrityConstraintViolationException || code == MYSQL_CHECK_CONSTRAINT_VIOLATED) {
			return new DataIntegrityViolationException(message, ex);
		}
		if (code == MYSQL_DEADLOCK || code == MYSQL_LOCK_WAIT_TIMEOUT) {
			return new CannotAcquireLockException(message, ex);
		}
		return new JdbcAccessException(message, ex);
	}

}
