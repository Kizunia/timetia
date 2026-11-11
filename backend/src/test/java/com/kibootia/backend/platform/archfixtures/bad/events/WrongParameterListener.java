package com.kibootia.backend.platform.archfixtures.bad.events;

import com.kibootia.backend.platform.events.DurableEventListener;

/** A durable listener that does not take a domain event, so the tenant cannot be restored. Rule 11 must name it. */
public class WrongParameterListener {

	@DurableEventListener
	void on(String notAnEvent) {
	}

}
