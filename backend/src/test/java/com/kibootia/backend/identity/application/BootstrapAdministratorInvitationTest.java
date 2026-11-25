package com.kibootia.backend.identity.application;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/** The cases where the setting says nothing usable: the runner must neither fail nor touch anything. */
class BootstrapAdministratorInvitationTest {

	private static BootstrapAdministratorInvitation with(String setting) {
		IdentityProperties properties = new IdentityProperties(setting, "http://localhost:3470", Duration.ofDays(1),
				new IdentityProperties.Keycloak("http://localhost:8180", "timetia", "timetia-web", "timetia-admin", "secret",
						Duration.ofSeconds(1), Duration.ofSeconds(1)));
		// Nothing else is used: the runner returns before it asks any of them anything.
		return new BootstrapAdministratorInvitation(properties, null, null, null, null);
	}

	@Test
	@DisplayName("no setting, a blank one, or one that is not an email address invites nobody and does not fail")
	void unusableSetting() {
		assertThatCode(() -> with(null).invite()).doesNotThrowAnyException();
		assertThatCode(() -> with("").invite()).doesNotThrowAnyException();
		assertThatCode(() -> with("   ").invite()).doesNotThrowAnyException();
		assertThatCode(() -> with("not-an-address").invite()).doesNotThrowAnyException();
	}

}
