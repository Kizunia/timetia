package com.kibootia.backend.timetable.web;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kibootia.backend.platform.web.PageQuery;
import com.kibootia.backend.platform.web.PageResponse;
import com.kibootia.backend.timetable.application.LiveTimetableService;
import com.kibootia.backend.timetable.application.LiveTimetableService.LiveFilter;

/**
 * The live timetable: the lectures the signed-in person may see, by date. Everyone with a role in Timetia reads it, and
 * what they see follows their role and place (see {@link LiveTimetableService}); a person with no role sees an empty
 * list. It is read-only: changing a lecture is a ticket, not an edit of the timetable.
 */
@RestController
@RequestMapping("/api/v1/timetable/live")
class LiveTimetableController {

	private static final Map<String, String> SORTABLE = Map.of("date", "effectiveDate", "startTime", "effectiveStartTime");

	private static final Sort BY_DATE_AND_TIME = Sort.by(Sort.Direction.ASC, "effectiveDate")
		.and(Sort.by(Sort.Direction.ASC, "effectiveStartTime"));

	private final LiveTimetableService live;

	LiveTimetableController(LiveTimetableService live) {
		this.live = live;
	}

	/**
	 * @param from the first date, inclusive
	 * @param to the last date, inclusive; at most 62 days after {@code from}
	 * @param division only lectures of this division
	 * @param batch only lectures this batch attends, which includes the theory lectures of its division
	 * @param faculty only lectures taught by this faculty member
	 */
	@GetMapping
	PageResponse<LiveLectureResponse> list(@ParameterObject @Valid PageQuery query,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) UUID division, @RequestParam(required = false) UUID batch,
			@RequestParam(required = false) UUID faculty) {
		return PageResponse.of(
				this.live.list(new LiveFilter(from, to, division, batch, faculty),
						query.toPageable(SORTABLE, BY_DATE_AND_TIME)),
				TimetableMapper::toResponse);
	}

	@GetMapping("/{id}")
	LiveLectureResponse get(@PathVariable UUID id) {
		return TimetableMapper.toResponse(this.live.get(id));
	}

}
