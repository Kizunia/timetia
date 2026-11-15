package com.kibootia.backend.rescheduling;

import java.util.UUID;

/**
 * What other modules may ask the rescheduling module about tickets without reading its tables: whether the person making
 * the request may see a ticket. The issues module uses it so that an issue is only linked to a ticket its author can see,
 * and a ticket that does not exist or is hidden from them is the same answer.
 */
public interface TicketVisibility {

	/** Whether the ticket exists and the signed-in caller may see it. */
	boolean visibleToCaller(UUID ticketId);

}
