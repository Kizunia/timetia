package com.kibootia.backend.platform.security;

import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

import com.kibootia.backend.platform.tenancy.TenantContextFilter;
import com.kibootia.backend.platform.tenancy.TenantResolver;
import com.kibootia.backend.platform.web.ProblemFactory;

import java.time.Duration;

/**
 * Resource server configuration (ADR 0007, ADR 0008, ADR 0044): every request needs a valid user token, nothing is
 * public, there are no sessions and no CORS. The server only ever answers the Next.js server, never a browser.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
class SecurityConfiguration {

	private static final Duration KEY_FETCH_TIMEOUT = Duration.ofSeconds(3);

	/**
	 * Lets the health endpoints on the management port answer without a token, so probes work. The management port
	 * is internal and exposes only health (ADR 0047). This matcher never matches on the API port: with a separate
	 * management port, requests to {@code /actuator/**} there fall through to the chain below and need a token.
	 */
	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE)
	SecurityFilterChain managementSecurityFilterChain(HttpSecurity http) throws Exception {
		http.securityMatcher(EndpointRequest.toAnyEndpoint())
			.authorizeHttpRequests((requests) -> requests.anyRequest().permitAll())
			.sessionManagement((sessions) -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(AbstractHttpConfigurer::disable);
		return http.build();
	}

	@Bean
	SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, ProblemFactory problemFactory,
			TenantResolver tenantResolver) throws Exception {
		ProblemDetailsHandlers problems = new ProblemDetailsHandlers(problemFactory);
		http
			// Binds the tenant once the bearer token has authenticated the caller (ADR 0028).
			.addFilterAfter(new TenantContextFilter(tenantResolver), BearerTokenAuthenticationFilter.class)
			.authorizeHttpRequests((requests) -> requests.anyRequest().authenticated())
			.sessionManagement((sessions) -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// Bearer tokens in a header are not sent by browsers on their own, so there is no cookie to forge.
			.csrf(AbstractHttpConfigurer::disable)
			.cors(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache((cache) -> cache.disable())
			.oauth2ResourceServer((oauth2) -> oauth2.jwt(Customizer.withDefaults())
				.authenticationEntryPoint(problems.authenticationEntryPoint())
				.accessDeniedHandler(problems.accessDeniedHandler()))
			.exceptionHandling((errors) -> errors.authenticationEntryPoint(problems.authenticationEntryPoint())
				.accessDeniedHandler(problems.accessDeniedHandler()));
		return http.build();
	}

	/**
	 * Validates the public issuer while fetching signing keys from a separate (possibly internal) address, so the
	 * same token is accepted whether Keycloak is reached by its public or its internal hostname (OQ-45).
	 */
	@Bean
	JwtDecoder jwtDecoder(SecurityProperties properties) {
		SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
		requests.setConnectTimeout(KEY_FETCH_TIMEOUT);
		requests.setReadTimeout(KEY_FETCH_TIMEOUT);
		RestTemplate keyFetcher = new RestTemplate(requests);
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
			.restOperations(keyFetcher)
			.build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(properties.issuer()),
				new AudienceValidator(properties.audience()), new UserTokenValidator()));
		return decoder;
	}

}
