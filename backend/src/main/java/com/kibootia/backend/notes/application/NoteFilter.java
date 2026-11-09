package com.kibootia.backend.notes.application;

import java.util.UUID;

/**
 * The named filters of the list endpoint (ADR 0043).
 *
 * @param text only notes containing this text in the title or body
 * @param owner only this user's notes
 */
public record NoteFilter(String text, UUID owner) {

	public static NoteFilter none() {
		return new NoteFilter(null, null);
	}

}
