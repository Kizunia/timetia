package com.kibootia.backend;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Architecture rule 5 (ADR 0024) as a rule that can be pointed at any module, real or a test fixture. */
final class ModuleRules {

	private ModuleRules() {
	}

	/**
	 * Nothing outside the module may depend on it, so removing it, or switching it off, cannot break another module.
	 *
	 * @param modulePackage the module's package, for example {@code com.kibootia.backend.notes}
	 */
	static ArchRule nothingOutsideDependsOn(String modulePackage) {
		return noClasses().that()
			.resideOutsideOfPackage(modulePackage + "..")
			.should()
			.dependOnClassesThat()
			.resideInAPackage(modulePackage + "..")
			.allowEmptyShould(true)
			.because("the " + modulePackage + " module is removable; the rest of the application must not need it");
	}

}
