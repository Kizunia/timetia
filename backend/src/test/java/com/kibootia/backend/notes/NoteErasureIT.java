package com.kibootia.backend.notes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kibootia.backend.AdminApiTest;
import com.kibootia.backend.Await;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Erasing a user removes the notes they own and no one else's (ADR 0034, ADR 0041). This is the notes module's part of
 * the {@code UserErased} event, so it lives with the module; the user administration tests do not know about notes.
 */
class NoteErasureIT extends AdminApiTest {

	private static final String BODY = "{\"title\":\"Mine\",\"body\":\"b\"}";

	@BeforeEach
	@AfterEach
	void cleanNotes() {
		this.jdbc.update("delete from note");
	}

	private int notesOf(java.util.UUID owner) {
		return count("select count(*) from note where owner_id = unhex(?)", hex(owner));
	}

	@Test
	@DisplayName("erasing a deleted user removes their notes and leaves other users' notes alone")
	void erasingAUserRemovesTheirNotes() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		signIn(BOB);
		post(user(ALICE), "/api/v1/notes", BODY).andExpect(status().isCreated());
		post(user(ALICE), "/api/v1/notes", "{\"title\":\"Also mine\",\"body\":\"b\"}").andExpect(status().isCreated());
		post(user(BOB), "/api/v1/notes", BODY).andExpect(status().isCreated());
		assertThat(notesOf(ALICE)).isEqualTo(2);

		delete(admin(), "/api/v1/users/" + ALICE).andExpect(status().isNoContent());
		post(admin(), "/api/v1/users/" + ALICE + "/erase", null).andExpect(status().isAccepted());

		Await.until("the notes to be erased", () -> notesOf(ALICE) == 0);
		assertThat(notesOf(BOB)).isEqualTo(1);
	}

	@Test
	@DisplayName("deleting a user without erasing them keeps their notes")
	void deletingAloneKeepsTheNotes() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		post(user(ALICE), "/api/v1/notes", BODY).andExpect(status().isCreated());

		delete(admin(), "/api/v1/users/" + ALICE).andExpect(status().isNoContent());

		assertThat(notesOf(ALICE)).isEqualTo(1);
	}

}
