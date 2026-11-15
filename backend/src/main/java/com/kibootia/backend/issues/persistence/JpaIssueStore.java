package com.kibootia.backend.issues.persistence;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import com.kibootia.backend.issues.application.Issue;
import com.kibootia.backend.issues.application.IssueQuery;
import com.kibootia.backend.issues.application.IssueStore;
import com.kibootia.backend.issues.application.NewIssue;

/** Keeps issues, their mentions and their ticket links in MySQL through JPA. */
@Component
class JpaIssueStore implements IssueStore {

	private final IssueRepository issues;

	private final IssueMentionRepository mentions;

	private final IssueLinkRepository links;

	JpaIssueStore(IssueRepository issues, IssueMentionRepository mentions, IssueLinkRepository links) {
		this.issues = issues;
		this.mentions = mentions;
		this.links = links;
	}

	@Override
	public Issue insert(NewIssue issue) {
		IssueEntity saved = this.issues.saveAndFlush(new IssueEntity(issue));
		issue.mentionedFacultyIds().forEach((faculty) -> this.mentions.save(new IssueMentionEntity(saved.getId(), faculty)));
		this.mentions.flush();
		return IssueMapper.toModel(saved);
	}

	@Override
	public Optional<Issue> find(UUID id) {
		return this.issues.findById(id).map(IssueMapper::toModel);
	}

	@Override
	public Issue update(Issue issue) {
		IssueEntity entity = this.issues.findById(issue.id()).orElseThrow();
		if (entity.getVersion() != issue.version()) {
			throw new ObjectOptimisticLockingFailureException(IssueEntity.class, issue.id());
		}
		entity.update(issue);
		return IssueMapper.toModel(this.issues.saveAndFlush(entity));
	}

	@Override
	public Set<UUID> mentionedFacultyIds(UUID issueId) {
		return mentionedFacultyIdsOf(Set.of(issueId)).getOrDefault(issueId, Set.of());
	}

	@Override
	public Map<UUID, Set<UUID>> mentionedFacultyIdsOf(Collection<UUID> issueIds) {
		if (issueIds.isEmpty()) {
			return Map.of();
		}
		return this.mentions.findByIssueIdIn(issueIds)
			.stream()
			.collect(Collectors.groupingBy(IssueMentionEntity::getIssueId,
					Collectors.mapping(IssueMentionEntity::getMentionedFacultyId, Collectors.toCollection(HashSet::new))));
	}

	@Override
	public Set<UUID> linkedTicketIds(UUID issueId) {
		return linkedTicketIdsOf(Set.of(issueId)).getOrDefault(issueId, Set.of());
	}

	@Override
	public Map<UUID, Set<UUID>> linkedTicketIdsOf(Collection<UUID> issueIds) {
		if (issueIds.isEmpty()) {
			return Map.of();
		}
		return this.links.findByIssueIdIn(issueIds)
			.stream()
			.collect(Collectors.groupingBy(IssueLinkEntity::getIssueId,
					Collectors.mapping(IssueLinkEntity::getTicketId, Collectors.toCollection(HashSet::new))));
	}

	@Override
	public boolean addMention(UUID issueId, UUID facultyId) {
		if (this.mentions.existsByIssueIdAndMentionedFacultyId(issueId, facultyId)) {
			return false;
		}
		this.mentions.saveAndFlush(new IssueMentionEntity(issueId, facultyId));
		return true;
	}

	@Override
	public boolean addLink(UUID issueId, UUID ticketId, UUID linkedByUserId) {
		if (this.links.existsByIssueIdAndTicketId(issueId, ticketId)) {
			return false;
		}
		this.links.saveAndFlush(new IssueLinkEntity(issueId, ticketId, linkedByUserId));
		return true;
	}

	@Override
	public Page<Issue> search(IssueQuery query, Pageable pageable) {
		return this.issues.findAll(IssueSpecifications.matching(query), pageable).map(IssueMapper::toModel);
	}

}
