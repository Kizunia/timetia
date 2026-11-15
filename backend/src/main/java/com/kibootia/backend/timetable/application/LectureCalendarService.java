package com.kibootia.backend.timetable.application;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.reference.AcademicDirectory;
import com.kibootia.backend.reference.AssignmentDetails;
import com.kibootia.backend.reference.FacultyDetails;
import com.kibootia.backend.reference.RoomDetails;
import com.kibootia.backend.reference.TermDetails;
import com.kibootia.backend.timetable.ConflictType;
import com.kibootia.backend.timetable.LectureCalendar;
import com.kibootia.backend.timetable.LectureChange;
import com.kibootia.backend.timetable.LectureConflict;
import com.kibootia.backend.timetable.LectureSlot;
import com.kibootia.backend.timetable.LectureView;
import com.kibootia.backend.timetable.ChangeConflictException;
import com.kibootia.backend.timetable.domain.ClaimKey;
import com.kibootia.backend.timetable.domain.ClaimKind;
import com.kibootia.backend.timetable.domain.Claims;

/**
 * Reads lectures as they are now and makes changes to them, for the rescheduling module. The rule it keeps: a teacher, a
 * room or a group of students is never in two lectures at once. Every lecture holds claims on those things for each hour
 * (see {@link Claims}); a change is allowed only when nothing it needs is held by another lecture, and the claims move with
 * the lecture in the same transaction, so what is held always matches what is scheduled.
 *
 * <p>Checking tells the person planning a change what is in the way. Applying checks again with the lectures locked, because
 * something may have changed since, and the unique key on the claims decides if two changes still race for one slot.
 * Lectures changed together are checked together: a lecture moving out of a slot frees it for one moving in.
 */
@Service
class LectureCalendarService implements LectureCalendar {

	private static final String CALLED_BY_AN_AUTHORIZED_MODULE = "called by a module that has authorized the request";

	public static final String SLOT_TAKEN = "timetable.slot_taken";

	private static final LocalTime DAY_STARTS = LocalTime.of(8, 0);

	private static final LocalTime DAY_ENDS = LocalTime.of(20, 0);

	private final OccurrenceStore occurrences;

	private final ClaimStore claims;

	private final TimetableStore timetables;

	private final AcademicDirectory directory;

	private final LectureViewFactory views;

	private final TimetableProperties properties;

	LectureCalendarService(OccurrenceStore occurrences, ClaimStore claims, TimetableStore timetables,
			AcademicDirectory directory, LectureViewFactory views, TimetableProperties properties) {
		this.occurrences = occurrences;
		this.claims = claims;
		this.timetables = timetables;
		this.directory = directory;
		this.views = views;
		this.properties = properties;
	}

	@Override
	@Transactional(readOnly = true)
	@NoAuthorization(CALLED_BY_AN_AUTHORIZED_MODULE)
	public Optional<LectureView> lecture(UUID occurrenceId) {
		return this.occurrences.find(occurrenceId).map((occurrence) -> this.views.viewsOf(List.of(occurrence)).get(0));
	}

	@Override
	@Transactional(readOnly = true)
	@NoAuthorization(CALLED_BY_AN_AUTHORIZED_MODULE)
	public List<LectureView> series(UUID occurrenceId) {
		return this.occurrences.find(occurrenceId)
			.filter((occurrence) -> occurrence.entryId() != null)
			.map((occurrence) -> this.views
				.viewsOf(this.occurrences.findOfEntryFrom(occurrence.entryId(), occurrence.baseSlot().date())))
			.orElse(List.of());
	}

	@Override
	@NoAuthorization(CALLED_BY_AN_AUTHORIZED_MODULE)
	public ZoneId zone() {
		return this.properties.zone();
	}

	@Override
	@Transactional(readOnly = true)
	@NoAuthorization(CALLED_BY_AN_AUTHORIZED_MODULE)
	public List<LectureConflict> check(List<LectureChange> changes) {
		return evaluate(changes, byId(this.occurrences.findAll(occurrenceIds(changes))));
	}

