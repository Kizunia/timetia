package com.kibootia.backend.reference.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * The body of a {@code PUT} that renames a department, division or batch. Only the name is editable; the code is fixed
 * so an assignment made by it keeps resolving. {@code version} is the version the client last read: if the row changed
 * since, the update is refused with 409 instead of overwriting the other change (ADR 0043).
 */
public record RenameRequest(@NotBlank @Size(max = 120) String name, @NotNull @PositiveOrZero Long version) {

}
