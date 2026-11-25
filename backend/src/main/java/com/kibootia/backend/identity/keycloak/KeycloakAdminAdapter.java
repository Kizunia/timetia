package com.kibootia.backend.identity.keycloak;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.kibootia.backend.identity.application.IdentityProperties;
import com.kibootia.backend.identity.application.IdentityProviderAdmin;

/**
 * Talks to the Keycloak Admin API with a service-account client that holds only the realm-management roles it needs,
 * never realm-admin (ADR 0025, ADR 0045). It is the only class that knows the provider is Keycloak.
 *
 * <p>The access token is fetched on first use and kept until shortly before it expires; a 401 from the Admin API drops
 * it and tries once more. A failure of any kind is reported as {@link IdentityProviderAdmin.Unavailable} with a message
 * that never contains the secret, a token or a response body, which can echo personal data.
 */
@Component
class KeycloakAdminAdapter implements IdentityProviderAdmin {

	private static final Logger log = LoggerFactory.getLogger(KeycloakAdminAdapter.class);

	private static final ParameterizedTypeReference<Map<String, Object>> OBJECT = new ParameterizedTypeReference<>() {
	};

	/** Fetch a new token this long before the old one expires, so a call never carries one about to lapse. */
	private static final long TOKEN_MARGIN_SECONDS = 30;

	private final IdentityProperties properties;

	private final Clock clock;

	private final RestClient http;

	private volatile CachedToken token;

	KeycloakAdminAdapter(IdentityProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
		IdentityProperties.Keycloak settings = properties.keycloak();
		SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
		requests.setConnectTimeout(settings.connectTimeout());
		requests.setReadTimeout(settings.readTimeout());
		this.http = RestClient.builder().baseUrl(settings.baseUrl()).requestFactory(requests).build();
	}

	@Override
	public UUID createUser(String email, boolean emailVerified, List<String> requiredActions) {
		Map<String, Object> user = new HashMap<>();
		user.put("username", email);
		user.put("email", email);
		user.put("enabled", true);
		user.put("emailVerified", emailVerified);
		user.put("requiredActions", requiredActions);
		try {
			URI location = authorized((bearer) -> this.http.post()
				.uri("/admin/realms/{realm}/users", realm())
				.headers((headers) -> headers.setBearerAuth(bearer))
				.contentType(MediaType.APPLICATION_JSON)
				.body(user)
				.retrieve()
				.toBodilessEntity()
				.getHeaders()
				.getLocation());
			if (location == null) {
				throw new Unavailable("Keycloak created the account but did not say where");
			}
			String path = location.getPath();
			return UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
		}
		catch (HttpClientErrorException.Conflict ex) {
			throw new EmailInUse();
		}
	}

	@Override
	public Optional<Account> find(UUID id) {
		try {
			Map<String, Object> user = authorized((bearer) -> this.http.get()
				.uri("/admin/realms/{realm}/users/{id}", realm(), id)
				.headers((headers) -> headers.setBearerAuth(bearer))
				.retrieve()
				.body(OBJECT));
			if (user == null) {
				return Optional.empty();
			}
			return Optional.of(new Account(Boolean.TRUE.equals(user.get("enabled")),
					Boolean.TRUE.equals(user.get("emailVerified"))));
		}
		catch (HttpClientErrorException.NotFound ex) {
			return Optional.empty();
		}
	}

	/**
	 * Reads the whole account, changes the flag and writes it back, as Keycloak's own console does. A partial update
	 * could clear attributes of an account that uses the user profile feature.
	 */
	@Override
	public void setEnabled(UUID id, boolean enabled) {
		try {
			Map<String, Object> user = authorized((bearer) -> this.http.get()
				.uri("/admin/realms/{realm}/users/{id}", realm(), id)
				.headers((headers) -> headers.setBearerAuth(bearer))
				.retrieve()
				.body(OBJECT));
			if (user == null) {
				return;
			}
			user.put("enabled", enabled);
			authorized((bearer) -> this.http.put()
				.uri("/admin/realms/{realm}/users/{id}", realm(), id)
				.headers((headers) -> headers.setBearerAuth(bearer))
				.contentType(MediaType.APPLICATION_JSON)
				.body(user)
				.retrieve()
				.toBodilessEntity());
		}
		catch (HttpClientErrorException.NotFound ex) {
			log.info("Keycloak has no account {} to {}", id, enabled ? "enable" : "disable");
		}
		if (!enabled) {
			endSessions(id);
		}
	}

	@Override
	public void endSessions(UUID id) {
		try {
			authorized((bearer) -> this.http.post()
				.uri("/admin/realms/{realm}/users/{id}/logout", realm(), id)
				.headers((headers) -> headers.setBearerAuth(bearer))
				.retrieve()
				.toBodilessEntity());
		}
		catch (HttpClientErrorException.NotFound ex) {
			log.info("Keycloak has no account {} whose sessions could be ended", id);
		}
	}

