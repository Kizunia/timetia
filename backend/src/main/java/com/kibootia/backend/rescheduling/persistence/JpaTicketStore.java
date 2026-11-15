package com.kibootia.backend.rescheduling.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import com.kibootia.backend.rescheduling.application.NewTicket;
import com.kibootia.backend.rescheduling.application.Ticket;
import com.kibootia.backend.rescheduling.application.TicketChange;
import com.kibootia.backend.rescheduling.application.TicketQuery;
import com.kibootia.backend.rescheduling.application.TicketStore;
import com.kibootia.backend.rescheduling.domain.CheckTrigger;
import com.kibootia.backend.timetable.LectureConflict;
import com.kibootia.backend.timetable.LectureSlot;

/** Keeps tickets, their changes and the conflict checks made on them in MySQL through JPA. */
@Component
class JpaTicketStore implements TicketStore {

	private final TicketRepository tickets;

	private final TicketChangeRepository changes;

	private final ConflictCheckRunRepository runs;

	private final ConflictFindingRepository findings;

	JpaTicketStore(TicketRepository tickets, TicketChangeRepository changes, ConflictCheckRunRepository runs,
			ConflictFindingRepository findings) {
		this.tickets = tickets;
		this.changes = changes;
		this.runs = runs;
		this.findings = findings;
	}

	@Override
	public Ticket insert(NewTicket ticket) {
		TicketEntity saved = this.tickets.saveAndFlush(new TicketEntity(ticket));
		this.changes.saveAndFlush(new TicketChangeEntity(saved.getId(), ticket.change()));
		return TicketMapper.toModel(saved);
	}

	@Override
	public Optional<Ticket> find(UUID id) {
		return this.tickets.findById(id).map(TicketMapper::toModel);
	}

	@Override
	public TicketChange changeOf(UUID ticketId) {
		return TicketMapper.toModel(this.changes.findFirstByTicketIdOrderByIdAsc(ticketId).orElseThrow());
	}

	@Override
	public Map<UUID, TicketChange> changesOf(Collection<UUID> ticketIds) {
		if (ticketIds.isEmpty()) {
			return Map.of();
		}
		return this.changes.findByTicketIdIn(ticketIds)
			.stream()
			.map(TicketMapper::toModel)
			.collect(Collectors.toMap(TicketChange::ticketId, (change) -> change, (first, second) -> first));
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
	public TicketChange replaceChange(UUID ticketId, NewTicket.Change change) {
		TicketChangeEntity entity = this.changes.findFirstByTicketIdOrderByIdAsc(ticketId).orElseThrow();
		entity.set(change);
		return TicketMapper.toModel(this.changes.saveAndFlush(entity));
	}

	@Override
	public void recordBefore(UUID ticketId, LectureSlot before) {
		TicketChangeEntity entity = this.changes.findFirstByTicketIdOrderByIdAsc(ticketId).orElseThrow();
		entity.recordBefore(before);
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

}
