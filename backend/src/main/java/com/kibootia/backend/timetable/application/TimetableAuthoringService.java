package com.kibootia.backend.timetable.application;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.academic.AcademicPermissions;
import com.kibootia.backend.audit.AuditEntry;
import com.kibootia.backend.audit.AuditLog;
import com.kibootia.backend.platform.authorization.RequiresPermission;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.errors.ApiFieldError;
import com.kibootia.backend.platform.errors.ProblemCodes;
import com.kibootia.backend.reference.AcademicDirectory;
import com.kibootia.backend.reference.AssignmentDetails;
import com.kibootia.backend.reference.DivisionDetails;
import com.kibootia.backend.reference.TermDetails;
import com.kibootia.backend.timetable.LectureMode;
import com.kibootia.backend.timetable.LectureSlot;
import com.kibootia.backend.timetable.domain.ClaimKey;
import com.kibootia.backend.timetable.domain.ClaimKind;
import com.kibootia.backend.timetable.domain.Claims;
import com.kibootia.backend.timetable.domain.Clashes;
import com.kibootia.backend.timetable.domain.Clashes.Clash;
import com.kibootia.backend.timetable.domain.Clashes.Weekly;
import com.kibootia.backend.timetable.domain.WeeklyDates;

/**
 * The use cases that arrange the base timetable: a division's versions, the weekly entries of a draft, and publishing,
 * which turns a draft into the dated lectures everyone sees. This is Super Admin configuration
 * ({@link AcademicPermissions#CONFIG_MANAGE}), the manually arranged timetable of version 1 (Timetia ADR 0001).
 *
 * <p>A draft is edited freely; a published version is never changed. A version takes effect on its {@code effectiveFrom}
 * date and until the next published version of the division does. Publishing checks the timetable cannot book a teacher,
 * a room or a group of students twice, within the version and against what other divisions already have, and only then
 * creates the dated lectures, in one transaction with replacing the ones the new version supersedes.
 */
@Service
public class TimetableAuthoringService {

	public static final String VERSION_NOT_FOUND = "timetable.version_not_found";

	public static final String ENTRY_NOT_FOUND = "timetable.entry_not_found";

	public static final String VERSION_PUBLISHED = "timetable.version_published";

	public static final String VERSION_EMPTY = "timetable.version_empty";

	public static final String EFFECTIVE_FROM_TAKEN = "timetable.effective_from_taken";

	public static final String REFERENCE_MISSING = "timetable.reference_missing";

	public static final String CLASH = "timetable.clash";

	public static final String CHANGES_WOULD_BE_LOST = "timetable.changes_would_be_lost";

	/** How many clashes a refusal spells out; the rest are counted. */
	private static final int CLASHES_SHOWN = 3;

	private static final LocalTime DAY_STARTS = LocalTime.of(8, 0);

	private static final LocalTime DAY_ENDS = LocalTime.of(20, 0);

	private final TimetableStore timetables;

	private final OccurrenceStore occurrences;

	private final ClaimStore claims;

	private final AcademicDirectory directory;

	private final AuditLog auditLog;

	TimetableAuthoringService(TimetableStore timetables, OccurrenceStore occurrences, ClaimStore claims,
			AcademicDirectory directory, AuditLog auditLog) {
		this.timetables = timetables;
		this.occurrences = occurrences;
		this.claims = claims;
		this.directory = directory;
		this.auditLog = auditLog;
	}

