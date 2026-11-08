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
 * feature module exists. Rules 1, 4 and 6 hold vacuously until module code exists; rule 8 is checked against real
 * classes. Rules 5 and 7 wait for the code they constrain; rules 2 and 3 are covered by Spring Modulith.
 */
class ArchitectureTests {

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
				.should().dependOnClassesThat().resideInAnyPackage("..web..", "..persistence..", "..keycloak..")
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
	void rule8_platformDependsOnNoOtherModule() {
		ArchRule rule = noClasses().that().resideInAPackage(ROOT + ".platform..")
				.should().dependOnClassesThat().resideInAnyPackage(
						ROOT + ".identity..", ROOT + ".access..", ROOT + ".audit..",
						ROOT + ".files..", ROOT + ".notes..");
		rule.check(classes);
	}

}
