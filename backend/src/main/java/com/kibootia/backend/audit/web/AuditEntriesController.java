package com.kibootia.backend.audit.web;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kibootia.backend.audit.application.AuditEntryStore;
import com.kibootia.backend.audit.application.AuditRecord;
import com.kibootia.backend.audit.application.AuditViewer;
import com.kibootia.backend.platform.web.PageQuery;
import com.kibootia.backend.platform.web.PageResponse;

/**
 * The audit log for administrators (ADR 0046): read only, newest first. There is no way to change or delete an entry
 * through the API; only the retention purge removes them.
 */
@RestController
@RequestMapping("/api/v1/audit-entries")
class AuditEntriesController {

	/** API field name to entity property, for every field a client may sort by. */
	private static final Map<String, String> SORTABLE = Map.of("occurredAt", "occurredAt", "action", "action");

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "occurredAt");

	private final AuditViewer viewer;

	AuditEntriesController(AuditViewer viewer) {
		this.viewer = viewer;
	}

	/**
	 * @param actorId only what this user did
	 * @param action only entries whose action code starts with this, for example {@code access.} for a whole module
	 * @param targetType only entries about this kind of record, for example {@code user}
	 * @param targetId only entries about this record
	 * @param from only entries at or after this time
	 * @param to only entries before this time
	 */
	@GetMapping
	PageResponse<AuditEntryResponse> list(@ParameterObject @Valid PageQuery query,
			@RequestParam(required = false) UUID actorId, @RequestParam(required = false) @Size(max = 100) String action,
			@RequestParam(required = false) @Size(max = 50) String targetType,
			@RequestParam(required = false) UUID targetId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
		AuditEntryStore.Filter filter = new AuditEntryStore.Filter(actorId, action, targetType, targetId, from, to);
		return PageResponse.of(this.viewer.search(filter, query.toPageable(SORTABLE, NEWEST_FIRST)),
				AuditEntriesController::toResponse);
	}

	private static AuditEntryResponse toResponse(AuditRecord record) {
		return new AuditEntryResponse(record.id(), record.occurredAt(), person(record.actor()), record.action(),
				record.targetType(), record.targetId(), (record.target() != null) ? person(record.target()) : null,
				record.changes(), record.outcome(), record.traceId());
	}

	private static AuditEntryResponse.PersonResponse person(AuditRecord.Person person) {
		return new AuditEntryResponse.PersonResponse(person.id(), person.name(), person.kind().name());
	}

}
