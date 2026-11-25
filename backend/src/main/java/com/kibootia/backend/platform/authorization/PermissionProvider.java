package com.kibootia.backend.platform.authorization;

import java.util.List;

/**
 * Implemented by a module as a Spring bean to declare its permissions. They are collected at startup into the
 * registry the role editor lists from; a malformed or duplicate name stops the application from starting.
 */
public interface PermissionProvider {

	List<Permission> permissions();

}
