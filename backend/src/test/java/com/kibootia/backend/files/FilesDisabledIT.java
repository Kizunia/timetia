package com.kibootia.backend.files;

import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.kibootia.backend.AdminApiTest;
import com.kibootia.backend.files.application.FileService;
import com.kibootia.backend.files.application.ReconciliationJob;
import com.kibootia.backend.files.s3.S3ObjectStorage;
import com.kibootia.backend.platform.files.StoredFiles;
import com.kibootia.backend.platform.jobs.RetentionPurgeable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The files module can be switched off with {@code kibootia.files.enabled=false} (ADR 0024): the application starts with
 * no storage setting and none of the module's beans, everything else works, and the avatar endpoints say why they do not.
 * This is the module-removal check for {@code files}; the CI job that runs the same for every module comes with
 * milestone 5.
 */
@TestPropertySource(properties = { "kibootia.files.enabled=false", "kibootia.files.access-key=",
		"kibootia.files.secret-key=" })
class FilesDisabledIT extends AdminApiTest {

	@Autowired
	ApplicationContext context;

	@Test
	@DisplayName("starts with the module off and no storage keys, and none of its beans exist")
	void startsWithoutTheModule() {
		assertThat(this.context.getBeansOfType(StoredFiles.class)).isEmpty();
		assertThat(this.context.getBeansOfType(FileService.class)).isEmpty();
		assertThat(this.context.getBeansOfType(S3ObjectStorage.class)).isEmpty();
		assertThat(this.context.getBeansOfType(ReconciliationJob.class)).isEmpty();
		assertThat(this.context.getBeansOfType(RetentionPurgeable.class).values())
			.noneMatch((purgeable) -> purgeable.name().equals("deleted-files"));
	}

	@Test
	@DisplayName("the profile says avatars are off, and the avatar endpoints answer 404 files.disabled")
	void avatarEndpointsSayWhy() throws Exception {
		signIn(ALICE);
		BufferedImage picture = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream png = new ByteArrayOutputStream();
		ImageIO.write(picture, "png", png);

		get(user(ALICE), "/api/v1/me").andExpect(status().isOk())
			.andExpect(jsonPath("$.avatarsEnabled").value(false))
			.andExpect(jsonPath("$.filesEnabled").value(false));
		this.mvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/me/avatar")
			.file(new MockMultipartFile("file", "a.png", "image/png", png.toByteArray()))
			.with(user(ALICE))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("files.disabled"));
		delete(user(ALICE), "/api/v1/me/avatar").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("files.disabled"));
		get(user(ALICE), "/api/v1/users/" + ALICE + "/avatar").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("files.disabled"));
	}

	@Test
	@DisplayName("everything that is not about files still works")
	void restOfTheApplication() throws Exception {
		ensureAdmin();

		get(admin(), "/api/v1/users").andExpect(status().isOk());
		get(user(ALICE), "/api/v1/me").andExpect(status().isOk());
	}

}
