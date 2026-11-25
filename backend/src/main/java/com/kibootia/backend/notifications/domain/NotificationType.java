package com.kibootia.backend.notifications.domain;

/** What a notification is about (data model {@code notification_type}); the interface shows the words. */
public enum NotificationType {

	/** You are asked to review a ticket. */
	REVIEW_REQUESTED,

	/** A reviewer approved your ticket. */
	REVIEW_APPROVED,

	/** A reviewer rejected your ticket. */
	REVIEW_REJECTED,

	/** A change was applied that concerns you. */
	TICKET_APPLIED,

	/** Something is in the way of a ticket. */
	TICKET_CONFLICTED,

	/** A ticket you were asked to review was edited and needs your answer again. */
	TICKET_EDITED,

	/** A ticket you were asked to review was withdrawn. */
	TICKET_WITHDRAWN,

	/** A change that concerned you was undone. */
	TICKET_REVERTED,

	/** An issue mentions you. */
	ISSUE_MENTION

}
