package com.kibootia.backend.notes;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.kibootia.backend.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the notes module adds to the API description. The core test ({@code OpenApiContractIT}) leaves these paths and
 * types out so that it stays right when the module is removed; this one goes with the module.
 */
@SpringBootTest(properties = "springdoc.use-management-port=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NotesOpenApiIT {

	@Autowired
	MockMvc mvc;

	private JsonNode generate() throws Exception {
		String json = this.mvc.perform(get("/v3/api-docs")
			.with(jwt().jwt((token) -> token.subject("11111111-1111-7111-8111-111111111111").claim("email_verified", true))))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		return JsonMapper.builder().build().readTree(json);
	}

	@Test
	@DisplayName("the notes endpoints are described under /api/v1/notes")
	void describesTheNotesEndpoints() throws Exception {
		Set<String> paths = new TreeSet<>();
		generate().get("paths").propertyNames().forEach(paths::add);

		assertThat(paths).filteredOn((path) -> path.startsWith("/api/v1/notes")).containsExactlyInAnyOrder("/api/v1/notes",
				"/api/v1/notes/{id}", "/api/v1/notes/{id}/restore", "/api/v1/notes/{id}/attachment");
	}

	@Test
	@DisplayName("the attachment is described as an upload, a removal and a PNG download")
	void describesTheAttachment() throws Exception {
		JsonNode attachment = generate().at("/paths/~1api~1v1~1notes~1{id}~1attachment");

		assertThat(attachment.has("put")).isTrue();
		assertThat(attachment.has("delete")).isTrue();
		assertThat(attachment.has("get")).isTrue();
		assertThat(attachment.at("/put/requestBody/content").has("multipart/form-data")).isTrue();
		assertThat(attachment.at("/get/responses/200/content").has("image/png")).isTrue();
	}

	@Test
	@DisplayName("the note types mark their required fields, so the client types are exact")
	void fieldsAreRequired() throws Exception {
		JsonNode schemas = generate().at("/components/schemas");

		assertThat(schemas.at("/NoteResponse/required").toString()).contains("id", "title", "body", "version", "createdAt");
		assertThat(schemas.at("/NoteResponse/required").toString()).doesNotContain("attachmentVersion");
		assertThat(schemas.at("/NoteResponse/properties/attachmentVersion/type").toString()).contains("string", "null");
		assertThat(schemas.at("/CreateNoteRequest/required").toString()).contains("title", "body");
		assertThat(schemas.at("/UpdateNoteRequest/required").toString()).contains("title", "body", "version");
	}

}
