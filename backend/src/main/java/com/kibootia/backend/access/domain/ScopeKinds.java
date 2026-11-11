package com.kibootia.backend.access.domain;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

import com.kibootia.backend.platform.authorization.GrantedScope;

/**
 * The kinds of place a role can be assigned in (Timetia ADR 0004): {@code GLOBAL} and the kinds modules declare. A
 * malformed name stops the application from starting.
 */
public final class ScopeKinds {

	private static final Pattern NAME = Pattern.compile("[A-Z][A-Z_]{0,18}[A-Z]");

	private final Set<String> kinds = new LinkedHashSet<>();

	public ScopeKinds(Collection<String> declared) {
		this.kinds.add(GrantedScope.GLOBAL);
		for (String kind : declared) {
			if (!NAME.matcher(kind).matches()) {
				throw new IllegalStateException("Scope kind '" + kind + "' must be upper-case letters and underscores");
			}
			this.kinds.add(kind);
		}
	}

	public boolean isKnown(String kind) {
		return this.kinds.contains(kind);
	}

	public Set<String> all() {
		return Set.copyOf(this.kinds);
	}

}
