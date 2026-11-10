package com.kibootia.backend.notes.web;

import com.kibootia.backend.notes.application.NewNote;
import com.kibootia.backend.notes.application.Note;
import com.kibootia.backend.notes.application.NoteChanges;

/** Converts between API types and application types by hand (ADR 0005). */
final class NoteMapper {

	private NoteMapper() {
	}

	static NewNote toNewNote(CreateNoteRequest request) {
		return new NewNote(request.title(), request.body());
	}

	static NoteChanges toChanges(UpdateNoteRequest request) {
		return new NoteChanges(request.title(), request.body(), request.version());
	}

	static NoteResponse toResponse(Note note) {
		return new NoteResponse(note.id(), note.ownerId(), note.title(), note.body(), note.version(), note.createdAt(),
				note.updatedAt(), note.attachmentFileId());
	}

}
