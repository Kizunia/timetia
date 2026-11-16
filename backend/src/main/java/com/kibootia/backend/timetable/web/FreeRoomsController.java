package com.kibootia.backend.timetable.web;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.timetable.application.RoomAvailabilityService;

/**
 * The rooms that are free at a time. Any signed-in person with a verified email may ask, because the rooms lecturers hold
 * are already visible in the timetable. It answers at most as many rooms as the campus has (a few hundred at the most).
 */
@RestController
@RequestMapping("/api/v1/timetable/free-rooms")
class FreeRoomsController {

	private final RoomAvailabilityService rooms;

	FreeRoomsController(RoomAvailabilityService rooms) {
		this.rooms = rooms;
	}

	/**
	 * @param date the day; not a Sunday
	 * @param startTime on the hour, 08:00 or later
	 * @param endTime on the hour, after the start, 20:00 at the latest
	 * @param sessionType what the room is for; rooms of a suitable kind come first and are marked {@code fits}
	 * @param excludeLecture a lecture that is about to move; the room it holds now counts as free for it
	 */
	@GetMapping
	List<FreeRoomResponse> list(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
			@RequestParam(required = false) SessionType sessionType,
			@RequestParam(required = false) UUID excludeLecture) {
		return this.rooms.freeRooms(date, startTime, endTime, sessionType, excludeLecture)
			.stream()
			.map((room) -> new FreeRoomResponse(room.id(), room.code(), room.name(), room.roomType(), room.capacity(),
					room.fits()))
			.toList();
	}

}
