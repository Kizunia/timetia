package com.kibootia.backend;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kibootia.backend.platform.archfixtures.good.application.CheckedUseCase;
import com.kibootia.backend.platform.archfixtures.rule5.lonely.Lonely;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The architecture rules are only worth having if they can fail. Most rules say "no class may...", which a typo in a
 * package name, or a module that does not exist, makes true of nothing; so each rule here is also pointed at a class made
 * to break it, and at one made to satisfy it. The classes are under {@code platform.archfixtures} in the test sources:
 * they are not beans, and neither {@code ArchitectureTest} nor Spring Modulith looks at test classes.
 */
class ArchitectureRulesFireTest {

	private static final String FIXTURES = "com.kibootia.backend.platform.archfixtures";

	static JavaClasses fixtures;

	@BeforeAll
	static void importFixtures() {
		// Tests are included on purpose: this is where the fixtures are.
		fixtures = new ClassFileImporter().importPackages(FIXTURES);
	}

	@Test
	@DisplayName("the fixtures were found, so a rule that checks none of them cannot pass by accident")
	void fixturesExist() {
		assertThat(fixtures.contain(CheckedUseCase.class)).isTrue();
		assertThat(fixtures.contain(Lonely.class)).isTrue();
		assertThat(fixtures.size()).isGreaterThan(10);
	}

	// --- rule 7 -----------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("rule 7 names a use case method that has no authorization check")
	void rule7Fires() {
		assertThatThrownBy(() -> AuthorizationRules.useCaseMethodsAreChecked("..archfixtures.bad.application..").check(fixtures))
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining("UncheckedUseCase.save");
	}

	@Test
	@DisplayName("rule 7 accepts a use case whose methods carry @RequiresPermission or @NoAuthorization")
	void rule7Accepts() {
		assertThatCode(() -> AuthorizationRules.useCaseMethodsAreChecked("..archfixtures.good.application..").check(fixtures))
			.doesNotThrowAnyException();
	}

	// --- rule 5 -----------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("rule 5 names the class that depends on a removable module")
	void rule5Fires() {
		assertThatThrownBy(() -> ModuleRules.nothingOutsideDependsOn(FIXTURES + ".rule5.removable").check(fixtures))
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining("DependsOnRemovable");
	}

	@Test
	@DisplayName("rule 5 accepts a removable module nothing depends on")
	void rule5Accepts() {
		assertThatCode(() -> ModuleRules.nothingOutsideDependsOn(FIXTURES + ".rule5.lonely").check(fixtures))
			.doesNotThrowAnyException();
	}

	// --- rules 9 to 11 ----------------------------------------------------------------------------------------------

	@Test
	@DisplayName("rule 9 names a scheduled method without a lock, and the rule holds for one with a lock")
	void rule9() {
		assertThatThrownBy(() -> EventAndJobRules.scheduledMethodsTakeALock().check(fixtures.that(inPackage("bad.jobs"))))
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining("UnlockedJob.run");
		assertThatCode(() -> EventAndJobRules.scheduledMethodsTakeALock().check(fixtures.that(inPackage("good.jobs"))))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("rule 10 names a listener that uses @ApplicationModuleListener")
	void rule10() {
		assertThatThrownBy(() -> EventAndJobRules.durableListenersReplaceApplicationModuleListener().check(fixtures))
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining("LegacyListener.on");
	}

	@Test
	@DisplayName("rule 11 names a durable listener that does not take a domain event, and accepts one that does")
	void rule11() {
		assertThatThrownBy(() -> EventAndJobRules.durableListenersTakeADomainEvent().check(fixtures.that(inPackage("bad.events"))))
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining("WrongParameterListener.on");
		assertThatCode(() -> EventAndJobRules.durableListenersTakeADomainEvent().check(fixtures.that(inPackage("good.events"))))
			.doesNotThrowAnyException();
	}

	// Rules 2 and 3 belong to Spring Modulith, which analyses only main classes, so it cannot be given a broken module from
	// the test sources. That they are meaningful is shown in ModularityTest instead: the real modules depend on each other,
	// so there are edges for the verification to check.

	private static com.tngtech.archunit.base.DescribedPredicate<com.tngtech.archunit.core.domain.JavaClass> inPackage(String suffix) {
		return com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage("..archfixtures." + suffix + "..");
	}

}