	// --- versions ---------------------------------------------------------------------------------------------------

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public TimetableVersion createVersion(UUID divisionId, UUID termId, LocalDate effectiveFrom) {
		List<ApiFieldError> errors = new ArrayList<>();
		Optional<DivisionDetails> division = this.directory.division(divisionId);
		if (division.isEmpty()) {
			errors.add(new ApiFieldError("divisionId", "unknown_division", "There is no division with that ID."));
		}
		Optional<TermDetails> term = this.directory.term(termId);
		if (term.isEmpty()) {
			errors.add(new ApiFieldError("termId", "unknown_term", "There is no academic term with that ID."));
		}
		else if (effectiveFrom.isBefore(term.get().startDate()) || effectiveFrom.isAfter(term.get().endDate())) {
			errors.add(new ApiFieldError("effectiveFrom", "outside_term", "The date is outside the term, which runs from "
					+ term.get().startDate() + " to " + term.get().endDate() + "."));
		}
		requireValid(errors);
		TimetableVersion created = this.timetables.insertVersion(divisionId, termId, effectiveFrom);
		this.auditLog.record(AuditEntry.of("timetable.version.created", "timetable-version", created.id()));
		return created;
	}

	@Transactional(readOnly = true)
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public TimetableVersion getVersion(UUID id) {
		return requireVersion(id);
	}

	@Transactional(readOnly = true)
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Page<TimetableVersion> listVersions(UUID division, VersionStatus status, Pageable pageable) {
		return this.timetables.searchVersions(division, status, pageable);
	}

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public void deleteVersion(UUID id) {
		requireDraft(requireVersion(id));
		this.timetables.deleteVersion(id);
		this.auditLog.record(AuditEntry.of("timetable.version.deleted", "timetable-version", id));
	}

	// --- entries ----------------------------------------------------------------------------------------------------

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public TimetableEntry addEntry(UUID versionId, NewEntry entry) {
		TimetableVersion version = requireDraft(requireVersion(versionId));
		AssignmentDetails assignment = validate(version, entry);

		List<TimetableEntry> existing = this.timetables.entriesOf(versionId);
		UUID placeholder = UUID.randomUUID();
		List<Weekly> lectures = new ArrayList<>(weekly(existing));
		lectures.add(new Weekly(placeholder, entry.dayOfWeek(), entry.startTime(), entry.endTime(),
				assignment.facultyId(), entry.roomId(), assignment.divisionId(), assignment.batchId()));
		List<Clash<Weekly>> clashes = Clashes.within(lectures)
			.stream()
			.filter((clash) -> clash.first().id().equals(placeholder) || clash.second().id().equals(placeholder))
			.toList();
		if (!clashes.isEmpty()) {
			throw ApiException.conflict(CLASH, "This lecture would clash with one already in the version: "
					+ describeWeekly(clashes));
		}
		TimetableEntry created = this.timetables.insertEntry(versionId, entry);
		this.auditLog.record(AuditEntry.of("timetable.entry.added", "timetable-version", versionId)
			.withChange("entryId", created.id().toString()));
		return created;
	}

	@Transactional(readOnly = true)
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public List<TimetableEntry> listEntries(UUID versionId) {
		requireVersion(versionId);
		return this.timetables.entriesOf(versionId);
	}

	@Transactional(readOnly = true)
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public TimetableEntry getEntry(UUID versionId, UUID entryId) {
		requireVersion(versionId);
		return requireEntry(versionId, entryId);
	}

	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public void deleteEntry(UUID versionId, UUID entryId) {
		requireDraft(requireVersion(versionId));
		TimetableEntry entry = requireEntry(versionId, entryId);
		this.timetables.deleteEntry(entry.id());
		this.auditLog.record(AuditEntry.of("timetable.entry.removed", "timetable-version", versionId)
			.withChange("entryId", entryId.toString()));
	}

	// --- publishing -------------------------------------------------------------------------------------------------

