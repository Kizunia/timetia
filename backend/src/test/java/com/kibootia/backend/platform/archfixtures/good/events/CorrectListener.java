package com.kibootia.backend.platform.archfixtures.good.events;

import com.kibootia.backend.platform.events.DomainEvent;
import com.kibootia.backend.platform.events.DurableEventListener;
import com.kibootia.backend.platform.events.EventContext;

/** A durable listener that takes a domain event. Rule 11 must accept it. */
public class CorrectListener {

	public record Happened(EventContext context) implements DomainEvent {

	}

	@DurableEventListener
	void on(Happened event) {
	}

}
