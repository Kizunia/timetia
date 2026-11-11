package com.kibootia.backend.platform.archfixtures.rule5.core;

import com.kibootia.backend.platform.archfixtures.rule5.removable.Widget;

/** Core code that needs a removable module, so the module could not be removed. Rule 5 must name it. */
public class DependsOnRemovable {

	private final Widget widget = new Widget();

	public Widget widget() {
		return this.widget;
	}

}