	/**
	 * Not marked for rollback when it refuses the changes: it has written nothing by then, and the caller records the
	 * conflicts and commits.
	 */
	@Override
	@Transactional(noRollbackFor = ChangeConflictException.class)
	@NoAuthorization(CALLED_BY_AN_AUTHORIZED_MODULE)
	public List<UUID> apply(List<LectureChange> changes, UUID ticketId) {
		Map<UUID, Occurrence> current = byId(this.occurrences.lockAll(occurrenceIds(changes)));
		List<LectureConflict> conflicts = evaluate(changes, current);
		if (!conflicts.isEmpty()) {
			throw new ChangeConflictException(conflicts);
		}
		Map<UUID, AssignmentDetails> assignments = this.directory.assignments(assignmentIds(changes, current));

		// Everything the lectures held is released first, so that lectures changing places can take each other's slots.
		this.claims.deleteOf(current.keySet());
		List<ClaimStore.Claim> held = new ArrayList<>();
		List<UUID> result = new ArrayList<>();
		for (LectureChange change : changes) {
			if (change.isCreate()) {
				AssignmentDetails assignment = assignments.get(change.assignmentId());
				TimetableVersion version = this.timetables
					.publishedInForce(assignment.divisionId(), change.target().date())
					.orElseThrow();
				Occurrence created = this.occurrences.insertExtra(new NewOccurrence(null, version.id(), assignment.id(),
						assignment.departmentId(), assignment.divisionId(), assignment.batchId(), assignment.sessionType(),
						assignment.facultyId(), change.target()), ticketId);
				result.add(created.id());
				claimsOf(created, assignment, change).forEach((key) -> held.add(new ClaimStore.Claim(created.id(), key)));
				continue;
			}
			Occurrence occurrence = current.get(change.occurrenceId());
			result.add(occurrence.id());
			if (change.isCancel()) {
				this.occurrences.cancel(occurrence.id(), ticketId);
				continue;
			}
			AssignmentDetails assignment = assignments.get(occurrence.assignmentId());
			if (change.isReassign()) {
				this.occurrences.reassign(occurrence.id(), change.facultyId(), ticketId);
			}
			else if (change.isRestore()) {
				this.occurrences.restore(occurrence.id(), change.target(), change.facultyId(), ticketId);
			}
			else {
				this.occurrences.move(occurrence.id(), change.target(), ticketId);
			}
			claimsOf(occurrence, assignment, change).forEach((key) -> held.add(new ClaimStore.Claim(occurrence.id(), key)));
		}
		try {
			this.claims.insertAll(held);
		}
		catch (DataIntegrityViolationException ex) {
			// Another change took a slot between the check and this write. Nothing is kept; asking again shows what is in the way.
			throw ApiException.conflict(SLOT_TAKEN, "Another change took a slot this one needs. Check the plan again.");
		}
		return result;
	}

	// --- the rules --------------------------------------------------------------------------------------------------

	/**
	 * @param existing the lectures the changes are about that exist now; a lecture to be created is planned here and held
	 *        beside them while the changes are checked, so the rules are the same for it
	 */
	private List<LectureConflict> evaluate(List<LectureChange> changes, Map<UUID, Occurrence> existing) {
		Set<UUID> moving = new HashSet<>(occurrenceIds(changes));
		Map<UUID, AssignmentDetails> assignments = this.directory.assignments(assignmentIds(changes, existing));
		Map<UUID, Occurrence> current = new HashMap<>(existing);
		Set<UUID> created = new HashSet<>();
		Set<UUID> notPlanned = new HashSet<>();
		List<LectureConflict> planning = new ArrayList<>();
		for (LectureChange change : changes) {
			if (change.isCreate()) {
				created.add(change.occurrenceId());
				Optional<Occurrence> planned = planned(change, assignments.get(change.assignmentId()), planning);
				planned.ifPresentOrElse((occurrence) -> current.put(change.occurrenceId(), occurrence),
						() -> notPlanned.add(change.occurrenceId()));
			}
		}
		return evaluate(changes, current, assignments, moving, created, notPlanned, planning);
	}

