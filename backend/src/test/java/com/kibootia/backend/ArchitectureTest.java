package com.kibootia.backend;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit rules from kibootia-docs/architecture/architecture-enforcement.md that can be meaningful before the first
 * feature module exists. Rules 1, 4, 6 and 7 hold vacuously until a module has an application package; rule 8 is
 * checked against real classes. Rule 5 waits for the optional modules; rules 2 and 3 are covered by Spring Modulith.
 */
class ArchitectureTest {

	static final String ROOT = "com.kibootia.backend";

	static JavaClasses classes;

	@BeforeAll
	static void importClasses() {
		classes = new ClassFileImporter()
				.withImportOption(new ImportOption.DoNotIncludeTests())
				.importPackages(ROOT);
	}

	@Test
	void rule1_applicationLogicDoesNotDependOnInfrastructure() {
		ArchRule rule = noClasses().that().resideInAPackage("..application..")
				.should().dependOnClassesThat().resideInAnyPackage("..web..", "..persistence..", "..keycloak..", "..s3..")
				.allowEmptyShould(true);
		rule.check(classes);
	}

	@Test
	void rule4_domainDoesNotDependOnInfrastructureFrameworks() {
		ArchRule rule = noClasses().that().resideInAPackage("..domain..")
				.should().dependOnClassesThat().resideInAnyPackage(
						"org.springframework..", "jakarta.persistence..", "org.hibernate..")
				.allowEmptyShould(true);
		rule.check(classes);
	}

	@Test
	void rule6_persistenceEntitiesAreNotUsedInWebTypes() {
		ArchRule rule = noClasses().that().resideInAPackage("..web..")
				.should().dependOnClassesThat().areAnnotatedWith("jakarta.persistence.Entity")
				.allowEmptyShould(true);
		rule.check(classes);
	}

	@Test
	void rule7_everyPublicUseCaseMethodHasAnAuthorizationCheckOrAnExplicitMarker() {
		AuthorizationRules.useCaseMethodsAreChecked("..application..").check(classes);
	}

	@Test
	void rule9_everyScheduledMethodTakesALock() {
		EventAndJobRules.scheduledMethodsTakeALock().check(classes);
	}

	@Test
	void rule10_listenersDoNotUseApplicationModuleListener() {
		EventAndJobRules.durableListenersReplaceApplicationModuleListener().check(classes);
	}

	@Test
	void rule11_durableListenersTakeADomainEvent() {
		EventAndJobRules.durableListenersTakeADomainEvent().check(classes);
	}

	/**
	 * Rule 5 for the default-on {@code files} module (ADR 0024): nothing outside it depends on it, so removing it, or
	 * switching it off, cannot break another module. Others reach it through the {@code platform.files} port.
	 */
	@Test
	void rule5_nothingOutsideTheFilesModuleDependsOnIt() {
		ArchRule rule = noClasses().that().resideOutsideOfPackage(ROOT + ".files..")
				.should().dependOnClassesThat().resideInAPackage(ROOT + ".files..")
				.because("the files module is optional; other modules use the platform.files port");
		rule.check(classes);
	}

	@Test
	void rule8_platformDependsOnNoOtherModule() {
		ArchRule rule = noClasses().that().resideInAPackage(ROOT + ".platform..")
				.should().dependOnClassesThat().resideInAnyPackage(
						ROOT + ".identity..", ROOT + ".access..", ROOT + ".audit..",
						ROOT + ".files..", ROOT + ".notes..");
		rule.check(classes);
	}

}
