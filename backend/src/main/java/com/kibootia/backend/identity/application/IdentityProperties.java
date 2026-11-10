package com.kibootia.backend.identity.application;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * The identity module's settings (ADR 0025, ADR 0041).
 *
 * @param bootstrapAdminEmail the address that is made the first administrator when it signs in with a verified email
 *        address, only while no administrator exists (ADR 0025). Leave empty once an administrator exists.
 * @param publicBaseUrl where users reach the web application; the links in Keycloak's emails return there
 * @param linkLifespan how long the link in an invitation or verification email works
 * @param keycloak how to reach the Keycloak Admin API
 */
@ConfigurationProperties("kibootia.identity")
@Validated
public record IdentityProperties(String bootstrapAdminEmail,
		@DefaultValue("http://localhost:3470") @NotBlank String publicBaseUrl,
		@DefaultValue("P7D") @NotNull Duration linkLifespan, @DefaultValue @Valid @NotNull Keycloak keycloak) {

	/**
	 * @param baseUrl Keycloak's address from the backend's side; inside Compose this is the internal address
	 * @param realm the realm users live in
	 * @param webClientId the client whose redirect URIs the links in emails may return to
	 * @param clientId the confidential service-account client that holds only the realm-management roles it needs
	 * @param clientSecret the service-account secret, from the environment or a file, never from configuration files
	 * @param connectTimeout how long to wait for a connection
	 * @param readTimeout how long to wait for an answer
	 */
	public record Keycloak(@DefaultValue("http://localhost:8180") @NotBlank String baseUrl,
			@DefaultValue("kibootia") @NotBlank String realm, @DefaultValue("kibootia-web") @NotBlank String webClientId,
			@DefaultValue("kibootia-admin") @NotBlank String clientId, String clientSecret,
			@DefaultValue("3s") @NotNull Duration connectTimeout, @DefaultValue("10s") @NotNull Duration readTimeout) {

	}

}
