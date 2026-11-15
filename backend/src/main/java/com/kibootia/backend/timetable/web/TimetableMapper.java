package com.kibootia.backend.timetable.web;

import com.kibootia.backend.timetable.LectureSlot;
import com.kibootia.backend.timetable.LectureView;
import com.kibootia.backend.timetable.application.NewEntry;
import com.kibootia.backend.timetable.application.TimetableAuthoringService.Published;
import com.kibootia.backend.timetable.application.TimetableEntry;
import com.kibootia.backend.timetable.application.TimetableVersion;

/** Converts between API types and application types by hand (ADR 0005). */
final class TimetableMapper {

	private TimetableMapper() {
	}

	static NewEntry toNewEntry(CreateEntryRequest request) {
		return new NewEntry(request.assignmentId(), request.dayOfWeek(), request.startTime(), request.endTime(),
				request.roomId(), request.mode(), request.meetingLink());
	}

	static TimetableVersionResponse toResponse(TimetableVersion version) {
		return new TimetableVersionResponse(version.id(), version.divisionId(), version.termId(), version.effectiveFrom(),
				version.status(), version.version(), version.createdAt(), version.updatedAt());
	}

	static TimetableEntryResponse toResponse(TimetableEntry entry) {
		return new TimetableEntryResponse(entry.id(), entry.timetableVersionId(), entry.assignmentId(), entry.dayOfWeek(),
				entry.startTime(), entry.endTime(), entry.roomId(), entry.mode(), entry.meetingLink());
	}

	static PublishedVersionResponse toResponse(Published published) {
		return new PublishedVersionResponse(toResponse(published.version()), published.occurrencesCreated(),
				published.occurrencesSuperseded());
	}

	static LiveLectureResponse toResponse(LectureView lecture) {
		LectureSlot slot = lecture.slot();
		LectureSlot base = lecture.baseSlot();
		// The original time is shown only when the lecture was moved; a cancelled lecture is where it always was.
		boolean moved = !slot.date().equals(base.date()) || !slot.startTime().equals(base.startTime())
				|| !slot.endTime().equals(base.endTime());
		return new LiveLectureResponse(lecture.id(), slot.date(), slot.startTime(), slot.endTime(), lecture.sessionType(),
				slot.mode(), lecture.divisionId(), lecture.divisionCode(), lecture.batchId(), lecture.batchCode(),
				lecture.subjectCode(), lecture.subjectName(), lecture.facultyId(), lecture.facultyName(), slot.roomId(),
				lecture.roomCode(), slot.meetingLink(), lecture.cancelled(), lecture.changed(),
				moved ? base.date() : null, moved ? base.startTime() : null, moved ? base.endTime() : null);
	}

}