	private List<LectureConflict> evaluate(List<LectureChange> changes, Map<UUID, Occurrence> current,
			Map<UUID, AssignmentDetails> assignments, Set<UUID> moving, Set<UUID> created, Set<UUID> notPlanned,
			List<LectureConflict> planning) {
		Map<UUID, FacultyDetails> substitutes = this.directory.faculties(changes.stream()
			.filter((change) -> change.isReassign() || change.isRestore())
			.map(LectureChange::facultyId)
			.collect(Collectors.toSet()));
		List<LectureConflict> conflicts = new ArrayList<>(planning);
		Map<LectureChange, Set<ClaimKey>> wanted = new LinkedHashMap<>();

		for (LectureChange change : changes) {
			if (notPlanned.contains(change.occurrenceId())) {
				continue;
			}
			Occurrence occurrence = current.get(change.occurrenceId());
			if (occurrence == null || (occurrence.cancelled() && !change.isRestore())) {
				conflicts.add(changed(change, "The lecture no longer exists or has been cancelled."));
				continue;
			}
			if (occurrence.revision() != change.expectedRevision()) {
				conflicts.add(changed(change, "The lecture was changed by another ticket after this one was planned."));
				continue;
			}
			if (change.isCancel()) {
				continue;
			}
			if ((change.isReassign() || change.isRestore()) && !substitutes.containsKey(change.facultyId())) {
				conflicts.add(changed(change, "The faculty member who was to take the lecture no longer exists."));
				continue;
			}
			List<LectureConflict> outside = change.isReassign() ? List.of() : calendarProblems(occurrence, change);
			if (!outside.isEmpty()) {
				conflicts.addAll(outside);
				continue;
			}
			AssignmentDetails assignment = assignments.get(occurrence.assignmentId());
			if (assignment == null) {
				conflicts.add(changed(change, "The lecture's teaching assignment no longer exists."));
				continue;
			}
			wanted.put(change, claimsOf(occurrence, assignment, change));
		}
		conflicts.addAll(clashes(wanted, current, moving));
		return conflicts.stream().map((conflict) -> withoutPlaceholders(conflict, created)).toList();
	}

	/**
	 * The lecture a change to create one would make, as it would be: the slot it asks for, the teacher and place of the
	 * teaching assignment, and the timetable in force that day. It exists only while the change is checked. Says why not, in
	 * {@code problems}, when the lecture cannot be planned.
	 */
	private Optional<Occurrence> planned(LectureChange change, AssignmentDetails assignment, List<LectureConflict> problems) {
		if (assignment == null) {
			problems.add(changed(change, "The teaching assignment of the lecture no longer exists."));
			return Optional.empty();
		}
		Optional<TimetableVersion> version = this.timetables.publishedInForce(assignment.divisionId(), change.target().date());
		if (version.isEmpty()) {
			problems.add(new LectureConflict(ConflictType.OUTSIDE_CALENDAR, change.occurrenceId(),
					"No published timetable of the division is in force on " + change.target().date() + ".", null, null));
			return Optional.empty();
		}
		return Optional.of(new Occurrence(change.occurrenceId(), null, null, version.get().id(), assignment.id(),
				assignment.departmentId(), assignment.divisionId(), assignment.batchId(), assignment.sessionType(),
				assignment.facultyId(), assignment.facultyId(), change.target(), change.target(), false, null, 0));
	}

	/** A lecture that does not exist yet has no ID to report. */
	private static LectureConflict withoutPlaceholders(LectureConflict conflict, Set<UUID> placeholders) {
		boolean own = placeholders.contains(conflict.occurrenceId());
		boolean other = placeholders.contains(conflict.conflictingOccurrenceId());
		if (!own && !other) {
			return conflict;
		}
		return new LectureConflict(conflict.type(), own ? null : conflict.occurrenceId(), conflict.message(),
				other ? null : conflict.conflictingOccurrenceId(), conflict.conflictingTicketId());
	}

