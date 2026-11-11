package com.kibootia.backend.platform.authorization;

import java.util.UUID;

/**
 * Says whether a place a role can be assigned in exists (Timetia ADR 0004). The module that owns the data, for
 * example departments, divisions and batches, implements it. Without an implementation no scoped role can be assigned,
 * so nothing is silently unvalidated.
 */
public interface ScopeDirectory {

	/** Whether there is a live place of this kind with this id in the current tenant. */
	boolean exists(String kind, UUID id);

}