	/**
	 * Makes a draft the timetable of its division from its effective date. The dated lectures are created from the
	 * effective date to the end of the term, or to the day before the next published version takes over, and the division's
	 * lectures in that range from earlier versions are superseded. All of it happens in one transaction: either the version is
	 * published with its lectures or nothing changes.
	 */
	@Transactional
	@RequiresPermission(AcademicPermissions.CONFIG_MANAGE)
	public Published publish(UUID versionId) {
		TimetableVersion version = requireDraft(requireVersion(versionId));
		List<TimetableEntry> entries = this.timetables.entriesOf(versionId);
		if (entries.isEmpty()) {
			throw ApiException.conflict(VERSION_EMPTY, "A version with no lectures cannot be published.");
		}
		if (this.timetables.publishedStartingOn(version.divisionId(), version.effectiveFrom())) {
			throw ApiException.conflict(EFFECTIVE_FROM_TAKEN,
					"Another published version of this division already takes effect on " + version.effectiveFrom() + ".");
		}
		TermDetails term = this.directory.term(version.termId())
			.orElseThrow(() -> ApiException.conflict(REFERENCE_MISSING, "The version's academic term no longer exists."));
		Map<UUID, AssignmentDetails> assignments = this.directory
			.assignments(entries.stream().map(TimetableEntry::assignmentId).collect(Collectors.toSet()));
		if (!assignments.keySet().containsAll(entries.stream().map(TimetableEntry::assignmentId).toList())) {
			throw ApiException.conflict(REFERENCE_MISSING,
					"A lecture's teaching assignment, or something it refers to, no longer exists.");
		}

		// Nothing in the version may book a teacher, a room or students twice. Entries are checked when added, so this is the
		// guard against anything that changed since, not the first line of defence.
		List<Clash<Weekly>> clashes = Clashes.within(weekly(entries, assignments));
		if (!clashes.isEmpty()) {
			throw ApiException.conflict(CLASH, "The version books something twice: " + describeWeekly(clashes));
		}

		LocalDate from = version.effectiveFrom();
		LocalDate to = this.timetables.nextPublishedAfter(version.divisionId(), from)
			.map((next) -> next.minusDays(1))
			.filter((end) -> end.isBefore(term.endDate()))
			.orElse(term.endDate());
		LocalDate toExclusive = to.plusDays(1);

		// A change that has been applied is kept and re-checked, never discarded silently (timetable version changes). Carrying
		// changes over to a new version is not built yet, so a version that would replace a changed lecture is refused.
		long changed = this.occurrences.countChanged(version.divisionId(), from, toExclusive);
		if (changed > 0) {
			throw ApiException.conflict(CHANGES_WOULD_BE_LOST, changed + " lecture(s) this version would replace have an applied "
					+ "change, which would be lost. Revert those changes first, or start the version after them.");
		}

		List<NewOccurrence> generated = generate(version, entries, assignments, from, to);
		List<UUID> replaced = this.occurrences.supersede(version.divisionId(), from, toExclusive);
		this.claims.deleteOf(replaced);
		List<Set<ClaimKey>> needed = generated.stream().map((lecture) -> claimsOf(lecture, assignments)).toList();
		rejectWhatOtherLecturesHold(needed);

		List<Occurrence> created = this.occurrences.insertAll(generated);
		List<ClaimStore.Claim> held = new ArrayList<>();
		for (int i = 0; i < created.size(); i++) {
			UUID occurrence = created.get(i).id();
			needed.get(i).forEach((key) -> held.add(new ClaimStore.Claim(occurrence, key)));
		}
		this.claims.insertAll(held);

		TimetableVersion published = this.timetables.markPublished(versionId);
		this.auditLog.record(AuditEntry.of("timetable.version.published", "timetable-version", versionId)
			.withChange("occurrences", String.valueOf(created.size()))
			.withChange("superseded", String.valueOf(replaced.size())));
		return new Published(published, created.size(), replaced.size());
	}

	/** The result of publishing: the version, how many dated lectures it created and how many earlier ones it replaced. */
	public record Published(TimetableVersion version, int occurrencesCreated, int occurrencesSuperseded) {

	}

