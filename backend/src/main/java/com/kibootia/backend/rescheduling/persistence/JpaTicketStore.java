package com.kibootia.backend.rescheduling.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import com.kibootia.backend.rescheduling.application.NewReview;
import com.kibootia.backend.rescheduling.application.NewTicket;
import com.kibootia.backend.rescheduling.application.Ticket;
import com.kibootia.backend.rescheduling.application.TicketChange;
import com.kibootia.backend.rescheduling.application.TicketQuery;
import com.kibootia.backend.rescheduling.application.TicketReview;
import com.kibootia.backend.rescheduling.application.TicketStore;
import com.kibootia.backend.rescheduling.domain.CheckTrigger;
import com.kibootia.backend.rescheduling.domain.ReviewStatus;
import com.kibootia.backend.rescheduling.domain.TicketStatus;
import com.kibootia.backend.timetable.LectureConflict;
import com.kibootia.backend.timetable.LectureSlot;

/** Keeps tickets, their changes and the conflict checks made on them in MySQL through JPA. */
@Component
class JpaTicketStore implements TicketStore {

	private final TicketRepository tickets;

	private final TicketChangeRepository changes;

	private final ConflictCheckRunRepository runs;

	private final ConflictFindingRepository findings;

	private final TicketReviewRepository reviews;

	JpaTicketStore(TicketRepository tickets, TicketChangeRepository changes, ConflictCheckRunRepository runs,
			ConflictFindingRepository findings, TicketReviewRepository reviews) {
		this.tickets = tickets;
		this.changes = changes;
		this.runs = runs;
		this.findings = findings;
		this.reviews = reviews;
	}

	@Override
	public Ticket insert(NewTicket ticket) {
		TicketEntity saved = this.tickets.saveAndFlush(new TicketEntity(ticket));
		List<NewTicket.Change> planned = ticket.changes();
		for (int i = 0; i < planned.size(); i++) {
			this.changes.save(new TicketChangeEntity(saved.getId(), i + 1, planned.get(i)));
		}
		this.changes.flush();
		return TicketMapper.toModel(saved);
	}

	@Override
	public Optional<Ticket> find(UUID id) {
		return this.tickets.findById(id).map(TicketMapper::toModel);
	}

	@Override
	public List<TicketChange> changesOf(UUID ticketId) {
		return this.changes.findByTicketIdOrderByItemNoAsc(ticketId).stream().map(TicketMapper::toModel).toList();
	}

	@Override
	public Map<UUID, List<TicketChange>> changesOf(Collection<UUID> ticketIds) {
		if (ticketIds.isEmpty()) {
			return Map.of();
		}
		return this.changes.findByTicketIdIn(ticketIds)
			.stream()
			.sorted(Comparator.comparingInt(TicketChangeEntity::getItemNo))
			.collect(Collectors.groupingBy(TicketChangeEntity::getTicketId,
					Collectors.mapping(TicketMapper::toModel, Collectors.toList())));
	}

	@Override
	public Ticket update(Ticket ticket) {
		TicketEntity entity = this.tickets.findById(ticket.id()).orElseThrow();
		if (entity.getVersion() != ticket.version()) {
			throw new ObjectOptimisticLockingFailureException(TicketEntity.class, ticket.id());
		}
		entity.update(ticket);
		return TicketMapper.toModel(this.tickets.saveAndFlush(entity));
	}

	@Override
	public void replaceChanges(UUID ticketId, List<NewTicket.Change> replacements) {
		List<TicketChangeEntity> entities = this.changes.findByTicketIdOrderByItemNoAsc(ticketId);
		if (entities.size() != replacements.size()) {
			throw new IllegalStateException("A ticket keeps the number of changes it was raised with");
		}
		for (int i = 0; i < entities.size(); i++) {
			entities.get(i).set(replacements.get(i));
		}
		this.changes.saveAllAndFlush(entities);
	}

	@Override
	public void recordBefore(UUID changeId, LectureSlot before, UUID beforeFacultyId) {
		TicketChangeEntity entity = this.changes.findById(changeId).orElseThrow();
		entity.recordBefore(before, beforeFacultyId);
		this.changes.saveAndFlush(entity);
	}

	@Override
	public void recordCreated(UUID changeId, UUID occurrenceId) {
		TicketChangeEntity entity = this.changes.findById(changeId).orElseThrow();
		entity.recordCreated(occurrenceId);
		this.changes.saveAndFlush(entity);
	}

	@Override
	public Page<Ticket> search(TicketQuery query, Pageable pageable) {
		return this.tickets.findAll(TicketSpecifications.matching(query), pageable).map(TicketMapper::toModel);
	}

