package com.kibootia.backend.identity;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.kibootia.backend.AdminApiTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uploads use the {@code uploads} category of the rate limiter built in milestone 3: past a user's allowance an upload is
 * 429 with {@code Retry-After}, nothing is stored for it, and another user is not affected. Viewing is not limited.
 */
@SpringBootTest(properties = { "kibootia.identity.bootstrap-admin-email=" + AdminApiTest.ADMIN_EMAIL,
		"kibootia.identity.bootstrap-invite.enabled=false",
		"kibootia.rate-limit.limits.uploads.capacity=2", "kibootia.rate-limit.limits.uploads.period=PT1H",
		"kibootia.rate-limit.limits.admin-actions.capacity=100000", "kibootia.events.initial-backoff=PT0S",
		"kibootia.events.max-backoff=PT0S" })
class AvatarRateLimitIT extends AdminApiTest {

	private ResultActions upload(java.util.UUID who) throws Exception {
		BufferedImage picture = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream png = new ByteArrayOutputStream();
		ImageIO.write(picture, "png", png);
		return this.mvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/me/avatar")
			.file(new MockMultipartFile("file", "a.png", "image/png", png.toByteArray()))
			.with(user(who)));
	}

	@Test
	@DisplayName("the third upload within the allowance is 429 with Retry-After and stores nothing, and another user is not affected")
	void uploadsAreLimited() throws Exception {
		signIn(ALICE);
		signIn(BOB);

		upload(ALICE).andExpect(status().isOk());
		upload(ALICE).andExpect(status().isOk());
		int stored = this.objectStorage.count();
		upload(ALICE).andExpect(status().isTooManyRequests())
			.andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("[1-9][0-9]*")))
			.andExpect(jsonPath("$.code").value("platform.rate_limited"));

		assertThat(this.objectStorage.count()).as("a refused upload stores nothing").isEqualTo(stored);
		get(user(ALICE), "/api/v1/users/" + ALICE + "/avatar").andExpect(status().isOk());
		upload(BOB).andExpect(status().isOk());
	}

	@Test
	@DisplayName("an upload that is refused for who is asking uses up nobody's allowance")
	void deniedUploadsAreNotCounted() throws Exception {
		this.keycloak.register(CAROL, true, false);
		get(unverified(CAROL), "/api/v1/me").andExpect(status().isOk());

		for (int i = 0; i < 5; i++) {
			BufferedImage picture = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
			ByteArrayOutputStream png = new ByteArrayOutputStream();
			ImageIO.write(picture, "png", png);
			this.mvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/me/avatar")
				.file(new MockMultipartFile("file", "a.png", "image/png", png.toByteArray()))
				.with(unverified(CAROL))).andExpect(status().isForbidden());
		}
	}

}
