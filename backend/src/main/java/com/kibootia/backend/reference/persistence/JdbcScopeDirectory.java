package com.kibootia.backend.reference.persistence;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.academic.AcademicScopes;
import com.kibootia.backend.platform.authorization.ScopeDirectory;

/**
 * Answers which departments, divisions and batches exist, from the reference tables (Timetia ADR 0004, integration
 * contract). This is the provider the access module asks before a scoped role is given; with it present, a scoped role
 * can finally be assigned, and without the reference module nothing provides it and only global roles can be given.
 *
 * <p>Codes compare exactly, the same way every time: a code is matched as written, with no trimming or case change, so
 * the caller's rule "a code names at most one live place" is kept by the unique key on (tenant, code, active marker).
 * Every query is filtered to the current tenant and to live rows ({@code deleted_at IS NULL}); a deleted place is therefore
 * invisible and its code is free to name a new one. A kind this provider does not own (anything but the three academic
 * kinds) is answered as no place, never as an error.
 */
@Component
@Transactional(readOnly = true)
class JdbcScopeDirectory extends JdbcReferenceSupport implements ScopeDirectory {

	// Which live place has this code?
	private static final String ID_BY_CODE = "SELECT id FROM %s WHERE tenant_id = ? AND code = ? AND deleted_at IS NULL";

	// The codes of some live places. The first %s is the table and the second the list of ?.
	private static final String CODES_OF = "SELECT id, code FROM %s WHERE tenant_id = ? AND deleted_at IS NULL AND id IN (%s)";

	JdbcScopeDirectory(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public boolean exists(String kind, UUID id) {
		if (id == null) {
			return false;
		}
		String table = tableOf(kind);
		return table != null && liveRowExists(table, id);
	}

	@Override
	public Optional<UUID> idOf(String kind, String code) {
		if (code == null || code.isBlank()) {
			return Optional.empty();
		}
		String table = tableOf(kind);
		if (table == null) {
			return Optional.empty();
		}
		return queryOne(ID_BY_CODE.formatted(table), (row) -> uuid(row, "id"), tenant(), code);
	}

	@Override
	public Map<UUID, String> codesOf(String kind, Collection<UUID> ids) {
		String table = tableOf(kind);
		if (ids == null || ids.isEmpty() || table == null) {
			return Map.of();
		}
		Map<UUID, String> codes = new HashMap<>();
		// A kind this provider does not own has no codes here; for the three it owns, one query names them all.
		queryAmong(CODES_OF.formatted(table, "%s"), (row) -> {
			codes.put(uuid(row, "id"), row.getString("code"));
			return null;
		}, ids);
		return codes;
	}

	/** The table of a kind of place, or null for a kind this provider does not own. */
	private static String tableOf(String kind) {
		return switch (kind) {
			case AcademicScopes.DEPARTMENT -> "department";
			case AcademicScopes.DIVISION -> "division";
			case AcademicScopes.BATCH -> "batch";
			default -> null;
		};
	}

}
