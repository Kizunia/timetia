package com.kibootia.backend.identity.keycloak;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import com.kibootia.backend.Await;
import com.kibootia.backend.identity.application.IdentityProperties;
import com.kibootia.backend.identity.application.IdentityProviderAdmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Keycloak adapter against a real Keycloak and a real mail catcher (ADR 0025, ADR 0041, ADR 0045), with the
 * least-privilege service account the design calls for: the realm-management roles to view, query and manage users, and
 * nothing else. It shows that the account can create, find, disable, enable and delete users and have Keycloak send both
 * emails, that a client holding fewer roles is refused, and that a wrong secret or an unreachable server is reported
 * without leaking anything.
 */
@Testcontainers
class KeycloakAdminAdapterIT {

	static final Network NETWORK = Network.newNetwork();

	@Container
	static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:v1.33.1").withNetwork(NETWORK)
		.withNetworkAliases("mailpit")
		.withExposedPorts(8025)
		.waitingFor(Wait.forHttp("/api/v1/info").forPort(8025));

	@Container
	static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.8.2")
		.withNetwork(NETWORK)
		.withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
		.withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin-password-for-tests")
		.withCommand("start-dev", "--import-realm")
		.withCopyFileToContainer(MountableFile.forClasspathResource("keycloak/kibootia-test-realm.json"),
				"/opt/keycloak/data/import/kibootia-realm.json")
		.withExposedPorts(8080)
		.waitingFor(Wait.forHttp("/realms/kibootia").forPort(8080).withStartupTimeout(Duration.ofMinutes(4)));

	private static final HttpClient HTTP = HttpClient.newHttpClient();

	private static KeycloakAdminAdapter adapter(String clientId, String secret) {
		return adapter("http://localhost:" + KEYCLOAK.getMappedPort(8080), clientId, secret);
	}

	private static KeycloakAdminAdapter adapter(String baseUrl, String clientId, String secret) {
		IdentityProperties properties = new IdentityProperties(null, "http://localhost:3470", Duration.ofDays(1),
				new IdentityProperties.Keycloak(baseUrl, "kibootia", "kibootia-web", clientId, secret,
						Duration.ofSeconds(2), Duration.ofSeconds(30)));
		return new KeycloakAdminAdapter(properties, Clock.systemUTC());
	}

	private static KeycloakAdminAdapter admin() {
		return adapter("kibootia-admin", "admin-secret");
	}

	private static String uniqueEmail(String prefix) {
		return prefix + "." + UUID.randomUUID().toString().substring(0, 8) + "@kibootia.test";
	}

	/** The mail the catcher holds for an address, as its raw JSON. */
	private static String mailTo(String address) throws Exception {
		HttpResponse<String> response = HTTP.send(HttpRequest
			.newBuilder(URI.create("http://localhost:" + MAILPIT.getMappedPort(8025) + "/api/v1/search?query=to:" + address))
			.build(), HttpResponse.BodyHandlers.ofString());
		return response.body();
	}

	private static void awaitMailTo(String address) {
		Await.until("a mail to " + address, Duration.ofSeconds(30), () -> {
			try {
				return mailTo(address).contains("\"messages_count\":1") || mailTo(address).contains("\"total\":1");
			}
			catch (Exception ex) {
				return false;
			}
		});
	}

	@Test
	@DisplayName("creates an account, finds it, and refuses a second account with the same address")
	void createAndFind() {
		KeycloakAdminAdapter keycloak = admin();
		String email = uniqueEmail("create");

		UUID id = keycloak.createUser(email, true, List.of("UPDATE_PASSWORD"));

		assertThat(keycloak.find(id)).hasValueSatisfying((account) -> {
			assertThat(account.enabled()).isTrue();
			assertThat(account.emailVerified()).isTrue();
		});
		assertThatThrownBy(() -> keycloak.createUser(email, true, List.of()))
			.isInstanceOf(IdentityProviderAdmin.EmailInUse.class);
		assertThat(keycloak.find(UUID.randomUUID())).isEmpty();
	}