	/** The teaching assignments of the lectures that exist and of those to be created. */
	private static Set<UUID> assignmentIds(List<LectureChange> changes, Map<UUID, Occurrence> existing) {
		Set<UUID> ids = existing.values().stream().map(Occurrence::assignmentId).collect(Collectors.toCollection(HashSet::new));
		changes.stream().filter(LectureChange::isCreate).forEach((change) -> ids.add(change.assignmentId()));
		return ids;
	}

	/**
	 * What the lecture needs once the change is made: the slot it is moved to, or the one it has when only its teacher
	 * changes, and the teacher it has then.
	 */
	private static Set<ClaimKey> claimsOf(Occurrence occurrence, AssignmentDetails assignment, LectureChange change) {
		LectureSlot slot = change.isReassign() ? occurrence.slot() : change.target();
		UUID teacher = (change.isReassign() || change.isRestore()) ? change.facultyId() : occurrence.currentFacultyId();
		return Claims.keysFor(assignment.sessionType(), teacher, slot.roomId(), assignment.divisionId(),
				assignment.batchIds(), slot.date(), slot.startTime(), slot.endTime());
	}

	private List<LectureConflict> clashes(Map<LectureChange, Set<ClaimKey>> wanted, Map<UUID, Occurrence> current,
			Set<UUID> moving) {
		if (wanted.isEmpty()) {
			return List.of();
		}
		List<LectureConflict> conflicts = new ArrayList<>(againstEachOther(wanted, current));
		Set<ClaimKey> everything = wanted.values().stream().flatMap(Set::stream).collect(Collectors.toCollection(LinkedHashSet::new));
		Map<ClaimKey, UUID> holders = this.claims.holders(everything);
		Set<UUID> others = holders.values().stream().filter((holder) -> !moving.contains(holder)).collect(Collectors.toSet());
		if (others.isEmpty()) {
			return conflicts;
		}
		Map<UUID, Occurrence> holding = byId(this.occurrences.findAll(others));
		Map<UUID, RoomDetails> rooms = this.directory.rooms(everything.stream()
			.filter((key) -> key.kind() == ClaimKind.ROOM)
			.map(ClaimKey::resourceId)
			.collect(Collectors.toSet()));

		for (Map.Entry<LectureChange, Set<ClaimKey>> entry : wanted.entrySet()) {
			SessionType movingType = current.get(entry.getKey().occurrenceId()).sessionType();
			Set<String> reported = new HashSet<>();
			for (ClaimKey key : entry.getValue()) {
				UUID holder = holders.get(key);
				if (holder == null || moving.contains(holder)) {
					continue;
				}
				Occurrence other = holding.get(holder);
				ConflictType type = typeOf(key.kind(), movingType, other);
				if (reported.add(type + ":" + holder)) {
					conflicts.add(new LectureConflict(type, entry.getKey().occurrenceId(), describe(type, key, other, rooms),
							holder, (other == null) ? null : other.changedByTicketId()));
				}
			}
		}
		return conflicts;
	}

	/** Two lectures changed together cannot need the same thing either; the second to ask is the one reported. */
	private static List<LectureConflict> againstEachOther(Map<LectureChange, Set<ClaimKey>> wanted,
			Map<UUID, Occurrence> current) {
		List<LectureConflict> conflicts = new ArrayList<>();
		Map<ClaimKey, UUID> claimedBy = new HashMap<>();
		for (Map.Entry<LectureChange, Set<ClaimKey>> entry : wanted.entrySet()) {
			UUID occurrenceId = entry.getKey().occurrenceId();
			SessionType type = current.get(occurrenceId).sessionType();
			Set<String> reported = new HashSet<>();
			for (ClaimKey key : entry.getValue()) {
				UUID earlier = claimedBy.putIfAbsent(key, occurrenceId);
				if (earlier != null && !earlier.equals(occurrenceId)
						&& reported.add(typeOf(key.kind(), type, null) + ":" + earlier)) {
					conflicts.add(new LectureConflict(typeOf(key.kind(), type, null), occurrenceId,
							"Two of the lectures being changed need the same slot, on " + key.date() + " at " + key.hour()
									+ ":00.",
							earlier, null));
				}
			}
		}
		return conflicts;
	}

