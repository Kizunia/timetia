package com.kibootia.backend.timetable.application;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.errors.ApiFieldError;
import com.kibootia.backend.platform.errors.ProblemCodes;
import com.kibootia.backend.reference.AcademicDirectory;
import com.kibootia.backend.reference.RoomOption;
import com.kibootia.backend.timetable.domain.ClaimKey;
import com.kibootia.backend.timetable.domain.ClaimKind;

/**
 * Which rooms are free at a time, so a screen can offer a room instead of asking for a room id and waiting for a conflict. A
 * room is free when no lecture holds it in any hour of the time asked. A lecture that is to move may be named, because it
 * will leave the room it holds, and its own hours must not count against it. Cancelled lectures hold nothing, so their rooms
 * are free, and an online lecture holds no room.
 */
@Service
public class RoomAvailabilityService {

	private static final LocalTime DAY_STARTS = LocalTime.of(8, 0);

	private static final LocalTime DAY_ENDS = LocalTime.of(20, 0);

	private static final String CLASSROOM = "CLASSROOM";

	private static final String TUTORIAL_ROOM = "TUTORIAL_ROOM";

	private static final String LAB = "LAB";

	/**
	 * A room that is free. {@code fits} says whether its kind suits the session; a screen lists the ones that fit first and
	 * may still offer the others, because the timetable does not refuse a lecture in a room of another kind.
	 */
	public record FreeRoom(UUID id, String code, String name, String roomType, Integer capacity, boolean fits) {

	}

	private final AcademicDirectory directory;

	private final ClaimStore claims;

	private final Authorization authorization;

	RoomAvailabilityService(AcademicDirectory directory, ClaimStore claims, Authorization authorization) {
		this.directory = directory;
		this.claims = claims;
		this.authorization = authorization;
	}

	/**
	 * @param date a day other than Sunday
	 * @param start on the hour, from 08:00
	 * @param end on the hour, after {@code start}, up to 20:00
	 * @param sessionType what the room is for, to order the rooms that suit it first; null for no preference
	 * @param excludeLecture a lecture whose own claim on a room does not count, or null
	 */
	@Transactional(readOnly = true)
	@NoAuthorization("room occupancy is already visible to anyone who sees the timetable; any verified person asks")
	public List<FreeRoom> freeRooms(LocalDate date, LocalTime start, LocalTime end, SessionType sessionType,
			UUID excludeLecture) {
		this.authorization.requireVerified();
		validate(date, start, end);
		List<Integer> hours = new ArrayList<>();
		for (int hour = start.getHour(); hour < end.getHour(); hour++) {
			hours.add(hour);
		}
		List<RoomOption> rooms = this.directory.roomOptions();
		Map<ClaimKey, UUID> held = this.claims.holdersOf(ClaimKind.ROOM, rooms.stream().map(RoomOption::id).toList(), date,
				hours);
		List<FreeRoom> free = new ArrayList<>();
		for (RoomOption room : rooms) {
			if (isFree(room, date, hours, held, excludeLecture)) {
				free.add(new FreeRoom(room.id(), room.code(), room.name(), room.roomType(), room.capacity(),
						fits(room.roomType(), sessionType)));
			}
		}
		free.sort(Comparator.comparing((FreeRoom room) -> !room.fits()).thenComparing(FreeRoom::code));
		return free;
	}

	private static boolean isFree(RoomOption room, LocalDate date, List<Integer> hours, Map<ClaimKey, UUID> held,
			UUID excludeLecture) {
		for (int hour : hours) {
			UUID holder = held.get(new ClaimKey(ClaimKind.ROOM, room.id(), date, hour));
			if (holder != null && !holder.equals(excludeLecture)) {
				return false;
			}
		}
		return true;
	}

	/** A lab needs a lab, theory a classroom, and a tutorial a tutorial room or a classroom; no session type suits all. */
	private static boolean fits(String roomType, SessionType sessionType) {
		if (sessionType == null) {
			return true;
		}
		return switch (sessionType) {
			case LAB -> LAB.equals(roomType);
			case THEORY -> CLASSROOM.equals(roomType);
			case TUTORIAL -> TUTORIAL_ROOM.equals(roomType) || CLASSROOM.equals(roomType);
		};
	}

	private static void validate(LocalDate date, LocalTime start, LocalTime end) {
		List<ApiFieldError> errors = new ArrayList<>();
		if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
			errors.add(new ApiFieldError("date", "sunday", "There are no lectures on a Sunday."));
		}
		if (!onTheHour(start) || !onTheHour(end) || !start.isBefore(end) || start.isBefore(DAY_STARTS)
				|| end.isAfter(DAY_ENDS)) {
			errors.add(new ApiFieldError("startTime", "invalid_time", "A lecture starts and ends on the hour, between "
					+ DAY_STARTS + " and " + DAY_ENDS + ", and ends after it starts."));
		}
		if (!errors.isEmpty()) {
			throw ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.", errors);
		}
	}

	private static boolean onTheHour(LocalTime time) {
		return time.getMinute() == 0 && time.getSecond() == 0 && time.getNano() == 0;
	}

}