	@Test
	@DisplayName("an account created without a verified address is found as unverified")
	void unverifiedAccount() {
		KeycloakAdminAdapter keycloak = admin();

		UUID id = keycloak.createUser(uniqueEmail("unverified"), false, List.of());

		assertThat(keycloak.find(id).orElseThrow().emailVerified()).isFalse();
	}

	@Test
	@DisplayName("disables and enables an account, ends its sessions, and does nothing for an account that is gone")
	void disableAndEnable() {
		KeycloakAdminAdapter keycloak = admin();
		UUID id = keycloak.createUser(uniqueEmail("toggle"), true, List.of());

		keycloak.setEnabled(id, false);
		assertThat(keycloak.find(id).orElseThrow().enabled()).isFalse();
		keycloak.setEnabled(id, true);
		assertThat(keycloak.find(id).orElseThrow().enabled()).isTrue();
		keycloak.endSessions(id);

		UUID gone = UUID.randomUUID();
		keycloak.setEnabled(gone, false);
		keycloak.endSessions(gone);
	}

	@Test
	@DisplayName("deletes an account, and deleting it again is harmless")
	void deleting() {
		KeycloakAdminAdapter keycloak = admin();
		UUID id = keycloak.createUser(uniqueEmail("delete"), true, List.of());

		keycloak.delete(id);

		assertThat(keycloak.find(id)).isEmpty();
		keycloak.delete(id);
	}

	@Test
	@DisplayName("Keycloak emails the invitation, and the mail catcher receives it")
	void invitationMail() throws Exception {
		KeycloakAdminAdapter keycloak = admin();
		String email = uniqueEmail("invited");
		UUID id = keycloak.createUser(email, true, List.of("UPDATE_PASSWORD", "UPDATE_PROFILE"));

		keycloak.sendInvitation(id);

		awaitMailTo(email);
		assertThat(mailTo(email)).contains(email);
	}

	@Test
	@DisplayName("Keycloak emails the verification link to an unverified address")
	void verificationMail() throws Exception {
		KeycloakAdminAdapter keycloak = admin();
		String email = uniqueEmail("verify");
		UUID id = keycloak.createUser(email, false, List.of());

		keycloak.sendVerificationEmail(id);

		awaitMailTo(email);
		assertThat(mailTo(email)).contains(email);
	}

	@Test
	@DisplayName("a client that holds only the view roles can read accounts but is refused every change")
	void leastPrivilege() {
		KeycloakAdminAdapter admin = admin();
		UUID id = admin.createUser(uniqueEmail("guarded"), true, List.of());
		KeycloakAdminAdapter reader = adapter("kibootia-reader", "reader-secret");

		assertThat(reader.find(id)).isPresent();
		assertThatThrownBy(() -> reader.createUser(uniqueEmail("forbidden"), true, List.of()))
			.isInstanceOf(IdentityProviderAdmin.Unavailable.class)
			.hasMessageContaining("403");
		assertThatThrownBy(() -> reader.delete(id)).isInstanceOf(IdentityProviderAdmin.Unavailable.class);
		assertThat(admin.find(id)).as("nothing was changed").isPresent();
	}

	@Test
	@DisplayName("a wrong secret is reported as a refusal and the message does not contain the secret")
	void wrongSecret() {
		KeycloakAdminAdapter wrong = adapter("kibootia-admin", "not-the-secret");

		assertThatThrownBy(() -> wrong.find(UUID.randomUUID())).isInstanceOf(IdentityProviderAdmin.Unavailable.class)
			.hasMessageContaining("401")
			.satisfies((ex) -> assertThat(ex.getMessage()).doesNotContain("not-the-secret"));
	}

	@Test
	@DisplayName("a missing secret and an unreachable server are both reported as unavailable")
	void unavailable() {
		assertThatThrownBy(() -> adapter("kibootia-admin", " ").find(UUID.randomUUID()))
			.isInstanceOf(IdentityProviderAdmin.Unavailable.class)
			.hasMessageContaining("no secret");
		assertThatThrownBy(() -> adapter("http://localhost:1", "kibootia-admin", "admin-secret").find(UUID.randomUUID()))
			.isInstanceOf(IdentityProviderAdmin.Unavailable.class)
			.hasMessageContaining("could not be reached");
	}

}
