package com.kibootia.backend.timetable.domain;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kibootia.backend.timetable.domain.Clashes.Clash;
import com.kibootia.backend.timetable.domain.Clashes.Kind;
import com.kibootia.backend.timetable.domain.Clashes.Weekly;

import static org.assertj.core.api.Assertions.assertThat;

class ClashesTest {

	static final UUID DIVISION = UUID.randomUUID();

	static final UUID OTHER_DIVISION = UUID.randomUUID();

	static final UUID BATCH_1 = UUID.randomUUID();

	static final UUID BATCH_2 = UUID.randomUUID();

	static final UUID ROOM_A = UUID.randomUUID();

	static final UUID ROOM_B = UUID.randomUUID();

	private static LocalTime at(int hour) {
		return LocalTime.of(hour, 0);
	}

	private static Weekly lecture(int day, int from, int to, UUID faculty, UUID room, UUID division, UUID batch) {
		return new Weekly(UUID.randomUUID(), day, at(from), at(to), faculty, room, division, batch);
	}

	@Test
	@DisplayName("lectures that meet end to end do not overlap")
	void endToEndIsNotAnOverlap() {
		UUID faculty = UUID.randomUUID();
		List<Clash<Weekly>> clashes = Clashes.within(List.of(lecture(1, 9, 10, faculty, ROOM_A, DIVISION, null),
				lecture(1, 10, 11, faculty, ROOM_A, DIVISION, null)));

		assertThat(clashes).isEmpty();
	}

	@Test
	@DisplayName("the same faculty member cannot be in two lectures at once, even for different divisions")
	void sameFacultyClashes() {
		UUID faculty = UUID.randomUUID();
		List<Clash<Weekly>> clashes = Clashes.within(List.of(lecture(2, 9, 11, faculty, ROOM_A, DIVISION, null),
				lecture(2, 10, 12, faculty, ROOM_B, OTHER_DIVISION, null)));

		assertThat(clashes).singleElement().extracting(Clash::kind).isEqualTo(Kind.FACULTY);
	}

	@Test
	@DisplayName("the same room cannot hold two lectures at once, and online lectures share no room")
	void sameRoomClashes() {
		List<Clash<Weekly>> clashes = Clashes.within(List.of(
				lecture(3, 9, 10, UUID.randomUUID(), ROOM_A, DIVISION, BATCH_1),
				lecture(3, 9, 10, UUID.randomUUID(), ROOM_A, OTHER_DIVISION, null)));
		List<Clash<Weekly>> online = Clashes.within(List.of(
				lecture(3, 9, 10, UUID.randomUUID(), null, DIVISION, BATCH_1),
				lecture(3, 9, 10, UUID.randomUUID(), null, OTHER_DIVISION, null)));

		assertThat(clashes).singleElement().extracting(Clash::kind).isEqualTo(Kind.ROOM);
		assertThat(online).isEmpty();
	}

	@Test
	@DisplayName("a theory lecture of a division clashes with a lab of one of its batches, since the batch is in the division")
	void theoryClashesWithALabOfTheSameDivision() {
		List<Clash<Weekly>> clashes = Clashes.within(List.of(
				lecture(4, 9, 10, UUID.randomUUID(), ROOM_A, DIVISION, null),
				lecture(4, 9, 11, UUID.randomUUID(), ROOM_B, DIVISION, BATCH_1)));

		assertThat(clashes).singleElement().extracting(Clash::kind).isEqualTo(Kind.STUDENTS);
	}

	@Test
	@DisplayName("two batches of one division may have labs at the same time, but one batch cannot have two")
	void parallelLabsAreAllowedForDifferentBatchesOnly() {
		assertThat(Clashes.within(List.of(
				lecture(5, 9, 11, UUID.randomUUID(), ROOM_A, DIVISION, BATCH_1),
				lecture(5, 9, 11, UUID.randomUUID(), ROOM_B, DIVISION, BATCH_2)))).isEmpty();

		assertThat(Clashes.within(List.of(
				lecture(5, 9, 11, UUID.randomUUID(), ROOM_A, DIVISION, BATCH_1),
				lecture(5, 10, 12, UUID.randomUUID(), ROOM_B, DIVISION, BATCH_1))))
			.singleElement()
			.extracting(Clash::kind)
			.isEqualTo(Kind.STUDENTS);
	}

	@Test
	@DisplayName("lectures on different days never clash")
	void differentDaysDoNotClash() {
		UUID faculty = UUID.randomUUID();
		assertThat(Clashes.within(List.of(lecture(1, 9, 10, faculty, ROOM_A, DIVISION, null),
				lecture(2, 9, 10, faculty, ROOM_A, DIVISION, null)))).isEmpty();
	}

}
