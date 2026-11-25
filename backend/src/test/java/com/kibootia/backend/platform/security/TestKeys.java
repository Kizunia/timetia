package com.kibootia.backend.platform.security;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

/**
 * Signing keys and a local JWKS endpoint for the resource-server tests. The endpoint runs on a random local port while
 * tokens carry the public Keycloak issuer, which is how the real deployment splits the two (ADR 0040).
 */
final class TestKeys {

	static final String ISSUER = "http://localhost:8180/realms/timetia";

	static final String AUDIENCE = "timetia-api";

	private static final String KEY_ID = "test-key";

	private static final RSAKey SIGNING_KEY = generate(KEY_ID);

	/** Same key id as the published key, but a different key pair: a forged signature. */
	private static final RSAKey FOREIGN_KEY = generate(KEY_ID);

	private static final HttpServer JWKS_SERVER = startJwksServer();

	private TestKeys() {
	}

	static String jwkSetUri() {
		return "http://127.0.0.1:" + JWKS_SERVER.getAddress().getPort() + "/certs";
	}

	/** A token for a signed-in user that passes every check. */
	static String validToken() {
		return token(claims -> {
		});
	}

	static String token(Consumer<JWTClaimsSet.Builder> customizer) {
		return sign(SIGNING_KEY, customizer);
	}

	static String tokenSignedWithForeignKey() {
		return sign(FOREIGN_KEY, (claims) -> {
		});
	}

	/** A token with the right claims and no signature at all ({@code alg: none}). */
	static String unsignedToken() {
		return new PlainJWT(baseClaims().build()).serialize();
	}

	/** A token with the right claims signed with a shared secret instead of Keycloak's RSA key. */
	static String tokenSignedWithSharedSecret() {
		try {
			SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(KEY_ID).build(),
					baseClaims().build());
			jwt.sign(new MACSigner("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
			return jwt.serialize();
		}
		catch (JOSEException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** The claims of an access token Keycloak would issue to a signed-in user. */
	private static JWTClaimsSet.Builder baseClaims() {
		Instant now = Instant.now();
		return new JWTClaimsSet.Builder().issuer(ISSUER)
			.audience(List.of(AUDIENCE))
			.subject("5d1ec1d6-3d0d-4d6e-8a31-6a0b9a1d4c11")
			.issueTime(Date.from(now))
			.expirationTime(Date.from(now.plusSeconds(300)))
			.jwtID(UUID.randomUUID().toString())
			.claim("typ", "Bearer")
			.claim("preferred_username", "demo")
			.claim("email", "demo@kibootia.localhost")
			.claim("email_verified", true)
			.claim("name", "Demo User")
			.claim("given_name", "Demo")
			.claim("family_name", "User");
	}

	private static String sign(RSAKey key, Consumer<JWTClaimsSet.Builder> customizer) {
		JWTClaimsSet.Builder claims = baseClaims();
		customizer.accept(claims);
		try {
			SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
					claims.build());
			jwt.sign(new RSASSASigner(key));
			return jwt.serialize();
		}
		catch (JOSEException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static RSAKey generate(String keyId) {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			var pair = generator.generateKeyPair();
			return new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate())
				.keyID(keyId)
				.build();
		}
		catch (java.security.GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static HttpServer startJwksServer() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
			byte[] body = new JWKSet(SIGNING_KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
			server.createContext("/certs", (exchange) -> {
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
				exchange.close();
			});
			server.setExecutor(null);
			server.start();
			return server;
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
