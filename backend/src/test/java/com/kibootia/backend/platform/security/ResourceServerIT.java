package com.kibootia.backend.platform.security;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.kibootia.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The resource server against a real decoder and a local key endpoint (token-validation.md, ADR 0008). Each rejection
 * case of milestone 1 has its own test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "management.server.port=0")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ResourceServerIT {

	@DynamicPropertySource
	static void tokenValidation(DynamicPropertyRegistry registry) {
		registry.add("kibootia.security.issuer", () -> TestKeys.ISSUER);
		registry.add("kibootia.security.jwk-set-uri", TestKeys::jwkSetUri);
		registry.add("kibootia.security.audience", () -> TestKeys.AUDIENCE);
	}

	@Autowired
	MockMvc mvc;

	@Autowired
	RequestMappingHandlerMapping handlerMapping;

	@LocalManagementPort
	int managementPort;

	private ResultActions me(String token) throws Exception {
		var request = get("/api/v1/me");
		if (token != null) {
			request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		return this.mvc.perform(request);
	}

	private void assertRejected(ResultActions result) throws Exception {
		result.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.startsWith("Bearer")))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.code").value("platform.unauthenticated"));
	}

	@Test
	@DisplayName("accepts a valid token and returns the signed-in user's identity")
	void validTokenReturnsIdentity() throws Exception {
		me(TestKeys.validToken()).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("5d1ec1d6-3d0d-4d6e-8a31-6a0b9a1d4c11"))
			.andExpect(jsonPath("$.username").value("demo"))
			.andExpect(jsonPath("$.email").value("demo@kibootia.localhost"))
			.andExpect(jsonPath("$.emailVerified").value(true))
			.andExpect(jsonPath("$.name").value("Demo User"));
	}

	@Test
	@DisplayName("accepts the public issuer while signing keys come from a different address")
	void publicIssuerWithInternalKeyAddress() throws Exception {
		assertThat(TestKeys.jwkSetUri()).doesNotContain("localhost:8180");
		me(TestKeys.validToken()).andExpect(status().isOk());
	}

	@Test
	@DisplayName("rejects a request without a token")
	void missingToken() throws Exception {
		assertRejected(me(null));
	}

	@Test
	@DisplayName("rejects a malformed token")
	void malformedToken() throws Exception {
		assertRejected(me("not-a-jwt"));
	}

	@Test
	@DisplayName("rejects an expired token")
	void expiredToken() throws Exception {
		String token = TestKeys.token((claims) -> claims.issueTime(Date.from(Instant.now().minusSeconds(900)))
			.expirationTime(Date.from(Instant.now().minusSeconds(600))));
		assertRejected(me(token));
	}

	@Test
	@DisplayName("rejects a token signed with a key Keycloak does not publish")
	void wrongSignature() throws Exception {
		assertRejected(me(TestKeys.tokenSignedWithForeignKey()));
	}

	@Test
	@DisplayName("rejects a token from a different issuer")
	void wrongIssuer() throws Exception {
		assertRejected(me(TestKeys.token((claims) -> claims.issuer("http://localhost:8180/realms/other"))));
	}

	@Test
	@DisplayName("rejects a token issued for a different audience")
	void wrongAudience() throws Exception {
		assertRejected(me(TestKeys.token((claims) -> claims.audience(List.of("some-other-api")))));
	}

	@Test
	@DisplayName("rejects a token without any audience")
	void missingAudience() throws Exception {
		assertRejected(me(TestKeys.token((claims) -> claims.audience((String) null))));
	}

	@Test
	@DisplayName("rejects a service-account token even when its audience is correct")
	void serviceAccountToken() throws Exception {
		String token = TestKeys.token((claims) -> claims.claim("preferred_username", "service-account-kibootia-admin")
			.claim("client_id", "kibootia-admin"));
		assertRejected(me(token));
	}

	@Test
	@DisplayName("requires authentication on every mapped endpoint")
	void everyEndpointRequiresAuthentication() throws Exception {
		Set<String> unprotected = new TreeSet<>();
		for (RequestMappingInfo info : this.handlerMapping.getHandlerMethods().keySet()) {
			if (info.getPathPatternsCondition() == null) {
				continue;
			}
			for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
				String path = pattern.replaceAll("\\{[^/]+}", "1");
				Set<org.springframework.web.bind.annotation.RequestMethod> methods = info.getMethodsCondition().getMethods();
				List<HttpMethod> verbs = methods.isEmpty() ? List.of(HttpMethod.GET)
						: methods.stream().map((method) -> HttpMethod.valueOf(method.name())).toList();
				for (HttpMethod verb : verbs) {
					int status = this.mvc.perform(request(verb, path)).andReturn().getResponse().getStatus();
					if (status != 401) {
						unprotected.add(verb + " " + pattern + " -> " + status);
					}
				}
			}
		}
		assertThat(unprotected).isEmpty();
	}

	@Test
	@DisplayName("denies by default: an unmapped path needs a token too")
	void unmappedPathRequiresAuthentication() throws Exception {
		assertRejected(this.mvc.perform(get("/api/v1/does-not-exist")));
		assertRejected(this.mvc.perform(get("/actuator/health")));
	}

	@Test
	@DisplayName("serves no authenticated response for an unmapped path either")
	void unmappedPathWithTokenIsNotFound() throws Exception {
		this.mvc.perform(get("/api/v1/does-not-exist").header(HttpHeaders.AUTHORIZATION, "Bearer " + TestKeys.validToken()))
			.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("serves health on the management port without a token and exposes nothing else there")
	void managementPortServesOnlyHealth() throws Exception {
		HttpClient client = HttpClient.newHttpClient();
		for (String path : List.of("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")) {
			HttpResponse<String> response = client.send(
					HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.managementPort + path)).build(),
					HttpResponse.BodyHandlers.ofString());
			assertThat(response.statusCode()).as(path).isEqualTo(200);
			assertThat(response.body()).as(path).contains("\"status\":\"UP\"");
		}
		HttpResponse<String> env = client.send(
				HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.managementPort + "/actuator/env")).build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(env.statusCode()).isIn(401, 404);
	}

}
