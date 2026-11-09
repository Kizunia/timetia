package com.kibootia.backend.notes.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The body of {@code POST /api/v1/notes}. The server validates; a client may check the same limits for usability. */
public record CreateNoteRequest(@NotBlank @Size(max = 120) String title, @NotNull @Size(max = 4000) String body) {

}
