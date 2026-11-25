package com.kibootia.backend.platform.security;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Token validation settings (ADR 0008, ADR 0040). The issuer is the public Keycloak URL that appears in tokens; the
 * key set is fetched from {@code jwkSetUri}, which can be an internal address, so the two are separate.
 *
 * @param issuer the exact {@code iss} value tokens must carry
 * @param jwkSetUri where signing keys are fetched from
 * @param audience the {@code aud} value tokens must carry
 */
@Validated
@ConfigurationProperties("kibootia.security")
record SecurityProperties(@NotBlank String issuer, @NotBlank String jwkSetUri, @NotBlank String audience) {
}
