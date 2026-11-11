package com.kibootia.backend;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Modulith verification: no cycles between modules, no access to the internals of another module.
 */
class ModularityTest {

	static final ApplicationModules MODULES = ApplicationModules.of(BackendApplication.class);

	/**
	 * The core modules are always there. The default-on modules ({@code files}, {@code notes}) are not required, because
	 * a fork may remove them (ADR 0024); what they may and may not depend on is checked by rule 5 in
	 * {@code ArchitectureTest}, and the verification below covers whatever modules exist.
	 */
	@Test
	void coreModulesExist() {
		List<String> names = MODULES.stream().map(module -> module.getIdentifier().toString()).sorted().toList();
		assertThat(names).contains("access", "audit", "identity", "platform");
	}

	/** Every module is one of the known ones, so a stray package cannot become a module unnoticed. */
	@Test
	void everyModuleIsKnown() {
		List<String> names = MODULES.stream().map(module -> module.getIdentifier().toString()).sorted().toList();
		assertThat(names).isSubsetOf("access", "audit", "files", "identity", "notes", "platform");
	}

	@Test
	void moduleStructureIsValid() {
		MODULES.verify();
	}

	/**
	 * Verification only means something if the modules depend on each other: with no edges there is no cycle to find and no
	 * internal access to refuse. Spring Modulith ignores test classes, so a broken module cannot be fed to it from the test
	 * sources; this shows instead that the real graph has edges, among the core modules that are always present.
	 */
	@Test
	void coreModulesDependOnEachOtherSoVerificationHasSomethingToCheck() {
		assertThat(dependenciesOf("identity")).contains("access", "platform");
		assertThat(dependenciesOf("access")).contains("platform");
		assertThat(dependenciesOf("audit")).contains("platform");
		assertThat(dependenciesOf("platform")).as("platform depends on no other module (rule 8)").isEmpty();
	}

	private static List<String> dependenciesOf(String module) {
		return MODULES.getModuleByName(module)
			.orElseThrow()
			.getDirectDependencies(MODULES)
			.uniqueModules()
			.map((dependency) -> dependency.getIdentifier().toString())
			.sorted()
			.toList();
	}

}
