/**
 * Notifications module: the in-app messages that tell people about what happened (rules: notification triggers). It owns
 * the notifications, one row for each person told. It reacts, through durable listeners, to the events other modules
 * publish ({@link com.kibootia.backend.rescheduling.TicketChanged}, {@link com.kibootia.backend.issues.IssueMentioned}) and
 * decides who is told what; the audiences of a place come from {@link com.kibootia.backend.access.ScopeHolders}. A person
 * reads and marks only their own. There is no email, push or reminder in this version, and expiry tells nobody.
 */
@ApplicationModule(displayName = "Notifications")
package com.kibootia.backend.notifications;

import org.springframework.modulith.ApplicationModule;
