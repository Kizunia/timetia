package com.kibootia.backend.timetable.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.stereotype.Component;

import com.kibootia.backend.platform.persistence.JdbcSupport;
import com.kibootia.backend.timetable.application.ClaimStore;
import com.kibootia.backend.timetable.domain.ClaimKey;
import com.kibootia.backend.timetable.domain.ClaimKind;

/**
 * Keeps what lectures hold in MySQL with hand-written SQL over JDBC. The unique key on (tenant, kind, resource, date, hour)
 * is in the migration, and it is what refuses a second claim on the same slot: the insert fails with MySQL error 1062, which
 * {@link JdbcSupport} turns into a data integrity violation.
 */
@Component
class JdbcClaimStore extends JdbcSupport implements ClaimStore {

	/** How many lectures one DELETE names, so the statement stays far below MySQL's limit on parameters. */
	private static final int DELETE_CHUNK = 500;

	private static final String INSERT = """
			INSERT INTO occurrence_claim (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
			                              occurrence_id, claim_kind, resource_id, claim_date, cell_hour)
			VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""";

	// Release everything the named lectures hold. The list of ? is added where %s stands.
	private static final String DELETE_OF = """
			DELETE FROM occurrence_claim
			WHERE tenant_id = ? AND occurrence_id IN (%s)
			""";

	// Who holds this resource on these dates and hours? Dates and hours are matched separately, so the caller narrows the rows.
	private static final String FIND = """
			SELECT claim_kind, resource_id, claim_date, cell_hour, occurrence_id
			FROM occurrence_claim
			WHERE tenant_id = ? AND claim_kind = ? AND resource_id = ? AND claim_date IN (%s) AND cell_hour IN (%s)
			""";

	// Who holds any of these resources of one kind, on one date, in these hours: every room at once for the free-room search.
	private static final String FIND_AMONG = """
			SELECT resource_id, cell_hour, occurrence_id
			FROM occurrence_claim
			WHERE tenant_id = ? AND claim_kind = ? AND claim_date = ? AND resource_id IN (%s) AND cell_hour IN (%s)
			""";

	JdbcClaimStore(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public void insertAll(List<Claim> newClaims) {
		// Each insert is sent at once, so a key already held is reported by this call and not at commit.
		for (Claim claim : newClaims) {
			ClaimKey key = claim.key();
			Instant now = now();
			execute(INSERT, newId(), tenantForWrite(), now, actor(), now, actor(), claim.occurrenceId(), key.kind(),
					key.resourceId(), key.date(), key.hour());
		}
	}

	@Override
	public void deleteOf(Collection<UUID> occurrenceIds) {
		List<UUID> ids = new ArrayList<>(occurrenceIds);
		for (int from = 0; from < ids.size(); from += DELETE_CHUNK) {
			List<UUID> chunk = ids.subList(from, Math.min(from + DELETE_CHUNK, ids.size()));
			Object[] parameters = new Object[chunk.size() + 1];
			parameters[0] = tenant();
			for (int i = 0; i < chunk.size(); i++) {
				parameters[i + 1] = chunk.get(i);
			}
			execute(DELETE_OF.formatted(placeholders(chunk.size())), parameters);
		}
	}

	@Override
	public Map<ClaimKey, UUID> holders(Collection<ClaimKey> keys) {
		// One query per resource, for the dates and hours asked of it. The query matches dates and hours separately, so the
		// rows are narrowed to the exact keys here.
		Map<Resource, Set<LocalDate>> dates = new HashMap<>();
		Map<Resource, Set<Integer>> hours = new HashMap<>();
		for (ClaimKey key : keys) {
			Resource resource = new Resource(key.kind(), key.resourceId());
			dates.computeIfAbsent(resource, (r) -> new HashSet<>()).add(key.date());
			hours.computeIfAbsent(resource, (r) -> new HashSet<>()).add(key.hour());
		}
		Set<ClaimKey> asked = new HashSet<>(keys);
		Map<ClaimKey, UUID> holders = new HashMap<>();
		for (Resource resource : dates.keySet()) {
			List<LocalDate> askedDates = new ArrayList<>(dates.get(resource));
			List<Integer> askedHours = new ArrayList<>(hours.get(resource));
			List<Object> parameters = new ArrayList<>();
			parameters.add(tenant());
			parameters.add(resource.kind());
			parameters.add(resource.id());
			parameters.addAll(askedDates);
			parameters.addAll(askedHours);
			String sql = FIND.formatted(placeholders(askedDates.size()), placeholders(askedHours.size()));
			List<Held> found = queryList(sql, (row) -> new Held(
					new ClaimKey(ClaimKind.valueOf(row.getString("claim_kind")), uuid(row, "resource_id"),
							row.getObject("claim_date", LocalDate.class), row.getInt("cell_hour")),
					uuid(row, "occurrence_id")), parameters.toArray());
			for (Held held : found) {
				if (asked.contains(held.key())) {
					holders.put(held.key(), held.occurrenceId());
				}
			}
		}
		return holders;
	}

	@Override
	public Map<ClaimKey, UUID> holdersOf(ClaimKind kind, Collection<UUID> resourceIds, LocalDate date,
			Collection<Integer> hours) {
		if (resourceIds.isEmpty() || hours.isEmpty()) {
			return Map.of();
		}
		List<Object> parameters = new ArrayList<>();
		parameters.add(tenant());
		parameters.add(kind);
		parameters.add(date);
		parameters.addAll(resourceIds);
		parameters.addAll(hours);
		String sql = FIND_AMONG.formatted(placeholders(resourceIds.size()), placeholders(hours.size()));
		Map<ClaimKey, UUID> holders = new HashMap<>();
		queryList(sql, (row) -> {
			ClaimKey key = new ClaimKey(kind, uuid(row, "resource_id"), date, row.getInt("cell_hour"));
			holders.put(key, uuid(row, "occurrence_id"));
			return Boolean.TRUE;
		}, parameters.toArray());
		return holders;
	}

	private record Resource(ClaimKind kind, UUID id) {

	}

	private record Held(ClaimKey key, UUID occurrenceId) {

	}

}
