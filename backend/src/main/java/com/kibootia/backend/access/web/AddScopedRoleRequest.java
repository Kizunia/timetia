package com.kibootia.backend.access.web;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code POST /api/v1/users/{id}/scoped-roles}: one more role for the user, in one place. It leaves every
 * other assignment alone, so an administrator's screen that is out of date cannot undo what an import added. A role that
 * applies everywhere takes neither field; every other names its place by {@code scopeId} or by {@code scopeCode}, never
 * both.
 */
public record AddScopedRoleRequest(@NotNull UUID roleId, UUID scopeId, @Size(max = 100) String scopeCode) {

}
