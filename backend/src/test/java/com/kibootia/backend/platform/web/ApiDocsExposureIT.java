package com.kibootia.backend.platform.web;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.kibootia.backend.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The API documentation is reachable only when asked for (ADR 0043): by default neither the description nor Swagger UI is
 * served anywhere, and when exposed they live on the internal management port, never on the API port.
 */
class ApiDocsExposureIT {

	private static HttpResponse<String> get(int port, String path) throws Exception {
		return HttpClient.newHttpClient()
			.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
					HttpResponse.BodyHandlers.ofString());
	}

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
	@Import(TestcontainersConfiguration.class)
	@DisplayName("by default (production)")
	class ByDefault {

		@LocalServerPort
		int apiPort;

		@LocalManagementPort
		int managementPort;

		@Test
		@DisplayName("the management port serves neither the description nor Swagger UI")
		void notExposedOnTheManagementPort() throws Exception {
			// Unexposed endpoints fall through to the secured chain, which answers 401; either way nothing is served.
			assertThat(get(managementPort, "/actuator/openapi").statusCode()).isIn(401, 404);
			assertThat(get(managementPort, "/actuator/swagger-ui").statusCode()).isIn(401, 404);
			assertThat(get(managementPort, "/v3/api-docs").statusCode()).isIn(401, 404);
			assertThat(get(managementPort, "/swagger-ui/index.html").statusCode()).isIn(401, 404);
		}

		@Test
		@DisplayName("the API port needs a token for everything, including documentation paths")
		void apiPortStaysClosed() throws Exception {
			assertThat(get(apiPort, "/v3/api-docs").statusCode()).isEqualTo(401);
			assertThat(get(apiPort, "/swagger-ui/index.html").statusCode()).isEqualTo(401);
			assertThat(get(apiPort, "/actuator/openapi").statusCode()).isEqualTo(401);
		}

	}

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
			properties = { "management.server.port=0",
					"management.endpoints.web.exposure.include=health,openapi,swaggerui" })
	@Import(TestcontainersConfiguration.class)
	@DisplayName("when exposed (local development)")
	class WhenExposed {

		@LocalServerPort
		int apiPort;

		@LocalManagementPort
		int managementPort;

		@Test
		@DisplayName("the description is served on the management port without a token")
		void descriptionOnManagementPort() throws Exception {
			HttpResponse<String> response = get(managementPort, "/actuator/openapi");

			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.body()).contains("\"openapi\"").contains("/api/v1/notes");
		}

		@Test
		@DisplayName("Swagger UI is served on the management port")
		void swaggerUiOnManagementPort() throws Exception {
			HttpResponse<String> response = get(managementPort, "/actuator/swagger-ui");

			assertThat(response.statusCode()).as(response.body()).isIn(200, 302);
		}

		@Test
		@DisplayName("the API port is still closed")
		void apiPortStaysClosed() throws Exception {
			assertThat(get(apiPort, "/v3/api-docs").statusCode()).isEqualTo(401);
			assertThat(get(apiPort, "/actuator/openapi").statusCode()).isEqualTo(401);
		}

	}

}
