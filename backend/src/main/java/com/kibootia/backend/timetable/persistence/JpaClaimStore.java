package com.kibootia.backend.timetable.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.kibootia.backend.timetable.application.ClaimStore;
import com.kibootia.backend.timetable.domain.ClaimKey;
import com.kibootia.backend.timetable.domain.ClaimKind;

/** Keeps what lectures hold in MySQL through JPA. */
@Component
class JpaClaimStore implements ClaimStore {

	private final OccurrenceClaimRepository claims;

	JpaClaimStore(OccurrenceClaimRepository claims) {
		this.claims = claims;
	}

	@Override
	public void insertAll(List<Claim> newClaims) {
		// Flushed now, so that a key already held is reported by this call and not at commit.
		this.claims.saveAll(newClaims.stream().map((claim) -> new OccurrenceClaimEntity(claim.occurrenceId(), claim.key())).toList());
		this.claims.flush();
	}

	@Override
	public void deleteOf(Collection<UUID> occurrenceIds) {
		if (!occurrenceIds.isEmpty()) {
			this.claims.deleteOf(occurrenceIds);
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
			for (OccurrenceClaimEntity claim : this.claims.find(resource.kind(), resource.id(), dates.get(resource),
					hours.get(resource))) {
				if (asked.contains(claim.key())) {
					holders.put(claim.key(), claim.getOccurrenceId());
				}
			}
		}
		return holders;
	}

	private record Resource(ClaimKind kind, UUID id) {

	}

}
