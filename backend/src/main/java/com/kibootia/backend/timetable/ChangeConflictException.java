package com.kibootia.backend.timetable;

import java.util.List;

/**
 * A change was refused because of conflicts. Nothing was written when it is thrown, which is why the port's {@code apply}
 * does not mark the caller's transaction for rollback: the caller can record the conflicts and commit.
 */
public class ChangeConflictException extends RuntimeException {

	private final transient List<LectureConflict> conflicts;

	public ChangeConflictException(List<LectureConflict> conflicts) {
		super(conflicts.size() + " conflict(s) block the change");
		this.conflicts = List.copyOf(conflicts);
	}

	public List<LectureConflict> conflicts() {
		return this.conflicts;
	}

}