	private List<NewOccurrence> generate(TimetableVersion version, List<TimetableEntry> entries,
			Map<UUID, AssignmentDetails> assignments, LocalDate from, LocalDate to) {
		List<NewOccurrence> generated = new ArrayList<>();
		for (TimetableEntry entry : entries) {
			AssignmentDetails assignment = assignments.get(entry.assignmentId());
			for (LocalDate date : WeeklyDates.of(entry.dayOfWeek(), from, to)) {
				generated.add(new NewOccurrence(entry.id(), version.id(), entry.assignmentId(), assignment.departmentId(),
						assignment.divisionId(), assignment.batchId(), assignment.sessionType(), assignment.facultyId(),
						new LectureSlot(date, entry.startTime(), entry.endTime(), entry.mode(), entry.roomId(),
								entry.meetingLink())));
			}
		}
		return generated;
	}

	private static Set<ClaimKey> claimsOf(NewOccurrence lecture, Map<UUID, AssignmentDetails> assignments) {
		AssignmentDetails assignment = assignments.get(lecture.assignmentId());
		LectureSlot slot = lecture.slot();
		return Claims.keysFor(lecture.sessionType(), lecture.facultyId(), slot.roomId(), lecture.divisionId(),
				assignment.batchIds(), slot.date(), slot.startTime(), slot.endTime());
	}

	/**
	 * A teacher or a room cannot be in two divisions' lectures at once, so what the new lectures need is checked against what
	 * other lectures hold. The lectures this version replaces have already let go. The database refuses a double booking
	 * anyway; this says what is in the way.
	 */
	private void rejectWhatOtherLecturesHold(List<Set<ClaimKey>> needed) {
		Set<ClaimKey> everything = needed.stream().flatMap(Set::stream).collect(Collectors.toCollection(LinkedHashSet::new));
		Map<ClaimKey, UUID> holders = this.claims.holders(everything);
		if (holders.isEmpty()) {
			return;
		}
		ClaimKey first = everything.stream().filter(holders::containsKey).findFirst().orElseThrow();
		throw ApiException.conflict(CLASH, "The version clashes with other lectures (" + holders.size() + " in all), for example on "
				+ first.date() + " at " + first.hour() + ":00: " + describe(first.kind()) + ".");
	}

	// --- validation -------------------------------------------------------------------------------------------------

	/** Checks a new entry and returns its teaching assignment; every bad field is reported together. */
	private AssignmentDetails validate(TimetableVersion version, NewEntry entry) {
		List<ApiFieldError> errors = new ArrayList<>();
		AssignmentDetails assignment = this.directory.assignments(Set.of(entry.assignmentId())).get(entry.assignmentId());
		if (assignment == null) {
			errors.add(new ApiFieldError("assignmentId", "unknown_assignment", "There is no such teaching assignment."));
		}
		else if (!assignment.divisionId().equals(version.divisionId())) {
			errors.add(new ApiFieldError("assignmentId", "assignment_of_another_division",
					"The teaching assignment belongs to another division than this version."));
		}
		if (entry.dayOfWeek() < 1 || entry.dayOfWeek() > 6) {
			errors.add(new ApiFieldError("dayOfWeek", "invalid_day", "The day must be 1 (Monday) to 6 (Saturday)."));
		}
		if (!onTheHour(entry.startTime()) || !onTheHour(entry.endTime()) || !entry.startTime().isBefore(entry.endTime())
				|| entry.startTime().isBefore(DAY_STARTS) || entry.endTime().isAfter(DAY_ENDS)) {
			errors.add(new ApiFieldError("startTime", "invalid_time", "A lecture starts and ends on the hour, between "
					+ DAY_STARTS + " and " + DAY_ENDS + ", and ends after it starts."));
		}
		if (entry.mode() == LectureMode.IN_PERSON) {
			if (entry.roomId() == null) {
				errors.add(new ApiFieldError("roomId", "room_required", "An in-person lecture needs a room."));
			}
			else if (!this.directory.rooms(Set.of(entry.roomId())).containsKey(entry.roomId())) {
				errors.add(new ApiFieldError("roomId", "unknown_room", "There is no room with that ID."));
			}
			if (entry.meetingLink() != null) {
				errors.add(new ApiFieldError("meetingLink", "link_not_allowed", "Only an online lecture has a meeting link."));
			}
		}
		else if (entry.roomId() != null) {
			errors.add(new ApiFieldError("roomId", "room_not_allowed", "An online lecture has no room."));
		}
		requireValid(errors);
		return assignment;
	}

