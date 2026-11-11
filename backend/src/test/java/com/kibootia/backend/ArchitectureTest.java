package com.kibootia.backend;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit rules from kibootia-docs/architecture/architecture-enforcement.md. Rule 5 covers every removable module in
 * {@link #REMOVABLE_MODULES}; rules 2 and 3 are covered by Spring Modulith ({@code ModularityTest}). Because the notes
 * module can be removed, a rule must not need any particular feature module to exist: a rule about a module that is
 * absent simply has nothing to check.
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
	 * The default-on modules a fork may remove or switch off (ADR 0024). Adding a module to this list is how a new
	 * removable module comes under rule 5.
	 */
	static final List<String> REMOVABLE_MODULES = List.of("files", "notes");

	/**
	 * Rule 5 for every removable default-on module (ADR 0024): nothing outside it depends on it, so removing it, or
	 * switching it off, cannot break another module. Others reach {@code files} through the {@code platform.files} port;
	 * nothing reaches {@code notes}, which is only a consumer.
	 */
	@TestFactory
	Stream<DynamicTest> rule5_nothingOutsideARemovableModuleDependsOnIt() {
		return REMOVABLE_MODULES.stream()
			.map((module) -> DynamicTest.dynamicTest(module, () -> ModuleRules.nothingOutsideDependsOn(ROOT + "." + module).check(classes)));
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