	@Override
	public void sendInvitation(UUID id) {
		// Setting a password proves the mailbox, and the profile form collects the name the invitation did not have.
		executeActionsEmail(id, List.of("UPDATE_PASSWORD", "UPDATE_PROFILE"));
	}

	@Override
	public void sendVerificationEmail(UUID id) {
		try {
			authorized((bearer) -> this.http.put()
				.uri((uri) -> uri.path("/admin/realms/{realm}/users/{id}/send-verify-email")
					.queryParam("client_id", this.properties.keycloak().webClientId())
					.queryParam("redirect_uri", returnAddress())
					.queryParam("lifespan", this.properties.linkLifespan().toSeconds())
					.build(realm(), id))
				.headers((headers) -> headers.setBearerAuth(bearer))
				.retrieve()
				.toBodilessEntity());
		}
		catch (HttpClientErrorException.NotFound ex) {
			throw new Unavailable("Keycloak has no such account to send the email to");
		}
	}

	@Override
	public void delete(UUID id) {
		try {
			authorized((bearer) -> this.http.delete()
				.uri("/admin/realms/{realm}/users/{id}", realm(), id)
				.headers((headers) -> headers.setBearerAuth(bearer))
				.retrieve()
				.toBodilessEntity());
		}
		catch (HttpClientErrorException.NotFound ex) {
			log.info("Keycloak account {} was already gone", id);
		}
	}

	// ---------------------------------------------------------------------------------------------------------------

	private void executeActionsEmail(UUID id, List<String> actions) {
		try {
			authorized((bearer) -> this.http.put()
				.uri((uri) -> uri.path("/admin/realms/{realm}/users/{id}/execute-actions-email")
					.queryParam("client_id", this.properties.keycloak().webClientId())
					.queryParam("redirect_uri", returnAddress())
					.queryParam("lifespan", this.properties.linkLifespan().toSeconds())
					.build(realm(), id))
				.headers((headers) -> headers.setBearerAuth(bearer))
				.contentType(MediaType.APPLICATION_JSON)
				.body(actions)
				.retrieve()
				.toBodilessEntity());
		}
		catch (HttpClientErrorException.NotFound ex) {
			throw new Unavailable("Keycloak has no such account to send the email to");
		}
	}

	/** The web application's front door: the one place a link in an email may send the user back to. */
	private String returnAddress() {
		String base = this.properties.publicBaseUrl();
		return base.endsWith("/") ? base : base + "/";
	}

	private String realm() {
		return this.properties.keycloak().realm();
	}

	/**
	 * Runs a call with a bearer token. A 401 means the cached token was refused, so a fresh one is fetched and the call is
	 * made once more. Anything else that goes wrong becomes {@link Unavailable}, except the client errors the callers
	 * handle themselves (404, 409), which pass through as they are.
	 */
	private <T> T authorized(Function<String, T> call) {
		try {
			try {
				return call.apply(bearer(false));
			}
			catch (HttpClientErrorException.Unauthorized ex) {
				return call.apply(bearer(true));
			}
		}
		catch (HttpClientErrorException.NotFound | HttpClientErrorException.Conflict ex) {
			throw ex;
		}
		catch (RestClientResponseException ex) {
			throw new Unavailable("Keycloak answered " + ex.getStatusCode().value(), ex);
		}
		catch (RestClientException ex) {
			throw new Unavailable("Keycloak could not be reached", ex);
		}
	}

	private String bearer(boolean refresh) {
		CachedToken current = this.token;
		if (refresh || current == null || this.clock.instant().isAfter(current.expiresAt().minusSeconds(TOKEN_MARGIN_SECONDS))) {
			current = fetchToken();
			this.token = current;
		}
		return current.value();
	}

	private CachedToken fetchToken() {
		IdentityProperties.Keycloak settings = this.properties.keycloak();
		if (settings.clientSecret() == null || settings.clientSecret().isBlank()) {
			throw new Unavailable("The Keycloak administration client has no secret configured");
		}
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "client_credentials");
		form.add("client_id", settings.clientId());
		form.add("client_secret", settings.clientSecret());
		try {
			Map<String, Object> response = this.http.post()
				.uri("/realms/{realm}/protocol/openid-connect/token", settings.realm())
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(OBJECT);
			if (response == null || !(response.get("access_token") instanceof String value)) {
				throw new Unavailable("Keycloak did not issue an administration token");
			}
			long lifetime = (response.get("expires_in") instanceof Number seconds) ? seconds.longValue() : 60;
			return new CachedToken(value, this.clock.instant().plusSeconds(lifetime));
		}
		catch (RestClientResponseException ex) {
			// Not the body: it can name the client. The status is enough to tell a wrong secret (401) from an outage.
			throw new Unavailable("Keycloak refused the administration login with " + ex.getStatusCode().value(), ex);
		}
		catch (RestClientException ex) {
			throw new Unavailable("Keycloak could not be reached", ex);
		}
	}

	private record CachedToken(String value, Instant expiresAt) {

	}

}
