package com.kibootia.backend.identity;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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
 * The avatar flow through the real stack (ADR 0029, ADR 0041): upload, replace, view and delete, who may see whose
 * avatar, tenant isolation, what an upload may be, and what happens when the storage server is down. The storage server
 * is the in-memory fake; {@code S3ObjectStorageContractIT} covers the real one.
 */
class AvatarApiIT extends AdminApiTest {

	private static final String TENANT = "00000000000070008000000000000001";

	// --- fixtures ---------------------------------------------------------------------------------------------------

	private static byte[] image(String format, int width, int height) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(Color.GREEN);
		graphics.fillRect(0, 0, width, height);
		graphics.dispose();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(image, format, bytes);
		return bytes.toByteArray();
	}

	private static byte[] png() throws IOException {
		return image("png", 120, 90);
	}

	private ResultActions upload(RequestPostProcessor who, byte[] content, String filename, String contentType) throws Exception {
		return this.mvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/me/avatar")
			.file(new MockMultipartFile("file", filename, contentType, content))
			.with(who));
	}

	private ResultActions upload(UUID who) throws Exception {
		return upload(user(who), png(), "me.png", "image/png");
	}

	private UUID uploaded(UUID who) throws Exception {
		signIn(who);
		return UUID.fromString(read(upload(who).andExpect(status().isOk()), "$.avatarVersion"));
	}

	private int liveFiles(UUID owner) {
		return count("select count(*) from stored_file where owner_id = unhex(?) and deleted_at is null", hex(owner));
	}

	private int allFiles(UUID owner) {
		return count("select count(*) from stored_file where owner_id = unhex(?)", hex(owner));
	}

	private static BufferedImage decode(byte[] bytes) throws IOException {
		return ImageIO.read(new ByteArrayInputStream(bytes));
	}

	// --- upload, replace, view, delete ------------------------------------------------------------------------------

	@Test
	@DisplayName("uploading an image makes it the avatar, kept as a small and a large PNG, and the profile says so")
	void uploadAndView() throws Exception {
		signIn(ALICE);
		get(user(ALICE), "/api/v1/me").andExpect(jsonPath("$.avatarVersion").doesNotExist())
			.andExpect(jsonPath("$.avatarsEnabled").value(true));

		var result = upload(ALICE).andExpect(status().isOk())
			.andExpect(jsonPath("$.avatarVersion").isNotEmpty())
			.andExpect(jsonPath("$.avatarsEnabled").value(true))
			.andExpect(jsonPath("$.id").value(ALICE.toString()));
		UUID file = UUID.fromString(read(result, "$.avatarVersion"));

		assertThat(this.objectStorage.keys()).containsExactly(key(file, "large"), key(file, "small"));
		byte[] large = get(user(ALICE), "/api/v1/users/" + ALICE + "/avatar?size=LARGE").andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "image/png"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("Cache-Control", "no-cache, private"))
			.andExpect(header().string("ETag", "\"" + file + "-large\""))
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		byte[] small = get(user(ALICE), "/api/v1/users/" + ALICE + "/avatar?size=SMALL").andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		assertThat(decode(large).getWidth()).isEqualTo(256);
		assertThat(decode(small).getWidth()).isEqualTo(64);
		// Without a size the large one is served.
		get(user(ALICE), "/api/v1/users/" + ALICE + "/avatar").andExpect(header().string("ETag", "\"" + file + "-large\""));
		get(user(ALICE), "/api/v1/me").andExpect(jsonPath("$.avatarVersion").value(file.toString()));
	}

	private static String key(UUID file, String rendition) {
		return "00000000-0000-7000-8000-000000000001/avatar/" + file + "/" + rendition;
	}

	@Test
	@DisplayName("a copy the browser already holds is answered 304 with no body, and a stale one gets the image")
	void conditionalRequests() throws Exception {
		UUID file = uploaded(ALICE);
		String etag = "\"" + file + "-small\"";

		var notModified = this.mvc.perform(MockMvcRequestBuilders.get("/api/v1/users/" + ALICE + "/avatar?size=SMALL")
			.header("If-None-Match", etag)
			.with(user(ALICE))).andExpect(status().isNotModified()).andExpect(header().string("ETag", etag));
		assertThat(notModified.andReturn().getResponse().getContentAsByteArray()).isEmpty();
		this.mvc.perform(MockMvcRequestBuilders.get("/api/v1/users/" + ALICE + "/avatar?size=SMALL")
			.header("If-None-Match", "\"someone-else-small\"")
			.with(user(ALICE))).andExpect(status().isOk());
		// The cached copy never lets someone through who is not allowed to see it.
		signIn(BOB);
		this.mvc.perform(MockMvcRequestBuilders.get("/api/v1/users/" + ALICE + "/avatar?size=SMALL")
			.header("If-None-Match", etag)
			.with(user(BOB))).andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("replacing the avatar hides the old file at once and keeps its objects until the retention purge")
	void replacing() throws Exception {
		UUID first = uploaded(ALICE);
		UUID second = UUID.fromString(read(upload(ALICE).andExpect(status().isOk()), "$.avatarVersion"));

		assertThat(second).isNotEqualTo(first);
		assertThat(liveFiles(ALICE)).isEqualTo(1);
		assertThat(allFiles(ALICE)).isEqualTo(2);
		assertThat(count("select count(*) from stored_file where id = unhex(?) and deleted_at is not null", hex(first))).isEqualTo(1);
		assertThat(this.objectStorage.contains(key(first, "large"))).as("kept for the retention period (ADR 0034)").isTrue();
		get(user(ALICE), "/api/v1/users/" + ALICE + "/avatar").andExpect(header().string("ETag", "\"" + second + "-large\""));
	}

	@Test
	@DisplayName("deleting the avatar removes it from the profile and hides the file, and deleting when there is none is fine")
	void deleting() throws Exception {
		uploaded(ALICE);

		delete(user(ALICE), "/api/v1/me/avatar").andExpect(status().isNoContent());

		assertThat(liveFiles(ALICE)).isZero();
		get(user(ALICE), "/api/v1/me").andExpect(jsonPath("$.avatarVersion").doesNotExist());
		get(user(ALICE), "/api/v1/users/" + ALICE + "/avatar").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("identity.avatar_not_found"));
		delete(user(ALICE), "/api/v1/me/avatar").andExpect(status().isNoContent());
	}

	// --- who may see whose avatar -----------------------------------------------------------------------------------

	@Test
	@DisplayName("others' avatars need identity.profiles.view; one's own never does, and a user with no avatar is 404")
	void visibility() throws Exception {
		ensureAdmin();
		UUID file = uploaded(ALICE);
		signIn(BOB);
		String path = "/api/v1/users/" + ALICE + "/avatar";

		get(user(ALICE), path).andExpect(status().isOk());
		get(user(BOB), path).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("platform.forbidden"));
		get(user(BOB), "/api/v1/users/" + BOB + "/avatar").andExpect(status().isNotFound());
		get(admin(), path).andExpect(status().isOk()).andExpect(header().string("ETag", "\"" + file + "-large\""));

		setRoles(BOB, builtInRoleId("USER"), createRole("Viewers", "identity.profiles.view"));
		get(user(BOB), path).andExpect(status().isOk());
		get(user(BOB), "/api/v1/users/" + UUID.randomUUID() + "/avatar").andExpect(status().isNotFound());
		get(admin(), "/api/v1/users/" + BOB + "/avatar").andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("a user who has not verified their address still sees their own avatar but cannot change it")
	void unverifiedUser() throws Exception {
		uploaded(ALICE);
		this.keycloak.register(BOB, true, false);
		get(unverified(BOB), "/api/v1/me").andExpect(status().isOk());

		get(unverified(ALICE), "/api/v1/users/" + ALICE + "/avatar").andExpect(status().isOk());
		upload(unverified(BOB), png(), "me.png", "image/png").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("platform.email_not_verified"));
		delete(unverified(ALICE), "/api/v1/me/avatar").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("platform.email_not_verified"));
		get(unverified(BOB), "/api/v1/users/" + ALICE + "/avatar").andExpect(status().isForbidden());
		assertThat(liveFiles(ALICE)).isEqualTo(1);
		assertThat(liveFiles(BOB)).isZero();
	}

	@Test
	@DisplayName("a profile or a file of another tenant is never served, even to an administrator")
	void tenantIsolation() throws Exception {
		ensureAdmin();
		UUID stranger = UUID.fromString("44444444-4444-7444-8444-444444444444");
		UUID file = UUID.fromString("55555555-5555-7555-8555-555555555555");
		String otherTenant = "ffffffffffff7fff8fffffffffffffff";
		this.jdbc.update("""
				insert into stored_file (id, tenant_id, version, created_at, created_by, updated_at, updated_by, owner_id,
				  category, content_type, renditions)
				values (unhex(?), unhex(?), 0, utc_timestamp(6), unhex(?), utc_timestamp(6), unhex(?), unhex(?), 'avatar',
				  'image/png', 'small,large')
				""", hex(file), otherTenant, hex(stranger), hex(stranger), hex(stranger));
		this.jdbc.update("""
				insert into user_profile (id, tenant_id, version, created_at, created_by, updated_at, updated_by, status,
				  email_verified, theme, name_synced_at, avatar_file_id)
				values (unhex(?), unhex(?), 0, utc_timestamp(6), unhex(?), utc_timestamp(6), unhex(?), 'ACTIVE', 1, 'SYSTEM',
				  utc_timestamp(6), unhex(?))
				""", hex(stranger), otherTenant, hex(stranger), hex(stranger), hex(file));
		this.objectStorage.putAt("ffffffff-ffff-7fff-8fff-ffffffffffff/avatar/" + file + "/large", new byte[] { 1 },
				java.time.Instant.now());

		get(admin(), "/api/v1/users/" + stranger + "/avatar").andExpect(status().isNotFound());
		// Someone who may look at profiles still finds nothing: the profile is in another tenant.
		signIn(BOB);
		setRoles(BOB, builtInRoleId("USER"), createRole("Viewers", "identity.profiles.view"));
		get(user(BOB), "/api/v1/users/" + stranger + "/avatar").andExpect(status().isNotFound());
		assertThat(this.objectStorage.count()).as("and nothing was read from storage for it").isEqualTo(1);
	}

	// --- what an upload may be --------------------------------------------------------------------------------------

	@Test
	@DisplayName("the type is found from the bytes: a script named .png is 415, and so are GIF and SVG")
	void typeFromTheBytes() throws Exception {
		signIn(ALICE);
		byte[] script = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
		byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\"/>".getBytes(StandardCharsets.UTF_8);
		byte[] gif = "GIF89a\u0001\u0000\u0001\u0000\u0000\u0000\u0000;".getBytes(StandardCharsets.ISO_8859_1);

		for (byte[] content : List.of(script, svg, gif)) {
			upload(user(ALICE), content, "avatar.png", "image/png").andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("files.unsupported_type"));
		}
		assertThat(allFiles(ALICE)).isZero();
		assertThat(this.objectStorage.count()).isZero();
	}

	@Test
	@DisplayName("a real image is accepted whatever the filename and the declared type say")
	void declaredTypeIsIgnored() throws Exception {
		signIn(ALICE);

		upload(user(ALICE), png(), "avatar.exe", "application/octet-stream").andExpect(status().isOk());
	}

	@Test
	@DisplayName("a file over the 5 MB limit is 413 and a damaged or empty image is 400, and nothing is stored for any of them")
	void sizeAndDamage() throws Exception {
		signIn(ALICE);
		byte[] big = new byte[5 * 1024 * 1024 + 1];
		byte[] pngHeader = png();
		System.arraycopy(pngHeader, 0, big, 0, 64);
		byte[] damaged = java.util.Arrays.copyOf(pngHeader, pngHeader.length / 2);

		upload(user(ALICE), big, "big.png", "image/png").andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.code").value("files.too_large"));
		upload(user(ALICE), damaged, "damaged.png", "image/png").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("files.invalid_image"));
		upload(user(ALICE), new byte[0], "empty.png", "image/png").andExpect(status().isBadRequest());
		this.mvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/me/avatar").with(user(ALICE)))
			.andExpect(status().isBadRequest());

		assertThat(allFiles(ALICE)).isZero();
		assertThat(this.objectStorage.count()).isZero();
		get(user(ALICE), "/api/v1/me").andExpect(jsonPath("$.avatarVersion").doesNotExist());
	}

	@Test
	@DisplayName("the stored renditions carry nothing from the upload: not its EXIF, its comment or its filename")
	void nothingOfTheOriginalIsStored() throws Exception {
		signIn(ALICE);
		byte[] jpeg = image("jpeg", 200, 200);
		byte[] comment = "COMMENT-SECRET-OWNER".getBytes(StandardCharsets.US_ASCII);
		ByteArrayOutputStream tagged = new ByteArrayOutputStream();
		tagged.write(jpeg, 0, 2);
		tagged.write(new byte[] { (byte) 0xFF, (byte) 0xFE, 0, (byte) (comment.length + 2) });
		tagged.write(comment);
		tagged.write(jpeg, 2, jpeg.length - 2);

		upload(user(ALICE), tagged.toByteArray(), "holiday-photo-of-my-house.jpg", "image/jpeg").andExpect(status().isOk());

		for (String key : this.objectStorage.keys()) {
			String text = new String(this.objectStorage.bytesOf(key), StandardCharsets.ISO_8859_1);
			assertThat(text).doesNotContain("SECRET").doesNotContain("holiday");
			assertThat(key).doesNotContain("holiday");
		}
	}

	// --- the storage server -----------------------------------------------------------------------------------------

	@Test
	@DisplayName("with the storage server down an upload and a view are 503 and leave nothing behind, and recovery needs no restart")
	void storageOutage() throws Exception {
		UUID file = uploaded(ALICE);
		this.objectStorage.setUnavailable(true);

		upload(user(ALICE), png(), "again.png", "image/png").andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("platform.service_unavailable"));
		get(user(ALICE), "/api/v1/users/" + ALICE + "/avatar").andExpect(status().isServiceUnavailable());
		assertThat(allFiles(ALICE)).as("the failed upload left no row").isEqualTo(1);
		get(user(ALICE), "/api/v1/me").andExpect(status().isOk())
			.andExpect(jsonPath("$.avatarVersion").value(file.toString()));

		this.objectStorage.setUnavailable(false);
		upload(user(ALICE), png(), "again.png", "image/png").andExpect(status().isOk());
		get(user(ALICE), "/api/v1/users/" + ALICE + "/avatar").andExpect(status().isOk());
		assertThat(this.objectStorage.bucketCalls()).isGreaterThanOrEqualTo(1);
	}

	@Test
	@DisplayName("two uploads at the same moment leave exactly one avatar, and every other file is hidden")
	void concurrentUploads() throws Exception {
		signIn(ALICE);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			for (int round = 0; round < 5; round++) {
				Callable<Integer> task = () -> upload(ALICE).andReturn().getResponse().getStatus();
				Future<Integer> a = pool.submit(task);
				Future<Integer> b = pool.submit(task);
				assertThat(a.get()).isEqualTo(200);
				assertThat(b.get()).isEqualTo(200);
			}
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(liveFiles(ALICE)).isEqualTo(1);
		assertThat(allFiles(ALICE)).isEqualTo(10);
		byte[] current = this.jdbc.queryForObject("select avatar_file_id from user_profile where id = unhex(?)", byte[].class,
				hex(ALICE));
		byte[] live = this.jdbc.queryForObject("select id from stored_file where owner_id = unhex(?) and deleted_at is null",
				byte[].class, hex(ALICE));
		assertThat(current).isEqualTo(live);
	}

	@Test
	@DisplayName("the avatar can be asked for as a size of its own and an unknown size is 400")
	void sizes() throws Exception {
		uploaded(ALICE);

		get(user(ALICE), "/api/v1/users/" + ALICE + "/avatar?size=HUGE").andExpect(status().isBadRequest());
		get(user(ALICE), "/api/v1/users/not-an-id/avatar").andExpect(status().isBadRequest());
		this.mvc.perform(MockMvcRequestBuilders.get("/api/v1/users/" + ALICE + "/avatar").with(user(ALICE))
			.accept(MediaType.IMAGE_PNG)).andExpect(status().isOk());
	}

}
