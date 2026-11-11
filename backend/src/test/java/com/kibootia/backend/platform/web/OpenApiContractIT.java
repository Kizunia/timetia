package com.kibootia.backend.platform.web;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import com.kibootia.backend.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Writes the OpenAPI description to the committed {@code backend/openapi.json} (ADR 0043). The frontend client is
 * generated from that file, and CI fails when running the build changes it: an API change is therefore always visible as
 * a diff in the pull request. Run {@code ./mvnw verify} and commit the result after changing an endpoint.
 */
@SpringBootTest(properties = "springdoc.use-management-port=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiContractIT {

	/** The module root is the working directory of Maven's test runs. */
	static final Path FILE = Path.of("openapi.json");

	@Autowired
	MockMvc mvc;

	private JsonNode generate() throws Exception {
		String json = mvc.perform(get("/v3/api-docs").with(jwt().jwt((token) -> token.subject("11111111-1111-7111-8111-111111111111")
				.claim("email_verified", true))))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		JsonNode api = JsonMapper.builder().build().readTree(json);
		// springdoc adds the URL the request came in on; the committed file must not depend on where it was generated.
		((tools.jackson.databind.node.ObjectNode) api).remove("servers");
		return api;
	}

	@Test
	@DisplayName("the description is written to openapi.json")
	void writesTheDescription() throws Exception {
		JsonMapper sorted = JsonMapper.builder()
			.enable(SerializationFeature.INDENT_OUTPUT)
			.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
			.build();
		// A tree has no key order of its own; going through plain maps lets the mapper sort every level.
		Object plain = sorted.convertValue(generate(), Object.class);
		String text = sorted.writeValueAsString(plain).replace("\r\n", "\n") + "\n";

		Files.writeString(FILE, text, StandardCharsets.UTF_8);

		assertThat(Files.readString(FILE)).isEqualTo(text);
	}

	@Test
	@DisplayName("it describes every endpoint under /api/v1, with a bearer scheme and no server URL")
	void describesTheApi() throws Exception {
		JsonNode api = generate();

		assertThat(api.has("servers")).as("a server URL would tie the file to where it was generated").isFalse();
		assertThat(api.at("/components/securitySchemes/bearer/scheme").asText()).isEqualTo("bearer");
		assertThat(api.at("/security/0/bearer").isArray()).isTrue();
		Set<String> paths = new java.util.TreeSet<>();
		api.get("paths").propertyNames().forEach(paths::add);
		assertThat(paths).allMatch((path) -> path.startsWith("/api/v1/"));
		// The notes module is removable (ADR 0024), so its endpoints are checked in NotesOpenApiIT and left out here: this
		// list stays right whether or not the module is present.
		paths.removeIf((path) -> path.equals("/api/v1/notes") || path.startsWith("/api/v1/notes/"));
		assertThat(paths).containsExactlyInAnyOrder("/api/v1/me", "/api/v1/me/preferences",
				"/api/v1/me/verification-email", "/api/v1/me/avatar", "/api/v1/users/{id}/avatar",
				"/api/v1/failed-events", "/api/v1/failed-events/{id}/retry", "/api/v1/permissions", "/api/v1/roles",
				"/api/v1/roles/{id}", "/api/v1/users", "/api/v1/users/{id}", "/api/v1/users/{id}/invitation",
				"/api/v1/users/{id}/disable", "/api/v1/users/{id}/enable", "/api/v1/users/{id}/restore",
				"/api/v1/users/{id}/erase", "/api/v1/users/{userId}/roles", "/api/v1/audit-entries");
	}

	@Test
	@DisplayName("every operation documents the Problem Details responses")
	void documentsProblems() throws Exception {
		JsonNode api = generate();

		assertThat(api.at("/components/schemas/Problem/required").toString()).contains("code");
		api.get("paths").properties().forEach((path) -> path.getValue().properties().forEach((method) -> {
			JsonNode responses = method.getValue().get("responses");
			assertThat(responses.has("401")).as(method.getKey() + " " + path.getKey() + " 401").isTrue();
			assertThat(responses.has("403")).as(method.getKey() + " " + path.getKey() + " 403").isTrue();
			assertThat(responses.has("500")).as(method.getKey() + " " + path.getKey() + " 500").isTrue();
			assertThat(responses.at("/401/content/application~1problem+json/schema/$ref").asText())
				.isEqualTo("#/components/schemas/Problem");
		}));
	}

	@Test
	@DisplayName("response fields are required and the nullable ones are marked, so the client types are exact")
	void fieldsAreRequiredOrNullable() throws Exception {
		JsonNode schemas = generate().at("/components/schemas");

		assertThat(schemas.at("/MeResponse/required").toString()).contains("id", "emailVerified", "filesEnabled")
			.doesNotContain("username");
		assertThat(schemas.at("/MeResponse/properties/email/type").toString()).contains("string", "null");
		assertThat(schemas.at("/MeResponse/properties/id/type").toString()).doesNotContain("null");
	}

}
