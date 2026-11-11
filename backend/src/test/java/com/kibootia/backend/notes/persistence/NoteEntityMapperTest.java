package com.kibootia.backend.notes.persistence;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.kibootia.backend.notes.application.Note;

import static org.assertj.core.api.Assertions.assertThat;

class NoteEntityMapperTest {

	static final UUID ID = UUID.fromString("11111111-1111-7111-8111-111111111111");

	static final UUID TENANT = UUID.fromString("00000000-0000-7000-8000-000000000001");

	static final UUID OWNER = UUID.fromString("22222222-2222-7222-8222-222222222222");

	@Test
	@DisplayName("an entity becomes a note with its owner, tenant, version and timestamps")
	void toModel() {
		Instant created = Instant.parse("2026-11-09T10:00:00Z");
		Instant updated = Instant.parse("2026-11-09T11:00:00Z");
		NoteEntity entity = NoteEntityMapper.toNewEntity(OWNER, "Title", "Body");
		ReflectionTestUtils.setField(entity, "id", ID);
		ReflectionTestUtils.setField(entity, "tenantId", TENANT);
		ReflectionTestUtils.setField(entity, "version", 4L);
		ReflectionTestUtils.setField(entity, "createdAt", created);
		ReflectionTestUtils.setField(entity, "updatedAt", updated);

		Note note = NoteEntityMapper.toModel(entity);

		assertThat(note).isEqualTo(new Note(ID, TENANT, OWNER, "Title", "Body", 4, created, updated, null));
	}

	@Test
	@DisplayName("the attachment of an entity reaches the note, and a new entity has none")
	void attachment() {
		UUID file = UUID.fromString("33333333-3333-7333-8333-333333333333");
		NoteEntity entity = NoteEntityMapper.toNewEntity(OWNER, "Title", "Body");
		assertThat(NoteEntityMapper.toModel(entity).attachmentFileId()).isNull();

		ReflectionTestUtils.setField(entity, "attachmentFileId", file);

		assertThat(NoteEntityMapper.toModel(entity).attachmentFileId()).isEqualTo(file);
	}

	@Test
	@DisplayName("a new entity carries the owner, title and body and no identity yet")
	void toNewEntity() {
		NoteEntity entity = NoteEntityMapper.toNewEntity(OWNER, "Title", "Body");

		assertThat(entity.getOwnerId()).isEqualTo(OWNER);
		assertThat(entity.getTitle()).isEqualTo("Title");
		assertThat(entity.getBody()).isEqualTo("Body");
		assertThat(entity.getId()).isNull();
	}

	@Test
	@DisplayName("changing an entity replaces the title and body only")
	void change() {
		NoteEntity entity = NoteEntityMapper.toNewEntity(OWNER, "Title", "Body");

		entity.change("New title", "New body");

		assertThat(entity.getTitle()).isEqualTo("New title");
		assertThat(entity.getBody()).isEqualTo("New body");
		assertThat(entity.getOwnerId()).isEqualTo(OWNER);
	}

}
