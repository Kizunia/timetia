package com.kibootia.backend.timetable.web;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.kibootia.backend.platform.web.PageQuery;
import com.kibootia.backend.platform.web.PageResponse;
import com.kibootia.backend.timetable.application.TimetableAuthoringService;
import com.kibootia.backend.timetable.application.VersionStatus;

/**
 * Arranging the base timetable: a division's versions, the weekly lectures of a draft, and publishing. Super Admin
 * configuration; the service enforces the permission. Adding to or removing from a draft are sub-resources of the version,
 * and publishing, which is not plain CRUD, is an action on it (ADR 0043).
 */
@RestController
@RequestMapping("/api/v1/timetable/versions")
class TimetableVersionsController {

	private static final Map<String, String> SORTABLE = Map.of("effectiveFrom", "effectiveFrom", "createdAt", "createdAt");

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "effectiveFrom");

	private final TimetableAuthoringService timetables;

	TimetableVersionsController(TimetableAuthoringService timetables) {
		this.timetables = timetables;
	}

	@PostMapping
	@ApiResponse(responseCode = "201", description = "The draft was created; `Location` is its URL.")
	ResponseEntity<TimetableVersionResponse> create(@Valid @RequestBody CreateVersionRequest request) {
		TimetableVersionResponse created = TimetableMapper
			.toResponse(this.timetables.createVersion(request.divisionId(), request.termId(), request.effectiveFrom()));
		return ResponseEntity.created(URI.create("/api/v1/timetable/versions/" + created.id())).body(created);
	}

	@GetMapping("/{id}")
	TimetableVersionResponse get(@PathVariable UUID id) {
		return TimetableMapper.toResponse(this.timetables.getVersion(id));
	}

	/** Removes a draft and its lectures. A published version is never removed. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable UUID id) {
		this.timetables.deleteVersion(id);
	}

	/**
	 * @param division only versions of this division
	 * @param status only versions in this state
	 */
	@GetMapping
	PageResponse<TimetableVersionResponse> list(@ParameterObject @Valid PageQuery query,
			@RequestParam(required = false) UUID division, @RequestParam(required = false) VersionStatus status) {
		return PageResponse.of(this.timetables.listVersions(division, status, query.toPageable(SORTABLE, NEWEST_FIRST)),
				TimetableMapper::toResponse);
	}

	// --- the weekly lectures of a draft -----------------------------------------------------------------------------

	@PostMapping("/{id}/entries")
	@ApiResponse(responseCode = "201", description = "The lecture was added to the draft; `Location` is its URL.")
	ResponseEntity<TimetableEntryResponse> addEntry(@PathVariable UUID id, @Valid @RequestBody CreateEntryRequest request) {
		TimetableEntryResponse created = TimetableMapper
			.toResponse(this.timetables.addEntry(id, TimetableMapper.toNewEntry(request)));
		return ResponseEntity.created(URI.create("/api/v1/timetable/versions/" + id + "/entries/" + created.id()))
			.body(created);
	}

	/** The lectures of a version by day and time. A version holds at most a week's worth, so it is not paged. */
	@GetMapping("/{id}/entries")
	PageResponse<TimetableEntryResponse> entries(@PathVariable UUID id) {
		return PageResponse.of(new PageImpl<>(this.timetables.listEntries(id)), TimetableMapper::toResponse);
	}

	@GetMapping("/{id}/entries/{entryId}")
	TimetableEntryResponse entry(@PathVariable UUID id, @PathVariable UUID entryId) {
		return TimetableMapper.toResponse(this.timetables.getEntry(id, entryId));
	}

	@DeleteMapping("/{id}/entries/{entryId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeEntry(@PathVariable UUID id, @PathVariable UUID entryId) {
		this.timetables.deleteEntry(id, entryId);
	}

	// --- publishing -------------------------------------------------------------------------------------------------

	/**
	 * Makes the draft the division's timetable from its effective date: checks it books nothing twice, creates the dated
	 * lectures and replaces the ones it supersedes, all or nothing.
	 */
	@PostMapping("/{id}/publish")
	PublishedVersionResponse publish(@PathVariable UUID id) {
		return TimetableMapper.toResponse(this.timetables.publish(id));
	}

}
