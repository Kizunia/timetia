package com.kibootia.backend.platform.authorization;

import java.util.UUID;

/**
 * One place where a caller holds a permission through a role assignment (Timetia ADR 0004): a kind, such as
 * {@code DEPARTMENT}, and the id of the thing in that kind. The platform does not know what the kinds mean; the module
 * that declares them does. {@link #GLOBAL} has no id and means "everywhere".
 *
 * @param kind the scope kind, upper case
 * @param id the id within the kind; null only for {@link #GLOBAL}
 */
public record GrantedScope(String kind, UUID id) {

	/** The kind of an assignment that is not limited to a place. */
	public static final String GLOBAL = "GLOBAL";

	public GrantedScope {
		if (kind == null || kind.isBlank()) {
			throw new IllegalArgumentException("A scope needs a kind");
		}
		if (GLOBAL.equals(kind) != (id == null)) {
			throw new IllegalArgumentException("Only the global scope has no id");
		}
	}

	public static GrantedScope global() {
		return new GrantedScope(GLOBAL, null);
	}

	public static GrantedScope of(String kind, UUID id) {
		return new GrantedScope(kind, id);
	}

	public boolean isGlobal() {
		return GLOBAL.equals(this.kind);
	}

}
