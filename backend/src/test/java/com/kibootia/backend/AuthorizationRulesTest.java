package com.kibootia.backend;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.stereotype.Service;

import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.authorization.RequiresPermission;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves rule 7 fails for an unprotected use case and passes for protected ones. The fixtures are nested classes of this
 * test, which Spring never scans; they live in this test's package, which is the package the rule is pointed at.
 */
class AuthorizationRulesTest {

	static final String PACKAGE = "com.kibootia.backend";

	static class Fixtures {

		@Service
		static class Unprotected {

			public String exposed() {
				return "anyone can call this";
			}

		}

		@Service
		static class Protected {

			private final Authorization authorization;

			Protected(Authorization authorization) {
				this.authorization = authorization;
			}

			@RequiresPermission("probe.items.read")
			public String byAnnotation() {
				return "checked before the call";
			}

			public String byCall() {
				this.authorization.require("probe.items.update");
				return "checked in the method";
			}

			@NoAuthorization("returns nothing about anyone")
			public String byMarker() {
				return "deliberately open";
			}

			String packagePrivateIsNotAUseCaseEntryPoint() {
				return "not public";
			}

		}

	}

	private static JavaClasses import_(Class<?>... classes) {
		return new ClassFileImporter().importClasses(classes);
	}

	@Test
	@DisplayName("fails for a public use-case method with no check and no marker")
	void unprotectedMethodIsReported() {
		JavaClasses unprotected = import_(Fixtures.Unprotected.class);

		assertThatThrownBy(() -> AuthorizationRules.useCaseMethodsAreChecked(PACKAGE).check(unprotected))
			.isInstanceOf(java.lang.AssertionError.class)
			.hasMessageContaining("Unprotected.exposed()");
	}

	@Test
	@DisplayName("passes for methods protected by annotation, by a call or by an explicit marker")
	void protectedMethodsPass() {
		JavaClasses protectedClasses = import_(Fixtures.Protected.class);

		assertThatCode(() -> AuthorizationRules.useCaseMethodsAreChecked(PACKAGE).check(protectedClasses))
			.doesNotThrowAnyException();
	}

}
