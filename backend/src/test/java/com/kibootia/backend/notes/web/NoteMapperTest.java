package com.kibootia.backend.notes.web;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kibootia.backend.notes.application.NewNote;
import com.kibootia.backend.notes.application.Note;
import com.kibootia.backend.notes.application.NoteChanges;

import static org.assertj.core.api.Assertions.assertThat;

class NoteMapperTest {

	static final UUID ID = UUID.fromString("11111111-1111-7111-8111-111111111111");

	static final UUID TENANT = UUID.fromString("00000000-0000-7000-8000-000000000001");

	static final UUID OWNER = UUID.fromString("22222222-2222-7222-8222-222222222222");

	@Test
	@DisplayName("a note becomes a response with every public field and without the tenant")
	void toResponse() {
		Instant created = Instant.parse("2026-11-09T10:00:00Z");
		Instant updated = Instant.parse("2026-11-09T11:00:00Z");
		Note note = new Note(ID, TENANT, OWNER, "Title", "Body", 3, created, updated, null);

		NoteResponse response = NoteMapper.toResponse(note);

		assertThat(response).isEqualTo(new NoteResponse(ID, OWNER, "Title", "Body", 3, created, updated, null));
		assertThat(NoteResponse.class.getRecordComponents()).extracting((c) -> c.getName()).doesNotContain("tenantId");
	}

	@Test
	@DisplayName("the stored file of a note is shown only as a version that changes with it, never as a file ID of its own")
	void attachmentVersion() {
		UUID file = UUID.fromString("33333333-3333-7333-8333-333333333333");
		Instant at = Instant.parse("2026-11-09T10:00:00Z");

		NoteResponse response = NoteMapper.toResponse(new Note(ID, TENANT, OWNER, "Title", "Body", 3, at, at, file));

		assertThat(response.attachmentVersion()).isEqualTo(file);
		assertThat(NoteResponse.class.getRecordComponents()).extracting((c) -> c.getName()).doesNotContain("attachmentFileId");
	}

	@Test
	@DisplayName("a create request becomes a new note")
	void toNewNote() {
		assertThat(NoteMapper.toNewNote(new CreateNoteRequest("Title", "Body"))).isEqualTo(new NewNote("Title", "Body"));
	}

	@Test
	@DisplayName("an update request becomes changes carrying the version the client saw")
	void toChanges() {
		assertThat(NoteMapper.toChanges(new UpdateNoteRequest("Title", "Body", 7L)))
			.isEqualTo(new NoteChanges("Title", "Body", 7));
	}

}
