package com.kibootia.backend.files.s3;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.kibootia.backend.files.application.FilesProperties;
import com.kibootia.backend.files.application.ObjectStorage;
import com.kibootia.backend.files.application.ObjectStorage.Listing;
import com.kibootia.backend.files.application.ObjectStorage.ObjectInfo;
import com.kibootia.backend.files.application.ObjectStorage.StoredObject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The storage contract against the S3 API of the pinned RustFS (ADR 0029). This is the verification gate of OQ-40: the
 * operations the module needs, including presigned PUT and GET, must work on the local default server, or SeaweedFS
 * becomes the default. Because the module talks to the S3 API only, running these same tests against another server is a
 * change of the container below.
 *
 * <p>The credentials are generated for each run in the shape {@code pnpm bootstrap} uses (43 URL-safe characters, which
 * can contain {@code -} and {@code _}), so a value that a server mishandles shows up here and not on a developer's
 * machine, and no secret-looking literal is committed.
 */
@Testcontainers
class S3ObjectStorageContractIT {

	private static final String ACCESS_KEY = generatedKey();

	private static final String SECRET_KEY = generatedKey();

	/** Made the way {@code pnpm bootstrap} makes a secret: 32 random bytes as 43 URL-safe characters. */
	private static String generatedKey() {
		byte[] bytes = new byte[32];
		new java.security.SecureRandom().nextBytes(bytes);
		return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static final HttpClient HTTP = HttpClient.newHttpClient();

	@Container
	static final GenericContainer<?> RUSTFS = new GenericContainer<>("rustfs/rustfs:1.0.4")
		.withEnv("RUSTFS_ACCESS_KEY", ACCESS_KEY)
		.withEnv("RUSTFS_SECRET_KEY", SECRET_KEY)
		.withEnv("RUSTFS_ADDRESS", ":9000")
		.withEnv("RUSTFS_CONSOLE_ENABLE", "false")
		.withCommand("/data")
		.withExposedPorts(9000)
		.waitingFor(Wait.forHttp("/health").forPort(9000).withStartupTimeout(Duration.ofMinutes(2)));

	static S3ObjectStorage storage;

	@BeforeAll
	static void connect() {
		FilesProperties properties = new FilesProperties(true, "http://localhost:" + RUSTFS.getMappedPort(9000),
				"us-east-1", "contract-" + UUID.randomUUID().toString().substring(0, 8), ACCESS_KEY, SECRET_KEY, true,
				null, null, "0 0 4 * * *", 500);
		storage = new S3ObjectStorage(properties);
		storage.ensureBucket();
	}

	private static String key(String name) {
		return "tenant/avatar/" + UUID.randomUUID() + "/" + name;
	}

	@Test
	@DisplayName("creating the bucket again changes nothing")
	void ensureBucketIsRepeatable() {
		storage.ensureBucket();
		storage.ensureBucket();
	}

	@Test
	@DisplayName("stores an object and reads back the same bytes, type and length")
	void putAndGet() throws IOException {
		String key = key("large");
		byte[] content = new byte[] { 1, 2, 3, 4, 5, (byte) 0xff, 0 };

		storage.put(key, content, "image/png");

		try (StoredObject object = read(key)) {
			assertThat(object.content().readAllBytes()).isEqualTo(content);
			assertThat(object.length()).isEqualTo(content.length);
			assertThat(object.contentType()).isEqualTo("image/png");
		}
	}

	@Test
	@DisplayName("stores an empty object and a larger one")
	void emptyAndLarge() throws IOException {
		String empty = key("empty");
		String large = key("large");
		byte[] bytes = new byte[2 * 1024 * 1024];
		new java.util.Random(7).nextBytes(bytes);

		storage.put(empty, new byte[0], "application/octet-stream");
		storage.put(large, bytes, "application/octet-stream");

		try (StoredObject object = read(empty)) {
			assertThat(object.content().readAllBytes()).isEmpty();
		}
		try (StoredObject object = read(large)) {
			assertThat(object.content().readAllBytes()).isEqualTo(bytes);
		}
	}

	@Test
	@DisplayName("replaces an object when the same key is stored again")
	void overwrite() throws IOException {
		String key = key("small");
		storage.put(key, new byte[] { 1 }, "image/png");
		storage.put(key, new byte[] { 2, 2 }, "image/png");

		try (StoredObject object = read(key)) {
			assertThat(object.content().readAllBytes()).containsExactly(2, 2);
		}
	}

	@Test
	@DisplayName("a missing object is empty, not an error")
	void missing() {
		assertThat(storage.get(key("never-stored"))).isEmpty();
	}

	@Test
	@DisplayName("deletes an object, and deleting it again is not an error")
	void delete() {
		String key = key("small");
		storage.put(key, new byte[] { 1 }, "image/png");

		storage.delete(key);
		storage.delete(key);

		assertThat(storage.get(key)).isEmpty();
	}

	@Test
	@DisplayName("deletes many objects at once and ignores keys that are not there")
	void deleteAll() {
		String prefix = "tenant/batch/" + UUID.randomUUID() + "/";
		List<String> keys = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			keys.add(prefix + i);
			storage.put(prefix + i, new byte[] { (byte) i }, "image/png");
		}
		keys.add(prefix + "absent");

		storage.deleteAll(keys);

		assertThat(storage.list(prefix, null, 100).objects()).isEmpty();
		storage.deleteAll(List.of());
	}

