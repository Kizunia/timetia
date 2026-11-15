package com.kibootia.backend.rescheduling.web;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.web.PageQuery;
import com.kibootia.backend.platform.web.PageResponse;
import com.kibootia.backend.rescheduling.application.TicketService;
import com.kibootia.backend.rescheduling.application.TicketService.Applied;
import com.kibootia.backend.rescheduling.application.TicketService.TicketFilter;
import com.kibootia.backend.rescheduling.domain.TicketStatus;

/**
 * Tickets that reschedule or cancel one lecture. A ticket is raised as a draft, checked for conflicts, and applied by its
 * author; applying, which is not plain CRUD, is an action on it (ADR 0043). The use cases decide who may do what and answer
 * a ticket the caller may not see as not found.
 */
@RestController
@RequestMapping("/api/v1/tickets")
class TicketsController {

	/** The code of the 409 for a ticket that conflicts with other lectures. */
	static final String BLOCKED = "rescheduling.blocked_by_conflicts";

	static final String EXPIRED = "rescheduling.ticket_expired";

	private static final Map<String, String> SORTABLE = Map.of("createdAt", "createdAt", "expiresAt", "expiresAt");

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

	private final TicketService tickets;

	TicketsController(TicketService tickets) {
		this.tickets = tickets;
	}

	@PostMapping
	@ApiResponse(responseCode = "201", description = "The draft was raised and checked; `Location` is its URL.")
	ResponseEntity<TicketResponse> create(@Valid @RequestBody CreateTicketRequest request) {
		TicketResponse created = TicketMapper.toResponse(this.tickets.create(TicketMapper.toDraft(request)));
		return ResponseEntity.created(URI.create("/api/v1/tickets/" + created.id())).body(created);
	}

	@GetMapping("/{id}")
	TicketResponse get(@PathVariable UUID id) {
		return TicketMapper.toResponse(this.tickets.get(id));
	}

	/** Replaces the plan of a draft and checks it again. */
	@PutMapping("/{id}")
	TicketResponse edit(@PathVariable UUID id, @Valid @RequestBody UpdateTicketRequest request) {
		return TicketMapper.toResponse(this.tickets.edit(id, TicketMapper.toEdit(request)));
	}

	/**
	 * Applies the ticket by its author, without a review. Answers 409 with each conflict when something is in the way, and
	 * 409 when the lecture started first and the ticket has expired.
	 */
	@PostMapping("/{id}/apply")
	TicketResponse apply(@PathVariable UUID id) {
		Applied result = this.tickets.apply(id);
		return switch (result.outcome()) {
			case APPLIED -> TicketMapper.toResponse(result.detail());
			case BLOCKED -> throw ApiException.conflict(BLOCKED,
					"The change cannot be applied: something is in the way. Nothing was changed.",
					TicketMapper.toErrors(result.detail().conflicts()));
			case EXPIRED -> throw ApiException.conflict(EXPIRED,
					"The lecture started before the ticket was applied, so the ticket has expired.");
		};
	}

	@PostMapping("/{id}/withdraw")
	TicketResponse withdraw(@PathVariable UUID id) {
		return TicketMapper.toResponse(this.tickets.withdraw(id));
	}

	/**
	 * @param status only tickets in this state
	 * @param lecture only tickets that concern this lecture
	 * @param mine only tickets the caller wrote
	 */
	@GetMapping
	PageResponse<TicketResponse> list(@ParameterObject @Valid PageQuery query,
			@RequestParam(required = false) TicketStatus status, @RequestParam(required = false) UUID lecture,
			@RequestParam(defaultValue = "false") boolean mine) {
		return PageResponse.of(
				this.tickets.list(new TicketFilter(status, lecture, mine), query.toPageable(SORTABLE, NEWEST_FIRST)),
				TicketMapper::toResponse);
	}

}
