package com.kibootia.backend.notes;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.kibootia.backend.AdminApiTest;
import com.kibootia.backend.platform.jobs.RetentionPurgeJob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With the files module switched off (ADR 0024) the notes work as before and only the picture says why it cannot: the
 * attachment endpoints answer 404 {@code files.disabled}, the profile says files are off, and purging a note that still
 * has a file from the time files were on does not fail.
 */
@TestPropertySource(properties = { "kibootia.files.enabled=false", "kibootia.files.access-key=",
		"kibootia.files.secret-key=" })
class NoteAttachmentFilesDisabledIT extends AdminApiTest {

	@Autowired
	RetentionPurgeJob retention;

	@BeforeEach
	@AfterEach
	void cleanNotes() {
		this.jdbc.update("delete from note");
	}

	@Test
	@DisplayName("notes work, the picture endpoints answer 404 files.disabled, and the profile says files are off")
	void picturesAreOff() throws Exception {
		signIn(ALICE);
		String note = read(post(user(ALICE), "/api/v1/notes", "{\"title\":\"No picture\",\"body\":\"b\"}")
			.andExpect(status().isCreated()), "$.id");
		BufferedImage picture = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream png = new ByteArrayOutputStream();
		ImageIO.write(picture, "png", png);

		get(user(ALICE), "/api/v1/me").andExpect(jsonPath("$.filesEnabled").value(false));
		this.mvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/notes/" + note + "/attachment")
			.file(new MockMultipartFile("file", "a.png", "image/png", png.toByteArray()))
			.with(user(ALICE))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("files.disabled"));
		delete(user(ALICE), "/api/v1/notes/" + note + "/attachment").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("files.disabled"));
		get(user(ALICE), "/api/v1/notes/" + note + "/attachment").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("files.disabled"));

		put(user(ALICE), "/api/v1/notes/" + note, "{\"title\":\"Still editable\",\"body\":\"b\",\"version\":0}")
			.andExpect(status().isOk());
		get(user(ALICE), "/api/v1/notes/" + note).andExpect(status().isOk());
	}

	@Test
	@DisplayName("purging a note that has a file from before the module was switched off succeeds and leaves the file alone")
	void purgeWithoutFiles() throws Exception {
		signIn(ALICE);
		String note = read(post(user(ALICE), "/api/v1/notes", "{\"title\":\"Old picture\",\"body\":\"b\"}")
			.andExpect(status().isCreated()), "$.id");
		this.jdbc.update("update note set attachment_file_id = unhex(?) where id = unhex(?)",
				hex(UUID.randomUUID()), note.replace("-", ""));
		delete(user(ALICE), "/api/v1/notes/" + note).andExpect(status().isNoContent());
		this.jdbc.update("update note set deleted_at = date_sub(utc_timestamp(6), interval 40 day)");

		this.retention.runOnce();

		assertThat(count("select count(*) from note")).isZero();
		// The event is handled without a storage module; if it failed it would be retried and the clean-up of the next
		// test would wait for it in vain.
	}

}
