package com.kibootia.backend.audit.web;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.kibootia.backend.audit.application.FailedEventService;
import com.kibootia.backend.platform.web.PageQuery;
import com.kibootia.backend.platform.web.PageResponse;

/** Events that keep failing and their retry (ADR 0031). Administrators only; there is no screen yet. */
@RestController
@RequestMapping("/api/v1/failed-events")
class FailedEventsController {

	private final FailedEventService service;

	FailedEventsController(FailedEventService service) {
		this.service = service;
	}

	/** Oldest first. The order is fixed, so no sort field is accepted. */
	@GetMapping
	PageResponse<FailedEventResponse> list(@Valid PageQuery query) {
		return PageResponse.of(this.service.list(query.toPageable(Map.of(), Sort.unsorted())),
				FailedEventMapper::toResponse);
	}

	/** Redelivers the event. The delivery runs afterwards, so the answer is 202 Accepted. */
	@PostMapping("/{id}/retry")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void retry(@PathVariable UUID id) {
		this.service.retry(id);
	}

}
