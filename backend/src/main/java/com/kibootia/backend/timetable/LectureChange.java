package com.kibootia.backend.timetable;

import java.util.UUID;

/**
 * A change to one lecture: move it to {@code target}, or cancel it when {@code target} is null. It is made against the
 * state the lecture was in when the change was planned: if the lecture has changed since ({@code expectedRevision} no longer
 * matches), the change is refused, so a ticket never overwrites what another ticket did.
 */
public record LectureChange(UUID occurrenceId, long expectedRevision, LectureSlot target) {

	public static LectureChange move(UUID occurrenceId, long expectedRevision, LectureSlot target) {
		return new LectureChange(occurrenceId, expectedRevision, target);
	}

	public static LectureChange cancel(UUID occurrenceId, long expectedRevision) {
		return new LectureChange(occurrenceId, expectedRevision, null);
	}

	public boolean isCancel() {
		return this.target == null;
	}

}