	/** A lecture another ticket moved into the slot is a clash of tickets; otherwise what is held says what is in the way. */
	private static ConflictType typeOf(ClaimKind kind, SessionType movingType, Occurrence holder) {
		if (holder != null && holder.changedByTicketId() != null) {
			return ConflictType.SAME_SLOT_TICKET;
		}
		return switch (kind) {
			case FACULTY -> ConflictType.FACULTY_BUSY;
			case ROOM -> ConflictType.ROOM_OCCUPIED;
			case DIVISION -> ConflictType.DIVISION_BUSY;
			case STUDENT_BATCH -> (movingType == SessionType.THEORY) ? ConflictType.DIVISION_BUSY : ConflictType.BATCH_BUSY;
		};
	}

	private static String describe(ConflictType type, ClaimKey key, Occurrence holder, Map<UUID, RoomDetails> rooms) {
		String when = (holder == null) ? " at " + key.hour() + ":00 on " + key.date()
				: " on " + holder.slot().date() + " from " + holder.slot().startTime() + " to " + holder.slot().endTime();
		return switch (type) {
			case FACULTY_BUSY -> "The faculty member already has a lecture" + when + ".";
			case ROOM_OCCUPIED -> "Room " + ((rooms.get(key.resourceId()) == null) ? "" : rooms.get(key.resourceId()).code() + " ")
					+ "is already in use" + when + ".";
			case DIVISION_BUSY -> "The division already has a lecture" + when + ".";
			case BATCH_BUSY -> "The batch already has a lecture" + when + ".";
			case SAME_SLOT_TICKET -> "Another ticket already moved a lecture into this slot" + when + ".";
			default -> "The slot is held" + when + ".";
		};
	}

	/** The calendar: never a Sunday, never outside the term, and always on the hour between 08:00 and 20:00. */
	private List<LectureConflict> calendarProblems(Occurrence occurrence, LectureChange change) {
		LectureSlot target = change.target();
		List<LectureConflict> problems = new ArrayList<>();
		if (target.date().getDayOfWeek() == DayOfWeek.SUNDAY) {
			problems.add(new LectureConflict(ConflictType.OUTSIDE_CALENDAR, change.occurrenceId(),
					target.date() + " is a Sunday.", null, null));
		}
		Optional<TermDetails> term = this.timetables.findVersion(occurrence.timetableVersionId())
			.flatMap((version) -> this.directory.term(version.termId()));
		if (term.isPresent()
				&& (target.date().isBefore(term.get().startDate()) || target.date().isAfter(term.get().endDate()))) {
			problems.add(new LectureConflict(ConflictType.OUTSIDE_CALENDAR, change.occurrenceId(),
					target.date() + " is outside the term, which runs from " + term.get().startDate() + " to "
							+ term.get().endDate() + ".",
					null, null));
		}
		if (!onTheHour(target.startTime()) || !onTheHour(target.endTime()) || !target.startTime().isBefore(target.endTime())
				|| target.startTime().isBefore(DAY_STARTS) || target.endTime().isAfter(DAY_ENDS)) {
			problems.add(new LectureConflict(ConflictType.OUTSIDE_SLOT_GRID, change.occurrenceId(),
					"A lecture starts and ends on the hour, between " + DAY_STARTS + " and " + DAY_ENDS + ".", null, null));
		}
		return problems;
	}

	private static boolean onTheHour(LocalTime time) {
		return time.getMinute() == 0 && time.getSecond() == 0 && time.getNano() == 0;
	}

	private static LectureConflict changed(LectureChange change, String message) {
		return new LectureConflict(ConflictType.OCCURRENCE_CHANGED, change.occurrenceId(), message, null, null);
	}

	private static List<UUID> occurrenceIds(List<LectureChange> changes) {
		return changes.stream().map(LectureChange::occurrenceId).distinct().toList();
	}

	private static Map<UUID, Occurrence> byId(List<Occurrence> occurrences) {
		return occurrences.stream().collect(Collectors.toMap(Occurrence::id, Function.identity()));
	}

}
