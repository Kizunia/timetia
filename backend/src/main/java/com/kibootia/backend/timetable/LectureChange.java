package com.kibootia.backend.timetable;

import java.util.UUID;

/**
 * A change to one lecture: move it to {@code target}, cancel it, put {@code facultyId} in front of it in place of its teacher
 * (a substitute), add a lecture for {@code assignmentId} at {@code target} (an extra lecture), or restore it to
 * {@code target} with {@code facultyId} teaching it, not cancelled (a revert). Exactly one of the five. It is made against
 * the state the lecture was in when the change was planned: if the lecture has changed since ({@code expectedRevision} no
 * longer matches), the change is refused, so a ticket never overwrites what another ticket did.
 *
 * <p>A lecture that does not exist yet has no ID. {@code occurrenceId} of a new one is made up for the change, only so that
 * several changes made together can be told apart; it is never stored, and the conflicts reported for it name no lecture.
 */
public record LectureChange(UUID occurrenceId, long expectedRevision, LectureSlot target, UUID facultyId, UUID assignmentId,
		boolean restoring) {

	public static LectureChange move(UUID occurrenceId, long expectedRevision, LectureSlot target) {
		return new LectureChange(occurrenceId, expectedRevision, target, null, null, false);
	}

	public static LectureChange cancel(UUID occurrenceId, long expectedRevision) {
		return new LectureChange(occurrenceId, expectedRevision, null, null, null, false);
	}

	public static LectureChange reassign(UUID occurrenceId, long expectedRevision, UUID facultyId) {
		return new LectureChange(occurrenceId, expectedRevision, null, facultyId, null, false);
	}

	/** A new lecture of the teaching assignment at the slot. */
	public static LectureChange create(UUID assignmentId, LectureSlot target) {
		return new LectureChange(UUID.randomUUID(), 0, target, null, assignmentId, false);
	}

	/** The lecture back at the slot, taught by the faculty member, whether or not a change cancelled it. */
	public static LectureChange restore(UUID occurrenceId, long expectedRevision, LectureSlot target, UUID facultyId) {
		return new LectureChange(occurrenceId, expectedRevision, target, facultyId, null, true);
	}

	public boolean isCreate() {
		return this.assignmentId != null;
	}

	public boolean isCancel() {
		return this.target == null && this.facultyId == null && this.assignmentId == null;
	}

	public boolean isReassign() {
		return this.facultyId != null && !this.restoring;
	}

	public boolean isRestore() {
		return this.restoring;
	}

}
