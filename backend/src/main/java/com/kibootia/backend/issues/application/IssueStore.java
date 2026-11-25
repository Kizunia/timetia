package com.kibootia.backend.issues.application;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Where issues, their mentions and their links are kept. The use cases depend on this port, never on JPA (ADR 0003). */
public interface IssueStore {

	/** Stores a new open issue with the faculty it mentions. */
	Issue insert(NewIssue issue);

	Optional<Issue> find(UUID id);

	/** Saves the state of an issue if it is still the version that was read; otherwise fails with an optimistic locking failure. */
	Issue update(Issue issue);

	Set<UUID> mentionedFacultyIds(UUID issueId);

	/** The mentioned faculty of these issues, by issue, so a page costs one query. */
	Map<UUID, Set<UUID>> mentionedFacultyIdsOf(Collection<UUID> issueIds);

	Set<UUID> linkedTicketIds(UUID issueId);

	Map<UUID, Set<UUID>> linkedTicketIdsOf(Collection<UUID> issueIds);

	/** Adds a mention; false when the faculty member was already mentioned. */
	boolean addMention(UUID issueId, UUID facultyId);

	/** Adds a link; false when the ticket was already linked. */
	boolean addLink(UUID issueId, UUID ticketId, UUID linkedByUserId);

	Page<Issue> search(IssueQuery query, Pageable pageable);

}