	@Override
	public void recordCheck(UUID ticketId, CheckTrigger trigger, List<LectureConflict> conflicts) {
		ConflictCheckRunEntity run = this.runs.saveAndFlush(new ConflictCheckRunEntity(ticketId, trigger, !conflicts.isEmpty()));
		this.findings.saveAll(conflicts.stream().map((conflict) -> new ConflictFindingEntity(run.getId(), ticketId, conflict)).toList());
		this.findings.flush();
	}

	@Override
	public List<LectureConflict> latestCheck(UUID ticketId) {
		return this.runs.findFirstByTicketIdOrderByCreatedAtDescIdDesc(ticketId)
			.map((run) -> this.findings.findByRunIdOrderByIdAsc(run.getId())
				.stream()
				.map(ConflictFindingEntity::toConflict)
				.toList())
			.orElse(List.of());
	}

	// --- reviews ----------------------------------------------------------------------------------------------------

	@Override
	public List<TicketReview> reviewsOf(UUID ticketId) {
		return this.reviews.findByTicketIdOrderByTicketRevisionNoAscIdAsc(ticketId)
			.stream()
			.map(TicketMapper::toModel)
			.toList();
	}

	@Override
	public Set<UUID> reviewerUserIds(UUID ticketId, int revisionNo) {
		Set<UUID> asked = new HashSet<>();
		this.reviews.findByTicketIdAndTicketRevisionNo(ticketId, revisionNo)
			.forEach((review) -> asked.add(review.getReviewerUserId()));
		return asked;
	}

	@Override
	public List<TicketReview> insertReviews(UUID ticketId, int revisionNo, List<NewReview> asked) {
		List<TicketReviewEntity> saved = this.reviews
			.saveAll(asked.stream().map((review) -> new TicketReviewEntity(ticketId, revisionNo, review)).toList());
		this.reviews.flush();
		return saved.stream().map(TicketMapper::toModel).toList();
	}

	@Override
	public TicketReview decide(UUID reviewId, ReviewStatus status, String reason, Instant at) {
		TicketReviewEntity entity = this.reviews.findById(reviewId).orElseThrow();
		entity.decide(status, reason, at);
		return TicketMapper.toModel(this.reviews.saveAndFlush(entity));
	}

	@Override
	public void resetDecisions(UUID ticketId, int revisionNo) {
		List<TicketReviewEntity> answered = this.reviews.findByTicketIdAndTicketRevisionNo(ticketId, revisionNo);
		answered.forEach(TicketReviewEntity::reset);
		this.reviews.saveAllAndFlush(answered);
	}

	@Override
	public void syncReviewers(UUID ticketId, int revisionNo, List<NewReview> reviewers) {
		List<TicketReviewEntity> existing = this.reviews.findByTicketIdAndTicketRevisionNo(ticketId, revisionNo);
		Set<String> wanted = new HashSet<>();
		reviewers.forEach((review) -> wanted.add(review.reviewerUserId() + ":" + review.capacity()));
		Set<String> kept = new HashSet<>();
		for (TicketReviewEntity entity : existing) {
			String key = entity.getReviewerUserId() + ":" + entity.getCapacity();
			if (wanted.contains(key)) {
				kept.add(key);
			}
			else {
				this.reviews.delete(entity);
			}
		}
		this.reviews.flush();
		insertReviews(ticketId, revisionNo, reviewers.stream()
			.filter((review) -> !kept.contains(review.reviewerUserId() + ":" + review.capacity()))
			.toList());
	}

	// --- finding tickets --------------------------------------------------------------------------------------------

	@Override
	public boolean hasOpenRevert(UUID ticketId) {
		return this.tickets.existsByRevertsTicketIdAndStatusIn(ticketId, List.of(TicketStatus.DRAFT, TicketStatus.IN_REVIEW));
	}

	@Override
	public List<Ticket> inReviewAffectedBy(Set<UUID> lectureIds, Set<LocalDate> dates) {
		// An IN with no values is not valid SQL; a date or a lecture no ticket can want matches none.
		Collection<LocalDate> askedDates = dates.isEmpty() ? List.of(LocalDate.EPOCH) : dates;
		Collection<UUID> askedLectures = lectureIds.isEmpty() ? List.of(new UUID(0, 0)) : lectureIds;
		return this.tickets.inStateAffectedBy(TicketStatus.IN_REVIEW, askedLectures, askedDates)
			.stream()
			.map(TicketMapper::toModel)
			.toList();
	}

	@Override
	public List<Ticket> openExpiredBy(Instant instant, int limit) {
		return this.tickets
			.findByStatusInAndExpiresAtLessThanEqualOrderByExpiresAtAsc(List.of(TicketStatus.DRAFT, TicketStatus.IN_REVIEW),
					instant, Limit.of(limit))
			.stream()
			.map(TicketMapper::toModel)
			.toList();
	}

}
