package com.kibootia.backend.access.domain;

import java.util.Set;

import com.kibootia.backend.platform.authorization.Caller;

/**
 * Where a caller's permissions come from: the roles stored in the database, read by one indexed query per call so a
 * change applies on the next request on any instance (ADR 0044).
 */
public interface PermissionSource {

	Set<String> permissionsOf(Caller caller);

}
