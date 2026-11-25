package com.kibootia.backend.rescheduling.web;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
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
import com.kibootia.backend.rescheduling.application.Progress;
import com.kibootia.backend.rescheduling.application.ReviewerCandidateService;
import com.kibootia.backend.rescheduling.application.TicketDetail;
import com.kibootia.backend.rescheduling.application.TicketNameService;
import com.kibootia.backend.rescheduling.application.TicketNames;
import com.kibootia.backend.rescheduling.application.TicketReviewService;
import com.kibootia.backend.rescheduling.application.TicketRevertService;
import com.kibootia.backend.rescheduling.application.TicketService;
import com.kibootia.backend.rescheduling.application.TicketService.TicketFilter;
import com.kibootia.backend.rescheduling.domain.TicketStatus;

/**
 * Tickets that reschedule or cancel one lecture. A ticket is raised as a draft and checked for conflicts; its author applies
 * it, or sends it for review and it is applied when the people asked have answered (ADR 0043: what is not plain CRUD is an
 * action on the ticket). The use cases decide who may do what and answer a ticket the caller may not see as not found.
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

	private final TicketReviewService reviews;

	private final TicketRevertService reverts;

	private final TicketNameService names;

	private final ReviewerCandidateService candidates;

	TicketsController(TicketService tickets, TicketReviewService reviews, TicketRevertService reverts,
			TicketNameService names, ReviewerCandidateService candidates) {
		this.tickets = tickets;
		this.reviews = reviews;
		this.reverts = reverts;
		this.names = names;
		this.candidates = candidates;
	}

	@PostMapping
	@ApiResponse(responseCode = "201", description = "The draft was raised and checked; `Location` is its URL.")
	ResponseEntity<TicketResponse> create(@Valid @RequestBody CreateTicketRequest request) {
		TicketResponse created = respond(this.tickets.create(TicketMapper.toDraft(request)));
		return ResponseEntity.created(URI.create("/api/v1/tickets/" + created.id())).body(created);
	}

	@GetMapping("/{id}")
	TicketResponse get(@PathVariable UUID id) {
		return respond(this.tickets.get(id));
	}

	/**
	 * The people the author may ask to review this ticket: the HODs of its department, the CR or BR of its class, and the
	 * faculty it affects, each with the capacity to name when asking. Only the author may ask; the author is never listed.
	 */
	@GetMapping("/{id}/reviewer-candidates")
	List<ReviewerCandidateResponse> reviewerCandidates(@PathVariable UUID id) {
		return this.candidates.candidates(id)
			.stream()
			.map((candidate) -> new ReviewerCandidateResponse(candidate.userId(), candidate.name(), candidate.capacity()))
			.toList();
	}

	/**
	 * Replaces the plan of a ticket that is not yet applied and checks it again. Editing a rejected ticket makes it a draft
	 * again, one revision later; editing one under review clears the approvals if the destination changed.
	 */
	@PutMapping("/{id}")
	TicketResponse edit(@PathVariable UUID id, @Valid @RequestBody UpdateTicketRequest request) {
		return respond(this.tickets.edit(id, TicketMapper.toEdit(request)));
	}

	/**
	 * Applies the ticket by its author: a draft without a review, a ticket under review once everyone asked has answered and
	 * none rejected it. Answers 409 with each conflict when something is in the way, and 409 when the lecture started first
	 * and the ticket has expired.
	 */
	@PostMapping("/{id}/apply")
	TicketResponse apply(@PathVariable UUID id) {
		Progress result = this.tickets.apply(id);
		return switch (result.outcome()) {
			case APPLIED, DONE -> respond(result.detail());
			case BLOCKED -> throw ApiException.conflict(BLOCKED,
					"The change cannot be applied: something is in the way. Nothing was changed.",
					TicketMapper.toErrors(result.detail().conflicts()));
			case EXPIRED -> throw expired();
		};
	}

	@PostMapping("/{id}/withdraw")
	TicketResponse withdraw(@PathVariable UUID id) {
		return respond(this.tickets.withdraw(id));
	}

	/** Sends a draft for review by the people named. A ticket with a conflict can still be sent. */
	@PostMapping("/{id}/submit")
	TicketResponse submit(@PathVariable UUID id, @Valid @RequestBody SubmitTicketRequest request) {
		return answered(this.reviews.submit(id, TicketMapper.toReviewers(request.reviewers())));
	}

	/**
	 * A requested reviewer approves, acknowledges or rejects the ticket. The last answer that completes the review applies the
	 * ticket; if something is in the way it stays under review, flagged, and this still answers 200 because the review was
	 * recorded.
	 */
	@PostMapping("/{id}/review")
	TicketResponse review(@PathVariable UUID id, @Valid @RequestBody ReviewRequest request) {
		return answered(this.reviews.review(id, request.decision(), request.reason()));
	}

	/**
	 * Raises, as a new draft ticket, the revert of an applied ticket: it restores the lectures to where, and with whom, they
	 * were. Apply it with {@code /apply}; if the original slot was taken meanwhile it is blocked until that is resolved.
	 */
	@PostMapping("/{id}/revert")
	@ApiResponse(responseCode = "201", description = "The revert was raised as a new ticket and checked; `Location` is its URL.")
	ResponseEntity<TicketResponse> revert(@PathVariable UUID id, @Valid @RequestBody(required = false) RevertRequest request) {
		TicketResponse created = respond(this.reverts.revert(id, (request == null) ? null : request.reason()));
		return ResponseEntity.created(URI.create("/api/v1/tickets/" + created.id())).body(created);
	}

	/** The author takes a rejection to the HOD, who decides. */
	@PostMapping("/{id}/escalate")
	TicketResponse escalate(@PathVariable UUID id, @Valid @RequestBody EscalateRequest request) {
		return answered(this.reviews.escalate(id, request.hodUserId()));
	}

	/**
	 * @param status only tickets in this state
	 * @param lecture only tickets that concern this lecture
	 * @param mine only tickets the caller wrote
	 * @param awaitingReview only tickets under review where the caller has been asked and has not answered yet
	 */
	@GetMapping
	PageResponse<TicketResponse> list(@ParameterObject @Valid PageQuery query,
			@RequestParam(required = false) TicketStatus status, @RequestParam(required = false) UUID lecture,
			@RequestParam(defaultValue = "false") boolean mine,
			@RequestParam(defaultValue = "false") boolean awaitingReview) {
		Page<TicketDetail> page = this.tickets.list(new TicketFilter(status, lecture, mine, awaitingReview),
				query.toPageable(SORTABLE, NEWEST_FIRST));
		// One lookup of each kind of name for the whole page, not one for each row.
		TicketNames found = this.names.namesFor(page.getContent(), false);
		return PageResponse.of(page, (detail) -> TicketMapper.toResponse(detail, found));
	}

	/** The ticket with the names behind its ids. For one ticket the changed lectures are read as well. */
	private TicketResponse respond(TicketDetail detail) {
		return TicketMapper.toResponse(detail, this.names.namesFor(List.of(detail), true));
	}

	/** The answer to an action that was done: the ticket as it is now, unless the lecture started first. */
	private TicketResponse answered(Progress result) {
		if (result.outcome() == Progress.Outcome.EXPIRED) {
			throw expired();
		}
		return respond(result.detail());
	}

	private static ApiException expired() {
		return ApiException.conflict(EXPIRED, "The lecture started before the ticket was applied, so the ticket has expired.");
	}

}
