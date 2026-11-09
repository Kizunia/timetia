package com.kibootia.backend.notes.persistence;

import com.kibootia.backend.notes.application.Note;

/** Converts between the persistence entity and the application model by hand (ADR 0005). */
final class NoteEntityMapper {

	private NoteEntityMapper() {
	}

	static Note toModel(NoteEntity entity) {
		return new Note(entity.getId(), entity.getTenantId(), entity.getOwnerId(), entity.getTitle(), entity.getBody(),
				entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
	}

	static NoteEntity toNewEntity(java.util.UUID ownerId, String title, String body) {
		return new NoteEntity(ownerId, title, body);
	}

}
