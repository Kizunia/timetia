package com.kibootia.backend.notifications.application;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.kibootia.backend.academic.AcademicScopes;
import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.access.ScopeHolders;
import com.kibootia.backend.notifications.domain.NotificationType;
import com.kibootia.backend.rescheduling.TicketChanged;

/**
 * The rules of who is told what when something happens to a ticket (rules: ticket lifecycle, "Notifications per
 * transition"), kept in one place. Package-private: it decides nothing about who may see a notification, only who receives one.
 *
 * <p>When a change is applied or undone, everyone it concerns is told: the author, the faculty involved, the HOD of the
 * department, the class representative of the division for a theory lecture, and the batch representatives and students of
 * the batches the lecture is for. A CR is not told of a lab, nor a BR of a batch the lecture is not for, which is the session
 * rule of the roles. Expiry tells nobody.
 */
@Component
class Audience {

	private final ScopeHolders holders;

	Audience(ScopeHolders holders) {
		this.holders = holders;
	}

	/** What a ticket event is called to the people told, or null when it tells nobody. */
	static NotificationType typeOf(TicketChanged.Kind kind) {
		return switch (kind) {
			case REVIEW_REQUESTED -> NotificationType.REVIEW_REQUESTED;
			case REVIEW_APPROVED -> NotificationType.REVIEW_APPROVED;
			case REVIEW_REJECTED -> NotificationType.REVIEW_REJECTED;
			case APPLIED -> NotificationType.TICKET_APPLIED;
			case CONFLICTED -> NotificationType.TICKET_CONFLICTED;
			case EDITED -> NotificationType.TICKET_EDITED;
			case WITHDRAWN -> NotificationType.TICKET_WITHDRAWN;
			case REVERTED -> NotificationType.TICKET_REVERTED;
		};
	}

	/** The people a ticket event tells. */
	Set<UUID> forTicket(TicketChanged event) {
		Set<UUID> people = new HashSet<>();
		switch (event.kind()) {
			case REVIEW_REQUESTED, EDITED, WITHDRAWN -> people.addAll(event.reviewerUserIds());
			case REVIEW_APPROVED, REVIEW_REJECTED -> people.add(event.authorUserId());
			case CONFLICTED -> {
				people.add(event.authorUserId());
				people.addAll(headsOf(event));
			}
			case APPLIED, REVERTED -> {
				people.add(event.authorUserId());
				people.addAll(event.facultyUserIds());
				people.addAll(headsOf(event));
				people.addAll(classOf(event));
			}
		}
		return people;
	}

	/** The HOD of the department. */
	private Set<UUID> headsOf(TicketChanged event) {
		return union(this.holders.usersIn(AcademicScopes.DEPARTMENT, Set.of(event.departmentId())));
	}

	/** The CR of the division, for a theory lecture only, and the BR and students of the batches the lecture is for. */
	private Set<UUID> classOf(TicketChanged event) {
		Set<UUID> people = new HashSet<>();
		if (event.sessionType() == SessionType.THEORY) {
			people.addAll(union(this.holders.usersIn(AcademicScopes.DIVISION, Set.of(event.divisionId()))));
		}
		if (!event.batchIds().isEmpty()) {
			people.addAll(union(this.holders.usersIn(AcademicScopes.BATCH, event.batchIds())));
		}
		return people;
	}

	private static Set<UUID> union(Map<UUID, Set<UUID>> byPlace) {
		Set<UUID> all = new HashSet<>();
		byPlace.values().forEach(all::addAll);
		return all;
	}

}
