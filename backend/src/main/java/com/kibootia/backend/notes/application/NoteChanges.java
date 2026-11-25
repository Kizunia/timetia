package com.kibootia.backend.notes.application;

/**
 * The data to change a note. {@code expectedVersion} is the version the caller last saw: if someone saved in between,
 * the change is refused rather than overwriting theirs.
 */
public record NoteChanges(String title, String body, long expectedVersion) {

}
