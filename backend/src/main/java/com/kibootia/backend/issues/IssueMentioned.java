package com.kibootia.backend.issues;

import java.util.Set;
import java.util.UUID;

import com.kibootia.backend.platform.events.DomainEvent;
import com.kibootia.backend.platform.events.EventContext;

/**
 * Faculty were mentioned on an issue, when it was opened or later. Part of the module's public contract: the notifications
 * module reacts to it, through a durable listener, to tell each of them. Only the people newly mentioned are listed.
 *
 * @param mentionedUserIds the accounts of the faculty mentioned; a faculty member with no account cannot be told and is left out
 */
public record IssueMentioned(UUID issueId, UUID openerUserId, Set<UUID> mentionedUserIds, EventContext context)
		implements DomainEvent {

}
