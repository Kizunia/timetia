package com.kibootia.backend.reference.web;

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

import com.kibootia.backend.reference.application.CatalogService;
import com.kibootia.backend.platform.web.PageQuery;
import com.kibootia.backend.platform.web.PageResponse;

/** Rooms. Super Admin configuration; the service enforces the permission. */
@RestController
@RequestMapping("/api/v1/rooms")
class RoomsController {

	private static final Map<String, String> SORTABLE = Map.of("code", "code", "name", "name", "createdAt", "createdAt");

	private static final Sort BY_CODE = Sort.by(Sort.Direction.ASC, "code");

	private final CatalogService catalog;

	RoomsController(CatalogService catalog) {
		this.catalog = catalog;
	}

	@PostMapping
	@ApiResponse(responseCode = "201", description = "The room was created; `Location` is its URL.")
	ResponseEntity<RoomResponse> create(@Valid @RequestBody CreateRoomRequest request) {
		RoomResponse created = CatalogMapper
			.toResponse(this.catalog.createRoom(request.code(), request.name(), request.roomType(), request.capacity()));
		return ResponseEntity.created(URI.create("/api/v1/rooms/" + created.id())).body(created);
	}

	@GetMapping("/{id}")
	RoomResponse get(@PathVariable UUID id) {
		return CatalogMapper.toResponse(this.catalog.getRoom(id));
	}

	@PutMapping("/{id}")
	RoomResponse rename(@PathVariable UUID id, @Valid @RequestBody RenameRequest request) {
		return CatalogMapper.toResponse(this.catalog.renameRoom(id, request.version(), request.name()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable UUID id) {
		this.catalog.deleteRoom(id);
	}

	/** @param q only rooms whose code or name contains this text */
	@GetMapping
	PageResponse<RoomResponse> list(@ParameterObject @Valid PageQuery query,
			@RequestParam(required = false) @Size(max = 100) String q) {
		return PageResponse.of(this.catalog.listRooms(q, query.toPageable(SORTABLE, BY_CODE)), CatalogMapper::toResponse);
	}

}
