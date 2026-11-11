package com.kibootia.backend.access.application;

import java.util.Set;

/** What an administrator supplies to create a role. */
public record NewRole(String name, String description, Set<String> permissions) {

}
