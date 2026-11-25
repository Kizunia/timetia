package com.kibootia.backend.timetable.application;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.reports.CsvReport;
import com.kibootia.backend.platform.reports.ReportExportException;
import com.kibootia.backend.timetable.LectureView;
import com.kibootia.backend.timetable.application.LiveTimetableService.LiveFilter;

/**
 * Writes the live timetable to a CSV file: one line for each lecture in a date range, with where, when, who teaches it and
 * whether a change has been laid over it. The lectures are read through {@link LiveTimetableService}, so a report holds only
 * the lectures the signed-in person may see, exactly as the list does.
 */
@Service
public class TimetableReportService {

	private static final int PAGE_SIZE = 200;

	private static final List<String> COLUMNS = List.of("Date", "Start", "End", "Division", "Batch", "Subject code",
			"Subject", "Session", "Faculty", "Room", "Status", "Changed by ticket");

	private final LiveTimetableService live;

	TimetableReportService(LiveTimetableService live) {
		this.live = live;
	}

	/**
	 * Writes the report to the file, replacing it, and returns how many lectures it holds.
	 *
	 * @throws ReportExportException when the file cannot be written
	 */
	@Transactional(readOnly = true)
	@NoAuthorization("the live timetable it reads through decides which lectures the caller may see")
	public int exportLive(LiveFilter filter, Path file) throws ReportExportException {
		try (CsvReport.Writer report = CsvReport.open(file, COLUMNS)) {
			Pageable pageable = PageRequest.of(0, PAGE_SIZE, Sort.by("effectiveDate", "effectiveStartTime"));
			Page<LectureView> page;
			do {
				page = this.live.list(filter, pageable);
				for (LectureView lecture : page) {
					report.write(row(lecture));
				}
				pageable = page.nextPageable();
			}
			while (page.hasNext());
			return report.rows();
		}
	}

	private static List<String> row(LectureView lecture) {
		return Arrays.asList(lecture.slot().date().toString(), lecture.slot().startTime().toString(),
				lecture.slot().endTime().toString(), lecture.divisionCode(), lecture.batchCode(), lecture.subjectCode(),
				lecture.subjectName(), lecture.sessionType().name(), lecture.facultyName(),
				(lecture.roomCode() == null) ? "ONLINE" : lecture.roomCode(), status(lecture),
				(lecture.changedByTicketId() == null) ? null : lecture.changedByTicketId().toString());
	}

	private static String status(LectureView lecture) {
		if (lecture.cancelled()) {
			return "CANCELLED";
		}
		if (lecture.substituted()) {
			return "SUBSTITUTED";
		}
		return lecture.changed() ? "CHANGED" : "AS PUBLISHED";
	}

}
