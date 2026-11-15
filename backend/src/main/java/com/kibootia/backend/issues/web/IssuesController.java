package com.kibootia.backend.issues.web;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kibootia.backend.issues.application.IssueService;
import com.kibootia.backend.issues.application.IssueService.IssueFilter;
import com.kibootia.backend.issues.domain.IssueStatus;
import com.kibootia.backend.platform.web.PageQuery;
import com.kibootia.backend.platform.web.PageResponse;

/**
 * Issues that a class representative or batch representative opens about what the class wants, in the way of GitHub issues.
 * An issue never changes the timetable: the faculty it mentions raise a ticket themselves if they agree, and the ticket can
 * be linked to the issue. The use cases decide who may do what and answer an issue the caller may not see as not found.
 */
@RestController
@RequestMapping("/api/v1/issues")
class IssuesController {

	private static final Map<String, String> SORTABLE = Map.of("createdAt", "createdAt");

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

	private final IssueService issues;

	IssuesController(IssueService issues) {
		this.issues = issues;
	}

	@PostMapping
	@ApiResponse(responseCode = "201", description = "The issue was opened; `Location` is its URL. Each mentioned faculty member is told.")
	ResponseEntity<IssueResponse> open(@Valid @RequestBody CreateIssueRequest request) {
		IssueResponse created = IssueMapper.toResponse(this.issues.open(IssueMapper.toDraft(request)));
		return ResponseEntity.created(URI.create("/api/v1/issues/" + created.id())).body(created);
	}

	@GetMapping("/{id}")
	IssueResponse get(@PathVariable UUID id) {
		return IssueMapper.toResponse(this.issues.get(id));
	}

	/**
	 * @param status only issues in this state
	 * @param mine only issues the caller opened
	 */
	@GetMapping
	PageResponse<IssueResponse> list(@ParameterObject @Valid PageQuery query,
			@RequestParam(required = false) IssueStatus status, @RequestParam(defaultValue = "false") boolean mine) {
		return PageResponse.of(this.issues.list(new IssueFilter(status, mine), query.toPageable(SORTABLE, NEWEST_FIRST)),
				IssueMapper::toResponse);
	}

	/** Closes the issue, by the person who opened it or a faculty member it mentions. */
	@PostMapping("/{id}/close")
	IssueResponse close(@PathVariable UUID id) {
		return IssueMapper.toResponse(this.issues.close(id));
	}

	/** Mentions another faculty member, who is told. Only the person who opened the issue may. */
	@PostMapping("/{id}/mentions")
	IssueResponse mention(@PathVariable UUID id, @Valid @RequestBody MentionRequest request) {
		return IssueMapper.toResponse(this.issues.mention(id, request.facultyId()));
	}

	/** Links the ticket that answers the issue, as a pull request is linked to the issue it answers. */
	@PostMapping("/{id}/links")
	IssueResponse link(@PathVariable UUID id, @Valid @RequestBody LinkRequest request) {
		return IssueMapper.toResponse(this.issues.link(id, request.ticketId()));
	}

}
