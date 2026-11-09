package com.kibootia.backend.notes.web;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.kibootia.backend.notes.application.NoteFilter;
import com.kibootia.backend.notes.application.NoteService;
import com.kibootia.backend.platform.web.PageQuery;
import com.kibootia.backend.platform.web.PageResponse;

/**
 * The reference REST resource (ADR 0043): plural kebab-case under {@code /api/v1}, DTOs rather than entities, lists with
 * paging and an allow-listed sort, and an action that is not plain CRUD as a sub-resource. The controller only converts
 * and delegates; the use cases decide who may do what.
 */
@RestController
@RequestMapping("/api/v1/notes")
class NotesController {

	/** API field name to entity property, for every field a client may sort by. */
	private static final Map<String, String> SORTABLE = Map.of("title", "title", "createdAt", "createdAt", "updatedAt",
			"updatedAt");

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

	private final NoteService notes;

	NotesController(NoteService notes) {
		this.notes = notes;
	}

	@PostMapping
	@ApiResponse(responseCode = "201", description = "The note was created; `Location` is its URL.")
	ResponseEntity<NoteResponse> create(@Valid @RequestBody CreateNoteRequest request) {
		NoteResponse created = NoteMapper.toResponse(this.notes.create(NoteMapper.toNewNote(request)));
		return ResponseEntity.created(URI.create("/api/v1/notes/" + created.id())).body(created);
	}

	@GetMapping("/{id}")
	NoteResponse get(@PathVariable UUID id) {
		return NoteMapper.toResponse(this.notes.get(id));
	}

	@PutMapping("/{id}")
	NoteResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateNoteRequest request) {
		return NoteMapper.toResponse(this.notes.update(id, NoteMapper.toChanges(request)));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable UUID id) {
		this.notes.delete(id);
	}

	/**
	 * @param q only notes containing this text in the title or body
	 * @param owner only this user's notes; a caller who may see only their own gets an empty page for anyone else
	 */
	@GetMapping
	PageResponse<NoteResponse> list(@ParameterObject @Valid PageQuery query,
			@RequestParam(required = false) @Size(max = 100) String q, @RequestParam(required = false) UUID owner) {
		return PageResponse.of(this.notes.list(new NoteFilter(q, owner), query.toPageable(SORTABLE, NEWEST_FIRST)),
				NoteMapper::toResponse);
	}

	/** Brings a deleted note back. Administrators only. */
	@PostMapping("/{id}/restore")
	NoteResponse restore(@PathVariable UUID id) {
		return NoteMapper.toResponse(this.notes.restore(id));
	}

}
