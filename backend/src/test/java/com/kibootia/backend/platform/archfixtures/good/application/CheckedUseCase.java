package com.kibootia.backend.platform.archfixtures.good.application;

import org.springframework.boot.test.context.TestComponent;

import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.authorization.RequiresPermission;

/** A use case where every public method is checked or explicitly marked. Rule 7 must accept it. */
@TestComponent
public class CheckedUseCase {

	@RequiresPermission("fixtures.things.create")
	public void create() {
	}

	@NoAuthorization("a constant label")
	public String name() {
		return "checked";
	}

}
