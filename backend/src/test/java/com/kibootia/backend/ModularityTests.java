package com.kibootia.backend;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Modulith verification: no cycles between modules, no access to the internals of another module.
 */
class ModularityTests {

	static final ApplicationModules MODULES = ApplicationModules.of(BackendApplication.class);

	@Test
	void foundationModulesExist() {
		List<String> names = MODULES.stream().map(module -> module.getIdentifier().toString()).sorted().toList();
		assertThat(names).containsExactly("access", "audit", "files", "identity", "notes", "platform");
	}

	@Test
	void moduleStructureIsValid() {
		MODULES.verify();
	}

}
