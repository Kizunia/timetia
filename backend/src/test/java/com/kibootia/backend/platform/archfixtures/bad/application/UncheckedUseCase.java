package com.kibootia.backend.platform.archfixtures.bad.application;

import org.springframework.boot.test.context.TestComponent;

/** A use case with a public method and no authorization check. Rule 7 must name it. Not a bean (see ArchitectureRulesFireTest). */
@TestComponent
public class UncheckedUseCase {

	public void save() {
	}

}
