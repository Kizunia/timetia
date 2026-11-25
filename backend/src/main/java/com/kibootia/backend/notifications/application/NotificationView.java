package com.kibootia.backend.notifications.application;

import java.util.UUID;

/**
 * A notification with what a screen needs to put it into words and link it somewhere.
 *
 * @param actorName who caused it; null when nobody did or the account is gone
 * @param ticketType the kind of ticket it is about; null for an issue
 * @param lectureId the lecture the ticket is about, so that someone who may not open tickets can still see the lecture
 * @param ticketVisible whether the person may open this ticket. Being told about a ticket does not make it visible: a batch
 *        representative who is also a student is told of a theory change and cannot open its ticket
 */
public record NotificationView(Notification notification, String actorName, String ticketType, UUID lectureId,
		boolean ticketVisible) {

}
