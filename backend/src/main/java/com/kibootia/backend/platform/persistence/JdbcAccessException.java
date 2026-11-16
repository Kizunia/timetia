package com.kibootia.backend.platform.persistence;

import org.springframework.dao.NonTransientDataAccessException;

/**
 * A SQL statement failed for a reason the rest of the code has no special answer for: the database is unreachable, the
 * statement is wrong, a column does not exist. Duplicate keys, other broken constraints and lock failures are not this
 * exception; {@link JdbcSupport} turns those into the Spring exceptions the use cases and the API already handle. The
 * {@link java.sql.SQLException} is always attached as the cause.
 */
public class JdbcAccessException extends NonTransientDataAccessException {

	public JdbcAccessException(String message, Throwable cause) {
		super(message, cause);
	}

}
