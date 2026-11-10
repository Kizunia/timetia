package com.kibootia.backend.notes;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.kibootia.backend.AdminApiTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A note's picture uses the {@code uploads} category of the rate limiter, like an avatar: past a user's allowance an
 * upload is 429 with {@code Retry-After}, nothing is stored for it, and another user is not affected. A caller who is
 * refused for who they are spends none of their allowance, because the limit is applied after the permission check.
 */
@SpringBootTest(properties = { "kibootia.identity.bootstrap-admin-email=" + AdminApiTest.ADMIN_EMAIL,
		"kibootia.rate-limit.limits.uploads.capacity=2", "kibootia.rate-limit.limits.uploads.period=PT1H",
		"kibootia.rate-limit.limits.admin-actions.capacity=100000", "kibootia.events.initial-backoff=PT0S",
		"kibootia.events.max-backoff=PT0S" })
class NoteAttachmentRateLimitIT extends AdminApiTest {

	@BeforeEach
	@AfterEach
	void cleanNotes() {
		this.jdbc.update("delete from note");
	}

	private String noteOf(UUID owner) throws Exception {
		signIn(owner);
		return read(post(user(owner), "/api/v1/notes", "{\"title\":\"Note " + UUID.randomUUID() + "\",\"body\":\"b\"}")
			.andExpect(status().isCreated()), "$.id");
	}

	private ResultActions attach(RequestPostProcessor who, String note) throws Exception {
		BufferedImage picture = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream png = new ByteArrayOutputStream();
		ImageIO.write(picture, "png", png);
		return this.mvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/notes/" + note + "/attachment")
			.file(new MockMultipartFile("file", "a.png", "image/png", png.toByteArray()))
			.with(who));
	}

	@Test
	@DisplayName("the third upload within the allowance is 429 with Retry-After and stores nothing, and another user is not affected")
	void uploadsAreLimited() throws Exception {
		String alices = noteOf(ALICE);
		String bobs = noteOf(BOB);

		attach(user(ALICE), alices).andExpect(status().isOk());
		attach(user(ALICE), alices).andExpect(status().isOk());
		int stored = this.objectStorage.count();
		attach(user(ALICE), alices).andExpect(status().isTooManyRequests())
			.andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("[1-9][0-9]*")))
			.andExpect(jsonPath("$.code").value("platform.rate_limited"));

		assertThat(this.objectStorage.count()).as("a refused upload stores nothing").isEqualTo(stored);
		get(user(ALICE), "/api/v1/notes/" + alices + "/attachment").andExpect(status().isOk());
		attach(user(BOB), bobs).andExpect(status().isOk());
	}

	/**
	 * The limiter keeps its state for the whole run, per user, so each test uses users of its own. This one uses Carol, who
	 * may see anyone's notes but change none, and Dave's note as the one she tries.
	 */
	@Test
	@DisplayName("an upload that is refused for who is asking uses up nobody's allowance")
	void deniedUploadsAreNotCounted() throws Exception {
		UUID dave = UUID.fromString("44444444-4444-7444-8444-444444444444");
		String daves = noteOf(dave);
		ensureAdmin();
		signIn(CAROL);
		setRoles(CAROL, builtInRoleId("USER"), createRole("Viewers", "notes.notes.view-any"));

		for (int i = 0; i < 5; i++) {
			attach(user(CAROL), daves).andExpect(status().isForbidden());
		}
		// Carol still has her whole allowance (two) for a note she may change.
		String carols = noteOf(CAROL);
		attach(user(CAROL), carols).andExpect(status().isOk());
		attach(user(CAROL), carols).andExpect(status().isOk());
	}

}
