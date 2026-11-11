package com.kibootia.backend.notes.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code PUT /api/v1/notes/{id}}. {@code version} is the version the client last read: if the note changed
 * since, the update is refused with 409 instead of overwriting the other change.
 */
public record UpdateNoteRequest(@NotBlank @Size(max = 120) String title, @NotNull @Size(max = 4000) String body,
		@NotNull @PositiveOrZero Long version) {

}
