package com.kibootia.backend.platform.archfixtures.bad.events;

import org.springframework.modulith.events.ApplicationModuleListener;

/** A listener that opens its transaction before the tenant is bound. Rule 10 must name it. */
public class LegacyListener {

	@ApplicationModuleListener
	void on(Object event) {
	}

}