	private static boolean onTheHour(LocalTime time) {
		return time.getMinute() == 0 && time.getSecond() == 0 && time.getNano() == 0;
	}

	private static void requireValid(List<ApiFieldError> errors) {
		if (!errors.isEmpty()) {
			throw ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.", errors);
		}
	}

	private TimetableVersion requireVersion(UUID id) {
		return this.timetables.findVersion(id)
			.orElseThrow(() -> ApiException.notFound(VERSION_NOT_FOUND, "There is no timetable version with that ID."));
	}

	/** An entry of this version; one that belongs to another version looks the same as one that does not exist. */
	private TimetableEntry requireEntry(UUID versionId, UUID entryId) {
		return this.timetables.findEntry(entryId)
			.filter((found) -> found.timetableVersionId().equals(versionId))
			.orElseThrow(() -> ApiException.notFound(ENTRY_NOT_FOUND, "There is no such entry in this version."));
	}

	private static TimetableVersion requireDraft(TimetableVersion version) {
		if (version.status() != VersionStatus.DRAFT) {
			throw ApiException.conflict(VERSION_PUBLISHED, "A published version cannot be changed.");
		}
		return version;
	}

	// --- clash helpers ----------------------------------------------------------------------------------------------

	/** The entries as weekly lectures; one whose assignment can no longer be found is left out. */
	private List<Weekly> weekly(List<TimetableEntry> entries) {
		Map<UUID, AssignmentDetails> assignments = this.directory
			.assignments(entries.stream().map(TimetableEntry::assignmentId).collect(Collectors.toSet()));
		return weekly(entries, assignments);
	}

	private static List<Weekly> weekly(List<TimetableEntry> entries, Map<UUID, AssignmentDetails> assignments) {
		List<Weekly> lectures = new ArrayList<>();
		for (TimetableEntry entry : entries) {
			AssignmentDetails assignment = assignments.get(entry.assignmentId());
			if (assignment != null) {
				lectures.add(new Weekly(entry.id(), entry.dayOfWeek(), entry.startTime(), entry.endTime(),
						assignment.facultyId(), entry.roomId(), assignment.divisionId(), assignment.batchId()));
			}
		}
		return lectures;
	}

	private static String describeWeekly(List<Clash<Weekly>> clashes) {
		return clashes.stream().limit(CLASHES_SHOWN).map((clash) -> {
			Weekly lecture = clash.first();
			return describe(clash.kind()) + " on " + DayOfWeek.of(lecture.dayOfWeek()).getDisplayName(TextStyle.SHORT,
					Locale.ENGLISH) + " " + lecture.start() + "-" + lecture.end();
		}).collect(Collectors.joining("; ")) + ((clashes.size() > CLASHES_SHOWN)
				? " (and " + (clashes.size() - CLASHES_SHOWN) + " more)" : "");
	}

	private static String describe(ClaimKind kind) {
		return switch (kind) {
			case FACULTY -> "the same faculty member is in two lectures";
			case ROOM -> "the same room is booked twice";
			case DIVISION, STUDENT_BATCH -> "the same students are in two lectures";
		};
	}

	private static String describe(Clashes.Kind kind) {
		return switch (kind) {
			case FACULTY -> "the same faculty member is in two lectures";
			case ROOM -> "the same room is booked twice";
			case STUDENTS -> "the same students are in two lectures";
		};
	}

}
