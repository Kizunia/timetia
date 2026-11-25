package com.kibootia.backend.timetable.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kibootia.backend.academic.SessionType;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimsTest {

	static final UUID FACULTY = UUID.randomUUID();

	static final UUID ROOM = UUID.randomUUID();

	static final UUID DIVISION = UUID.randomUUID();

	static final UUID BATCH_1 = UUID.randomUUID();

	static final UUID BATCH_2 = UUID.randomUUID();

	static final LocalDate DATE = LocalDate.of(2026, 7, 6);

	private static Set<ClaimKey> keys(SessionType type, UUID room, Set<UUID> batches, int from, int to) {
		return Claims.keysFor(type, FACULTY, room, DIVISION, batches, DATE, LocalTime.of(from, 0), LocalTime.of(to, 0));
	}

	private static Set<ClaimKind> kindsOf(Set<ClaimKey> keys) {
		return keys.stream().map(ClaimKey::kind).collect(Collectors.toSet());
	}

	@Test
	@DisplayName("a theory lecture holds its faculty member, its room, the whole division and every batch in it")
	void theoryHoldsTheDivisionAndEveryBatch() {
		Set<ClaimKey> keys = keys(SessionType.THEORY, ROOM, Set.of(BATCH_1, BATCH_2), 9, 10);

		assertThat(keys).containsExactlyInAnyOrder(new ClaimKey(ClaimKind.FACULTY, FACULTY, DATE, 9),
				new ClaimKey(ClaimKind.ROOM, ROOM, DATE, 9), new ClaimKey(ClaimKind.DIVISION, DIVISION, DATE, 9),
				new ClaimKey(ClaimKind.STUDENT_BATCH, BATCH_1, DATE, 9),
				new ClaimKey(ClaimKind.STUDENT_BATCH, BATCH_2, DATE, 9));
	}

	@Test
	@DisplayName("a lab holds only its own batch, not the division, so another batch's lab can run at the same time")
	void labHoldsOnlyItsBatch() {
		Set<ClaimKey> first = keys(SessionType.LAB, ROOM, Set.of(BATCH_1), 9, 10);
		Set<ClaimKey> second = Claims.keysFor(SessionType.LAB, UUID.randomUUID(), UUID.randomUUID(), DIVISION,
				Set.of(BATCH_2), DATE, LocalTime.of(9, 0), LocalTime.of(10, 0));

		assertThat(kindsOf(first)).containsExactlyInAnyOrder(ClaimKind.FACULTY, ClaimKind.ROOM, ClaimKind.STUDENT_BATCH);
		assertThat(first).doesNotContainAnyElementsOf(second);
	}

	@Test
	@DisplayName("a theory lecture and a lab of one of its batches hold the same batch, so they cannot overlap")
	void theoryAndLabOfTheDivisionShareABatch() {
		Set<ClaimKey> theory = keys(SessionType.THEORY, null, Set.of(BATCH_1, BATCH_2), 9, 10);
		Set<ClaimKey> lab = Claims.keysFor(SessionType.LAB, UUID.randomUUID(), UUID.randomUUID(), DIVISION, Set.of(BATCH_1),
				DATE, LocalTime.of(9, 0), LocalTime.of(11, 0));

		assertThat(theory).contains(new ClaimKey(ClaimKind.STUDENT_BATCH, BATCH_1, DATE, 9));
		assertThat(lab).contains(new ClaimKey(ClaimKind.STUDENT_BATCH, BATCH_1, DATE, 9));
	}

	@Test
	@DisplayName("two theory lectures of a division hold the division even when it has no batches")
	void theoryHoldsTheDivisionWithoutBatches() {
		assertThat(keys(SessionType.THEORY, null, Set.of(), 9, 10))
			.containsExactlyInAnyOrder(new ClaimKey(ClaimKind.FACULTY, FACULTY, DATE, 9),
					new ClaimKey(ClaimKind.DIVISION, DIVISION, DATE, 9));
	}

	@Test
	@DisplayName("a lecture holds each hour it lasts, so a two hour lab holds two")
	void holdsEveryHour() {
		List<Integer> hours = keys(SessionType.LAB, ROOM, Set.of(BATCH_1), 9, 11).stream()
			.filter((key) -> key.kind() == ClaimKind.ROOM)
			.map(ClaimKey::hour)
			.sorted()
			.toList();

		assertThat(hours).containsExactly(9, 10);
	}

	@Test
	@DisplayName("an online lecture holds no room")
	void onlineHoldsNoRoom() {
		assertThat(kindsOf(keys(SessionType.THEORY, null, Set.of(BATCH_1), 14, 15))).doesNotContain(ClaimKind.ROOM);
	}

}