	@Test
	@DisplayName("lists by prefix in pages and reports each object's size and time")
	void listInPages() {
		String prefix = "tenant/list/" + UUID.randomUUID() + "/";
		for (int i = 0; i < 7; i++) {
			storage.put(prefix + "o" + i, new byte[i + 1], "image/png");
		}
		storage.put("tenant/list-other/" + UUID.randomUUID(), new byte[1], "image/png");

		Set<String> seen = new HashSet<>();
		String token = null;
		int pages = 0;
		do {
			Listing page = storage.list(prefix, token, 3);
			assertThat(page.objects().size()).isLessThanOrEqualTo(3);
			for (ObjectInfo object : page.objects()) {
				assertThat(object.key()).startsWith(prefix);
				assertThat(object.size()).isEqualTo(Integer.parseInt(object.key().substring(prefix.length() + 1)) + 1);
				assertThat(object.lastModified()).isNotNull();
				seen.add(object.key());
			}
			token = page.nextToken();
			pages++;
		}
		while (token != null && pages < 10);

		assertThat(seen).hasSize(7);
		assertThat(pages).isEqualTo(3);
		assertThat(token).isNull();
	}

	@Test
	@DisplayName("a presigned PUT stores an object and a presigned GET reads it back")
	void presignedTransfers() throws Exception {
		String key = key("presigned");
		byte[] content = "hello from a presigned address".getBytes(StandardCharsets.UTF_8);

		URI put = storage.presignPut(key, "text/plain", Duration.ofMinutes(5)).toURI();
		HttpResponse<String> uploaded = HTTP.send(HttpRequest.newBuilder(put)
			.header("Content-Type", "text/plain")
			.PUT(HttpRequest.BodyPublishers.ofByteArray(content))
			.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(uploaded.statusCode()).as("presigned PUT: %s", uploaded.body()).isEqualTo(200);

		URI get = storage.presignGet(key, Duration.ofMinutes(5)).toURI();
		HttpResponse<byte[]> downloaded = HTTP.send(HttpRequest.newBuilder(get).GET().build(),
				HttpResponse.BodyHandlers.ofByteArray());
		assertThat(downloaded.statusCode()).isEqualTo(200);
		assertThat(downloaded.body()).isEqualTo(content);

		try (StoredObject object = read(key)) {
			assertThat(object.content().readAllBytes()).isEqualTo(content);
		}
	}

	@Test
	@DisplayName("a presigned address that was altered is refused")
	void alteredPresignedAddress() throws Exception {
		String key = key("tampered");
		storage.put(key, new byte[] { 1 }, "image/png");
		String url = storage.presignGet(key, Duration.ofMinutes(5)).toString();

		HttpResponse<String> response = HTTP.send(
				HttpRequest.newBuilder(URI.create(url.replace("/" + key.substring(key.lastIndexOf('/') + 1), "/other")))
					.GET()
					.build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isIn(403, 404);
	}

	@Test
	@DisplayName("wrong credentials are reported as the storage being unavailable, without revealing them")
	void wrongCredentials() {
		FilesProperties properties = new FilesProperties(true, "http://localhost:" + RUSTFS.getMappedPort(9000),
				"us-east-1", "contract-wrong", "wrong-access-key-value", "wrong-secret-key-value", false, null, null,
				"0 0 4 * * *", 500);
		try (S3ObjectStorage wrong = new S3ObjectStorage(properties)) {
			assertThatThrownBy(() -> wrong.put(key("x"), new byte[] { 1 }, "image/png"))
				.isInstanceOf(ObjectStorage.Unavailable.class)
				.satisfies((ex) -> assertThat(ex.getMessage()).doesNotContain("wrong-secret-key-value"));
		}
	}

	@Test
	@DisplayName("an unreachable server is reported as the storage being unavailable")
	void unreachable() {
		FilesProperties properties = new FilesProperties(true, "http://localhost:1", "us-east-1", "contract-down",
				ACCESS_KEY, SECRET_KEY, false, null, null, "0 0 4 * * *", 500);
		try (S3ObjectStorage down = new S3ObjectStorage(properties)) {
			assertThatThrownBy(() -> down.get(key("x"))).isInstanceOf(ObjectStorage.Unavailable.class);
		}
	}

	private static StoredObject read(String key) {
		return storage.get(key).orElseThrow(() -> new AssertionError("There is no object " + key));
	}

}
