package com.kibootia.backend.timetable.application;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.academic.AcademicPermissions;
import com.kibootia.backend.academic.AcademicScopes;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.authorization.Reach;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.errors.ApiFieldError;
import com.kibootia.backend.platform.errors.ProblemCodes;
import com.kibootia.backend.reference.AcademicDirectory;
import com.kibootia.backend.timetable.LectureView;
import com.kibootia.backend.timetable.application.OccurrenceQuery.Visibility;

/**
 * The live timetable: the lectures a person may see, in a date range. What a person sees follows their roles (Timetia
 * ADR 0004):
 *
 * <ul>
 * <li>the Super Admin sees every lecture ({@link AcademicPermissions#LIVE_VIEW_ALL});</li>
 * <li>a Super HOD, HOD, CR, BR or student sees the lectures of the places they hold the role in
 * ({@link AcademicPermissions#LIVE_VIEW}): a department's, a division's, or those of a batch, which include the theory
 * lectures of the batch's division;</li>
 * <li>a faculty member sees the lectures they teach ({@link AcademicPermissions#LIVE_VIEW_OWN}) and every lecture of a
 * division they teach in ({@link AcademicPermissions#LIVE_VIEW_DIVISION}).</li>
 * </ul>
 *
 * <p>A list and a single lecture must agree: the list holds exactly the lectures the single-record decision would allow.
 * The list is therefore filtered in the query from {@link Authorization#reach}, before it is paged, and a test holds the
 * two together for every role. A lecture the caller may not see is answered as not found, so its existence is not revealed.
 */
@Service
public class LiveTimetableService {

	public static final String LECTURE_NOT_FOUND = "timetable.lecture_not_found";

	/** The longest range one request may ask for, which keeps a page quick and a request from asking for a whole term. */
	public static final int MAX_RANGE_DAYS = 62;

	private final OccurrenceStore occurrences;

	private final AcademicDirectory directory;

	private final Authorization authorization;

	private final LectureViewFactory views;

	LiveTimetableService(OccurrenceStore occurrences, AcademicDirectory directory, Authorization authorization,
			LectureViewFactory views) {
		this.occurrences = occurrences;
		this.directory = directory;
		this.authorization = authorization;
		this.views = views;
	}

	/** What a list is narrowed by; the date range is required, the rest are optional. */
	public record LiveFilter(LocalDate from, LocalDate to, UUID division, UUID batch, UUID faculty) {

	}

	@Transactional(readOnly = true)
	public Page<LectureView> list(LiveFilter filter, Pageable pageable) {
		requireReasonableRange(filter);
		boolean everyLecture = this.authorization.can(AcademicPermissions.LIVE_VIEW_ALL);
		Reach inPlace = this.authorization.reach(AcademicPermissions.LIVE_VIEW);
		boolean ownLectures = !this.authorization.reach(AcademicPermissions.LIVE_VIEW_OWN).isEmpty();
		// A faculty member also sees every lecture of a division they teach in.
		Set<UUID> divisions = new HashSet<>(inPlace.idsOf(AcademicScopes.DIVISION));
		if (!this.authorization.reach(AcademicPermissions.LIVE_VIEW_DIVISION).isEmpty()) {
			divisions.addAll(this.directory.divisionIdsTaughtBy(this.authorization.currentUserId()));
		}

		Set<UUID> batches = inPlace.idsOf(AcademicScopes.BATCH);
		Visibility visibility = new Visibility(everyLecture || inPlace.everywhere(),
				inPlace.idsOf(AcademicScopes.DEPARTMENT), divisions, batches,
				divisionsOf(batches), ownLectures ? this.directory.facultyIdsOf(this.authorization.currentUserId()) : Set.of());
		if (visibility.isEmpty()) {
			// Holding the permission nowhere means seeing nothing; there is no need to ask the database.
			return Page.empty(pageable);
		}
		UUID batchDivision = (filter.batch() == null) ? null : divisionsOf(Set.of(filter.batch())).stream().findFirst().orElse(null);
		OccurrenceQuery query = new OccurrenceQuery(filter.from(), filter.to(), filter.division(), filter.batch(),
				batchDivision, filter.faculty(), visibility);
		Page<Occurrence> page = this.occurrences.search(query, pageable);
		List<LectureView> lectures = this.views.viewsOf(page.getContent());
		return new PageImpl<>(lectures, pageable, page.getTotalElements());
	}

	@Transactional(readOnly = true)
	public LectureView get(UUID id) {
		Occurrence occurrence = this.occurrences.find(id).orElseThrow(LiveTimetableService::notFound);
		LectureView lecture = this.views.viewsOf(List.of(occurrence)).get(0);
		boolean mayView = this.authorization.can(AcademicPermissions.LIVE_VIEW_ALL)
				|| this.authorization.can(AcademicPermissions.LIVE_VIEW, lecture)
				|| this.authorization.can(AcademicPermissions.LIVE_VIEW_DIVISION, lecture)
				|| this.authorization.can(AcademicPermissions.LIVE_VIEW_OWN, lecture);
		if (!mayView) {
			throw notFound();
		}
		return lecture;
	}

	/** The divisions of these batches. A theory lecture is for every batch of its division, so holding a batch reaches them. */
	private Set<UUID> divisionsOf(Set<UUID> batchIds) {
		return batchIds.isEmpty() ? Set.of() : new HashSet<>(this.directory.divisionsOfBatches(batchIds).values());
	}

	private static void requireReasonableRange(LiveFilter filter) {
		if (filter.to().isBefore(filter.from())) {
			throw ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.",
					List.of(new ApiFieldError("to", "before_from", "The end of the range is before its start.")));
		}
		if (ChronoUnit.DAYS.between(filter.from(), filter.to()) > MAX_RANGE_DAYS) {
			throw ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.",
					List.of(new ApiFieldError("to", "range_too_long",
							"Ask for at most " + MAX_RANGE_DAYS + " days at a time.")));
		}
	}

	private static ApiException notFound() {
		return ApiException.notFound(LECTURE_NOT_FOUND, "There is no lecture with that ID.");
	}

}
