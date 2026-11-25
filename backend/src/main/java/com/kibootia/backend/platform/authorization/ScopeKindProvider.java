package com.kibootia.backend.platform.authorization;

import java.util.Set;

/**
 * Implemented by a module as a Spring bean to declare the kinds of place a role can be assigned in (Timetia ADR 0004),
 * for example department, division and batch. {@link GrantedScope#GLOBAL} always exists and is not declared. The platform
 * and the {@code access} module only check that a role's scope type is a kind somebody declared.
 */
public interface ScopeKindProvider {

	/** Upper-case names, letters and underscores only. */
	Set<String> kinds();

}
