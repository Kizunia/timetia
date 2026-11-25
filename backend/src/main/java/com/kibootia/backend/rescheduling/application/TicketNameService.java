package com.kibootia.backend.rescheduling.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.directory.UserDirectory;
import com.kibootia.backend.platform.directory.UserSummary;
import com.kibootia.backend.reference.AcademicDirectory;
import com.kibootia.backend.reference.RoomDetails;
import com.kibootia.backend.timetable.LectureCalendar;
import com.kibootia.backend.timetable.LectureView;
import com.kibootia.backend.timetable.LectureSlot;

/**
 * Looks up the names behind the ids of tickets so that the screens never have to: the people, the teachers, the rooms and the
 * subject. It costs one lookup for each kind of name for the whole page of tickets, not one for each row. The lectures of the
 * changes are read one by one, and only for the detail of a ticket, where there are at most as many as the ticket changes.
 */
@Service
public class TicketNameService {

	private final UserDirectory users;

	private final AcademicDirectory directory;

	private final LectureCalendar calendar;

	TicketNameService(UserDirectory users, AcademicDirectory directory, LectureCalendar calendar) {
		this.users = users;
		this.directory = directory;
		this.calendar = calendar;
	}

	/**
	 * @param details tickets the caller has already been allowed to see
	 * @param withLectures also read each changed lecture as it is now (for the detail of a ticket, not for a list)
	 */
	@Transactional(readOnly = true)
	@NoAuthorization("adds names to tickets the caller has already been allowed to see")
	public TicketNames namesFor(Collection<TicketDetail> details, boolean withLectures) {
		if (details.isEmpty()) {
			return TicketNames.NONE;
		}
		Set<UUID> userIds = new HashSet<>();
		Set<UUID> facultyIds = new HashSet<>();
		Set<UUID> roomIds = new HashSet<>();
		Set<UUID> assignmentIds = new HashSet<>();
		Set<UUID> lectureIds = new HashSet<>();
		for (TicketDetail detail : details) {
			Ticket ticket = detail.ticket();
			userIds.add(ticket.authorUserId());
			facultyIds.add(ticket.lecture().facultyId());
			assignmentIds.add(ticket.lecture().assignmentId());
			detail.reviews().forEach((review) -> userIds.add(review.reviewerUserId()));
			for (TicketChange change : detail.changes()) {
				addIfPresent(facultyIds, change.facultyId());
				addIfPresent(facultyIds, change.substituteFacultyId());
				addIfPresent(facultyIds, change.beforeFacultyId());
				addIfPresent(roomIds, roomOf(change.target()));
				addIfPresent(roomIds, roomOf(change.before()));
				if (withLectures) {
					addIfPresent(lectureIds, change.occurrenceId());
				}
			}
		}
		Map<UUID, String> userNames = this.users.find(userIds)
			.values()
			.stream()
			.collect(Collectors.toMap(UserSummary::id, UserSummary::displayName, (first, second) -> first));
		Map<UUID, LectureView> lectures = new HashMap<>();
		lectureIds.forEach((id) -> this.calendar.lecture(id).ifPresent((lecture) -> lectures.put(id, lecture)));
		// A lecture's teacher and room are names too, but the lecture view already carries them (facultyName, roomCode).
		return new TicketNames(userNames, this.directory.faculties(facultyIds), roomCodes(roomIds),
				this.directory.assignments(assignmentIds), lectures);
	}

	private Map<UUID, String> roomCodes(Set<UUID> roomIds) {
		if (roomIds.isEmpty()) {
			return Map.of();
		}
		Map<UUID, String> codes = new HashMap<>();
		for (Map.Entry<UUID, RoomDetails> room : this.directory.rooms(roomIds).entrySet()) {
			codes.put(room.getKey(), room.getValue().code());
		}
		return codes;
	}

	private static UUID roomOf(LectureSlot slot) {
		return (slot == null) ? null : slot.roomId();
	}

	private static void addIfPresent(Set<UUID> ids, UUID id) {
		if (id != null) {
			ids.add(id);
		}
	}

}
