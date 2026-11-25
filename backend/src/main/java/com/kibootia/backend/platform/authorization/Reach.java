package com.kibootia.backend.platform.authorization;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * How far a caller's policy-decided permission reaches, for a list (Timetia ADR 0004): everywhere, or the ids of the
 * places the caller holds it, per kind. A query turns it into a condition on the module's own columns. Rights that
 * come from a relation to a record (my own tickets) are not in it; the module adds those from its own data.
 *
 * @param everywhere true when the caller holds the permission without a limit
 * @param ids the ids per scope kind; empty when {@code everywhere}
 */
public record Reach(boolean everywhere, Map<String, Set<UUID>> ids) {

	private static final Reach NOTHING = new Reach(false, Map.of());

	private static final Reach EVERYTHING = new Reach(true, Map.of());

	public Reach {
		Map<String, Set<UUID>> copy = new TreeMap<>();
		ids.forEach((kind, set) -> copy.put(kind, Set.copyOf(set)));
		ids = Map.copyOf(copy);
	}

	public static Reach nothing() {
		return NOTHING;
	}

	public static Reach everything() {
		return EVERYTHING;
	}

	/** The reach of a set of grants. */
	public static Reach of(List<GrantedScope> grants) {
		Map<String, Set<UUID>> ids = new TreeMap<>();
		for (GrantedScope grant : grants) {
			if (grant.isGlobal()) {
				return EVERYTHING;
			}
			ids.computeIfAbsent(grant.kind(), (kind) -> new LinkedHashSet<>()).add(grant.id());
		}
		return ids.isEmpty() ? NOTHING : new Reach(false, ids);
	}

	/** The ids held in the kind; empty when none. */
	public Set<UUID> idsOf(String kind) {
		return this.ids.getOrDefault(kind, Set.of());
	}

	/** True when the caller holds the permission nowhere. */
	public boolean isEmpty() {
		return !this.everywhere && this.ids.isEmpty();
	}

}
