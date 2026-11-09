package com.kibootia.backend.access.domain;

import java.util.Set;

import com.kibootia.backend.platform.authorization.Caller;

/**
 * Where a caller's permissions come from. Milestone 2 reads them from the built-in {@code user} role defined in code;
 * milestone 3 replaces this with the roles stored in the database, loaded by one indexed query per request (ADR 0044).
 */
public interface PermissionSource {

	Set<String> permissionsOf(Caller caller);

}
