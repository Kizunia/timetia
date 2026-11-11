package com.kibootia.backend.notes;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.kibootia.backend.AdminApiTest;
import com.kibootia.backend.Await;
import com.kibootia.backend.platform.jobs.RetentionPurgeJob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The picture of a note through the real stack (ADR 0029, ADR 0051): attach, replace, view and remove, who may see and
 * change it, tenant isolation, what happens to it when the note is deleted, restored, purged or its owner is erased, and
 * what an upload may be. The storage server is the in-memory fake; {@code S3ObjectStorageContractIT} covers the real one.
 */
// The tests share one user and attach many pictures; the limit itself is tested in NoteAttachmentRateLimitIT.
@TestPropertySource(properties = "kibootia.rate-limit.limits.uploads.capacity=100000")
class NoteAttachmentApiIT extends AdminApiTest {

	private static final String TENANT_KEY = "00000000-0000-7000-8000-000000000001";

	@Autowired
	RetentionPurgeJob retention;

	@BeforeEach
	@AfterEach
	void cleanNotes() {
		this.jdbc.update("delete from note");
	}

	// --- fixtures ---------------------------------------------------------------------------------------------------

	private static byte[] image(String format, int width, int height) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(Color.RED);
		graphics.fillRect(0, 0, width / 2, height);
		graphics.setColor(Color.BLUE);
		graphics.fillRect(width / 2, 0, width - width / 2, height);
		graphics.dispose();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(image, format, bytes);
		return bytes.toByteArray();
	}

	private static BufferedImage decode(byte[] bytes) throws IOException {
		return ImageIO.read(new ByteArrayInputStream(bytes));
	}

	private ResultActions attach(RequestPostProcessor who, String noteId, byte[] content, String filename, String type)
			throws Exception {
		return this.mvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/notes/" + noteId + "/attachment")
			.file(new MockMultipartFile("file", filename, type, content))
			.with(who));
	}

	private ResultActions attach(RequestPostProcessor who, String noteId) throws Exception {
		return attach(who, noteId, image("png", 1200, 400), "wide.png", "image/png");
	}

	/** A note of the user, made through the API. */
	private String noteOf(UUID owner) throws Exception {
		signIn(owner);
		return read(post(user(owner), "/api/v1/notes",
				"{\"title\":\"Note " + UUID.randomUUID() + "\",\"body\":\"b\"}").andExpect(status().isCreated()), "$.id");
	}

	/** A note of the user with a picture, and the ID of the stored file. */
	private UUID attachedNoteOf(UUID owner, String[] noteId) throws Exception {
		noteId[0] = noteOf(owner);
		return UUID.fromString(read(attach(user(owner), noteId[0]).andExpect(status().isOk()), "$.attachmentVersion"));
	}

	private int liveFiles(UUID owner) {
		return count("select count(*) from stored_file where owner_id = unhex(?) and deleted_at is null", hex(owner));
	}

	private int allFiles(UUID owner) {
		return count("select count(*) from stored_file where owner_id = unhex(?)", hex(owner));
	}

	private static String key(UUID file, String rendition) {
		return TENANT_KEY + "/note-image/" + file + "/" + rendition;
	}

	/** Gives the user a role holding the permissions, on top of the built-in user role. */
	private void grant(UUID user, String name, String... permissions) throws Exception {
		ensureAdmin();
		signIn(user);
		setRoles(user, builtInRoleId("USER"), createRole(name, permissions));
	}

	// --- attach, view, replace, remove ------------------------------------------------------------------------------

	@Test
	@DisplayName("attaching an image keeps it whole in a preview and a full size, and the note says so")
	void attachAndView() throws Exception {
		String note = noteOf(ALICE);
		get(user(ALICE), "/api/v1/notes/" + note).andExpect(jsonPath("$.attachmentVersion").doesNotExist());

		var result = attach(user(ALICE), note).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(note))
			.andExpect(jsonPath("$.attachmentVersion").isNotEmpty())
			// Attaching a picture is not an edit of the text: the version stays, so an open editor can still save.
			.andExpect(jsonPath("$.version").value(0));
		UUID file = UUID.fromString(read(result, "$.attachmentVersion"));

		assertThat(this.objectStorage.keys()).containsExactly(key(file, "full"), key(file, "preview"));
		byte[] preview = get(user(ALICE), "/api/v1/notes/" + note + "/attachment").andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "image/png"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
			.andExpect(header().string("Cache-Control", "no-cache, private"))
			.andExpect(header().string("ETag", "\"" + file + "-preview\""))
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		byte[] full = get(user(ALICE), "/api/v1/notes/" + note + "/attachment?size=FULL").andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		// 1200x400 stays in proportion: scaled to 480 wide for the preview, and not enlarged or cropped for the full size.
		assertThat(decode(preview).getWidth()).isEqualTo(480);
		assertThat(decode(preview).getHeight()).isEqualTo(160);
		assertThat(decode(full).getWidth()).isEqualTo(1200);
		assertThat(decode(full).getHeight()).isEqualTo(400);
		get(user(ALICE), "/api/v1/notes/" + note).andExpect(jsonPath("$.attachmentVersion").value(file.toString()));
		get(user(ALICE), "/api/v1/notes").andExpect(jsonPath("$.items[0].attachmentVersion").value(file.toString()));
	}

	@Test
	@DisplayName("a copy the browser already holds is answered 304 with no body, and a stale one gets the image")
	void conditionalRequests() throws Exception {
		String[] note = new String[1];
		UUID file = attachedNoteOf(ALICE, note);
		String etag = "\"" + file + "-preview\"";

		this.mvc.perform(MockMvcRequestBuilders.get("/api/v1/notes/" + note[0] + "/attachment")
			.header("If-None-Match", etag)
			.with(user(ALICE))).andExpect(status().isNotModified()).andExpect(header().string("ETag", etag));
		this.mvc.perform(MockMvcRequestBuilders.get("/api/v1/notes/" + note[0] + "/attachment")
			.header("If-None-Match", "\"somebody-else\"")
			.with(user(ALICE))).andExpect(status().isOk());
	}

	@Test
	@DisplayName("replacing the picture hides the old file at once and leaves one live file")
	void replacing() throws Exception {
		String[] note = new String[1];
		UUID first = attachedNoteOf(ALICE, note);

		UUID second = UUID.fromString(read(attach(user(ALICE), note[0], image("jpeg", 600, 900), "tall.jpg", "image/jpeg")
			.andExpect(status().isOk()), "$.attachmentVersion"));

		assertThat(second).isNotEqualTo(first);
		assertThat(liveFiles(ALICE)).isEqualTo(1);
		assertThat(allFiles(ALICE)).isEqualTo(2);
		get(user(ALICE), "/api/v1/notes/" + note[0] + "/attachment").andExpect(header().string("ETag", "\"" + second + "-preview\""));
		BufferedImage tall = decode(get(user(ALICE), "/api/v1/notes/" + note[0] + "/attachment?size=FULL").andReturn()
			.getResponse().getContentAsByteArray());
		assertThat(tall.getHeight()).isGreaterThan(tall.getWidth());
	}

	@Test
	@DisplayName("removing the picture leaves the note without one, and removing it again does nothing")
	void removing() throws Exception {
		String[] note = new String[1];
		attachedNoteOf(ALICE, note);

		delete(user(ALICE), "/api/v1/notes/" + note[0] + "/attachment").andExpect(status().isNoContent());

		get(user(ALICE), "/api/v1/notes/" + note[0]).andExpect(jsonPath("$.attachmentVersion").doesNotExist());
		get(user(ALICE), "/api/v1/notes/" + note[0] + "/attachment").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("notes.attachment_not_found"));
		assertThat(liveFiles(ALICE)).isZero();
		delete(user(ALICE), "/api/v1/notes/" + note[0] + "/attachment").andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("editing the text does not lose a picture that was attached in the meantime")
	void editingKeepsThePicture() throws Exception {
		String[] note = new String[1];
		UUID file = attachedNoteOf(ALICE, note);

		put(user(ALICE), "/api/v1/notes/" + note[0], "{\"title\":\"Renamed\",\"body\":\"New\",\"version\":0}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.attachmentVersion").value(file.toString()));
		get(user(ALICE), "/api/v1/notes/" + note[0] + "/attachment").andExpect(status().isOk());
	}

	// --- what an upload may be --------------------------------------------------------------------------------------

	@Test
	@DisplayName("a script, an SVG or a GIF is 415, whatever it is named")
	void onlyImages() throws Exception {
		String note = noteOf(ALICE);
		byte[] script = "<?php echo 1;".getBytes(StandardCharsets.US_ASCII);
		byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);
		byte[] gif = "GIF89a\u0001\u0000\u0001\u0000\u0000\u0000\u0000;".getBytes(StandardCharsets.ISO_8859_1);

		for (byte[] content : List.of(script, svg, gif)) {
			attach(user(ALICE), note, content, "picture.png", "image/png").andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("files.unsupported_type"));
		}
		assertThat(allFiles(ALICE)).isZero();
	}

	@Test
	@DisplayName("a file over the 5 MB limit is 413 and a damaged or empty image is 400, and nothing is stored for any of them")
	void sizeAndDamage() throws Exception {
		String note = noteOf(ALICE);
		byte[] big = new byte[5 * 1024 * 1024 + 1];
		byte[] valid = image("png", 120, 90);
		System.arraycopy(valid, 0, big, 0, 64);
		byte[] damaged = java.util.Arrays.copyOf(valid, valid.length / 2);

		attach(user(ALICE), note, big, "big.png", "image/png").andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.code").value("files.too_large"));
		attach(user(ALICE), note, damaged, "damaged.png", "image/png").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("files.invalid_image"));
		attach(user(ALICE), note, new byte[0], "empty.png", "image/png").andExpect(status().isBadRequest());

		assertThat(allFiles(ALICE)).isZero();
		assertThat(this.objectStorage.count()).isZero();
		get(user(ALICE), "/api/v1/notes/" + note).andExpect(jsonPath("$.attachmentVersion").doesNotExist());
	}

	@Test
	@DisplayName("an unknown size or note ID is 400")
	void badRequests() throws Exception {
		String[] note = new String[1];
		attachedNoteOf(ALICE, note);

		get(user(ALICE), "/api/v1/notes/" + note[0] + "/attachment?size=HUGE").andExpect(status().isBadRequest());
		get(user(ALICE), "/api/v1/notes/not-an-id/attachment").andExpect(status().isBadRequest());
	}

	// --- who may see and change it ----------------------------------------------------------------------------------

	@Test
	@DisplayName("someone who may not see the note gets 404 for every operation, so its existence is not revealed")
	void strangersGetNotFound() throws Exception {
		String[] note = new String[1];
		attachedNoteOf(ALICE, note);
		signIn(BOB);

		get(user(BOB), "/api/v1/notes/" + note[0] + "/attachment").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("notes.not_found"));
		attach(user(BOB), note[0]).andExpect(status().isNotFound());
		delete(user(BOB), "/api/v1/notes/" + note[0] + "/attachment").andExpect(status().isNotFound());
		get(user(BOB), "/api/v1/notes/" + UUID.randomUUID() + "/attachment").andExpect(status().isNotFound());
		assertThat(liveFiles(ALICE)).isEqualTo(1);
	}

	@Test
	@DisplayName("someone who may see anyone's notes may see their pictures but not change them")
	void viewersCannotChange() throws Exception {
		String[] note = new String[1];
		attachedNoteOf(ALICE, note);
		grant(BOB, "Viewers", "notes.notes.view-any");

		get(user(BOB), "/api/v1/notes/" + note[0] + "/attachment").andExpect(status().isOk());
		attach(user(BOB), note[0]).andExpect(status().isForbidden());
		delete(user(BOB), "/api/v1/notes/" + note[0] + "/attachment").andExpect(status().isForbidden());
		assertThat(liveFiles(ALICE)).isEqualTo(1);
	}

	@Test
	@DisplayName("someone who may change anyone's notes can attach a picture, and the file belongs to the note's owner")
	void editorsAttachForTheOwner() throws Exception {
		String note = noteOf(ALICE);
		grant(BOB, "Editors", "notes.notes.view-any", "notes.notes.update-any");

		attach(user(BOB), note).andExpect(status().isOk());

		assertThat(allFiles(ALICE)).isEqualTo(1);
		assertThat(allFiles(BOB)).isZero();
		get(user(ALICE), "/api/v1/notes/" + note + "/attachment").andExpect(status().isOk());
	}

	@Test
	@DisplayName("a caller whose email address is not verified holds no permissions, so the note is 404 to them and nothing changes")
	void unverifiedCaller() throws Exception {
		String[] note = new String[1];
		attachedNoteOf(ALICE, note);
		RequestPostProcessor unverifiedAlice = unverified(ALICE);

		get(unverifiedAlice, "/api/v1/notes/" + note[0] + "/attachment").andExpect(status().isNotFound());
		attach(unverifiedAlice, note[0]).andExpect(status().isNotFound());
		delete(unverifiedAlice, "/api/v1/notes/" + note[0] + "/attachment").andExpect(status().isNotFound());

		assertThat(liveFiles(ALICE)).isEqualTo(1);
		get(user(ALICE), "/api/v1/notes/" + note[0] + "/attachment").andExpect(status().isOk());
	}

	@Test
	@DisplayName("a note of another tenant is 404, even with its file in storage")
	void tenantIsolation() throws Exception {
		ensureAdmin();
		UUID stranger = UUID.fromString("44444444-4444-7444-8444-444444444444");
		UUID otherNote = UUID.randomUUID();
		UUID file = UUID.randomUUID();
		String otherTenant = "ffffffffffff7fff8fffffffffffffff";
		this.jdbc.update("""
				insert into note (id, tenant_id, version, created_at, created_by, updated_at, updated_by, owner_id, title, body,
				  attachment_file_id)
				values (unhex(?), unhex(?), 0, utc_timestamp(6), unhex(?), utc_timestamp(6), unhex(?), unhex(?), 'x', 'x', unhex(?))
				""", hex(otherNote), otherTenant, hex(stranger), hex(stranger), hex(stranger), hex(file));
		this.objectStorage.putAt("ffffffff-ffff-7fff-8fff-ffffffffffff/note-image/" + file + "/preview", new byte[] { 1 },
				java.time.Instant.now());

		get(admin(), "/api/v1/notes/" + otherNote + "/attachment").andExpect(status().isNotFound());
		attach(admin(), otherNote.toString()).andExpect(status().isNotFound());
		delete(admin(), "/api/v1/notes/" + otherNote + "/attachment").andExpect(status().isNotFound());
	}

	// --- the life of the note ---------------------------------------------------------------------------------------

	@Test
	@DisplayName("a deleted note hides its picture with it, and restoring the note brings the picture back")
	void deletedAndRestored() throws Exception {
		String[] note = new String[1];
		UUID file = attachedNoteOf(ALICE, note);
		ensureAdmin();

		delete(user(ALICE), "/api/v1/notes/" + note[0]).andExpect(status().isNoContent());
		get(user(ALICE), "/api/v1/notes/" + note[0] + "/attachment").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("notes.not_found"));
		attach(user(ALICE), note[0]).andExpect(status().isNotFound());
		assertThat(liveFiles(ALICE)).as("the file is kept while the note can be restored").isEqualTo(1);

		post(admin(), "/api/v1/notes/" + note[0] + "/restore", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.attachmentVersion").value(file.toString()));
		get(user(ALICE), "/api/v1/notes/" + note[0] + "/attachment").andExpect(status().isOk());
	}

	@Test
	@DisplayName("purging a deleted note removes its file through the event, and a note without a picture publishes nothing")
	void purgeRemovesTheFile() throws Exception {
		String[] withPicture = new String[1];
		UUID file = attachedNoteOf(ALICE, withPicture);
		String without = noteOf(ALICE);
		delete(user(ALICE), "/api/v1/notes/" + withPicture[0]).andExpect(status().isNoContent());
		delete(user(ALICE), "/api/v1/notes/" + without).andExpect(status().isNoContent());
		this.jdbc.update("update note set deleted_at = date_sub(utc_timestamp(6), interval 40 day) where owner_id = unhex(?)",
				hex(ALICE));

		this.retention.runOnce();

		assertThat(count("select count(*) from note where owner_id = unhex(?)", hex(ALICE))).isZero();
		Await.until("the file of the purged note to be discarded",
				() -> count("select count(*) from stored_file where id = unhex(?) and deleted_at is not null", hex(file)) == 1);
		assertThat(liveFiles(ALICE)).isZero();
	}

	@Test
	@DisplayName("erasing the owner removes the picture of their note, even when someone else attached it")
	void erasingTheOwnerRemovesThePictures() throws Exception {
		ensureAdmin();
		String note = noteOf(ALICE);
		attach(admin(), note).andExpect(status().isOk());
		assertThat(allFiles(ALICE)).isEqualTo(1);

		delete(admin(), "/api/v1/users/" + ALICE).andExpect(status().isNoContent());
		post(admin(), "/api/v1/users/" + ALICE + "/erase", null).andExpect(status().isAccepted());

		Await.until("the picture to be erased", () -> allFiles(ALICE) == 0 && this.objectStorage.count() == 0);
		assertThat(count("select count(*) from note where owner_id = unhex(?)", hex(ALICE))).isZero();
	}

}
