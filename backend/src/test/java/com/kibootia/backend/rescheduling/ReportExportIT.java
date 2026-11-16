package com.kibootia.backend.rescheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.kibootia.backend.platform.reports.ReportExportException;
import com.kibootia.backend.platform.tenancy.TenantContext;
import com.kibootia.backend.rescheduling.application.TicketReportService;
import com.kibootia.backend.rescheduling.application.TicketService.TicketFilter;
import com.kibootia.backend.timetable.application.LiveTimetableService.LiveFilter;
import com.kibootia.backend.timetable.application.TimetableReportService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The reports written to files: the live timetable and the tickets, read through the same use cases as the lists, on the
 * standard campus and timetable of {@link ReschedulingApiTest}.
 */
class ReportExportIT extends ReschedulingApiTest {

	@Autowired
	TimetableReportService timetableReport;

	@Autowired
	TicketReportService ticketReport;

	@TempDir
	Path folder;

	@Test
	@DisplayName("the live timetable is written with a line for each lecture in the range")
	void theTimetableIsWritten() throws Exception {
		Path file = this.folder.resolve("timetable.csv");
		LiveFilter week = new LiveFilter(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12), null, null, null);

		int written = asCaller(ADMIN, () -> this.timetableReport.exportLive(week, file));

		List<String> lines = Files.readAllLines(file);
		Integer inTheDatabase = this.jdbc.queryForObject(
				"select count(*) from lecture_occurrence where record_status = 'ACTIVE' and effective_date between ? and ?",
				Integer.class, "2026-07-06", "2026-07-12");
		assertThat(written).isEqualTo(inTheDatabase).isPositive();
		assertThat(lines).hasSize(written + 1);
		assertThat(lines.get(0)).isEqualTo("﻿Date,Start,End,Division,Batch,Subject code,Subject,Session,Faculty,Room,"
				+ "Status,Changed by ticket");
		// OOP theory of CSE-A on Monday 9 to 10 in R101, taught by Dr Rao, as the base timetable has it.
		assertThat(lines).anySatisfy((line) -> assertThat(line).startsWith("2026-07-06,09:00,10:00,CSE-A,,OOP,")
			.contains(",THEORY,Dr Rao,R101,AS PUBLISHED,"));
		// Dates run in order.
		assertThat(lines.subList(1, lines.size())).isSortedAccordingTo(String::compareTo);
	}

	@Test
	@DisplayName("a cancelled lecture is marked in the report with the ticket that cancelled it")
	void aCancelledLectureIsMarked() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String ticket = raise(RAO_USER, cancel(oop, "Unwell"));
		applyOk(RAO_USER, ticket);
		Path file = this.folder.resolve("timetable.csv");

		asCaller(ADMIN, () -> this.timetableReport.exportLive(
				new LiveFilter(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 6), null, null, null), file));

		assertThat(Files.readAllLines(file)).anySatisfy((line) -> assertThat(line)
			.startsWith("2026-07-06,09:00,10:00,CSE-A,,OOP,").contains(",CANCELLED,").endsWith("," + ticket));
	}

	@Test
	@DisplayName("tickets are written newest first with what their change does, and awkward text is quoted")
	void ticketsAreWritten() throws Exception {
		String oop = lecture(this.theoryOop, "2026-07-06");
		String cancelled = raise(RAO_USER, cancel(oop, "Unwell, \\\"flu\\\""));
		applyOk(RAO_USER, cancelled);
		String moved = raise(RAO_USER, move(lecture(this.theoryOop, "2026-07-13"), "2026-07-16", 9, 10, this.room101));
		Path file = this.folder.resolve("tickets.csv");

		int written = asCaller(RAO_USER, () -> this.ticketReport.exportTickets(new TicketFilter(null, null, true), file));

		List<String> lines = Files.readAllLines(file);
		assertThat(written).isEqualTo(2);
		assertThat(lines.get(0)).isEqualTo("﻿Ticket,Type,Status,Approval,Revision,Raised at,Expires at,Conflicted,"
				+ "Operation,Lecture,Target date,Target start,Target end,Reason,Description");
		assertThat(lines.get(1)).startsWith(moved + ",RESCHEDULE,DRAFT,,1,").contains(",MOVE,").contains(",2026-07-16,09:00,10:00,");
		assertThat(lines.get(2)).startsWith(cancelled + ",CANCEL,APPLIED,SELF,1,").contains(",CANCEL,")
			.contains("\"Unwell, \"\"flu\"\"\"");
	}

	@Test
	@DisplayName("a person's report holds only the tickets they may see")
	void aReportHoldsOnlyWhatThePersonMaySee() throws Exception {
		raise(RAO_USER, cancel(lecture(this.theoryOop, "2026-07-06"), "Unwell"));
		Path file = this.folder.resolve("tickets.csv");

		int written = asCaller(ROY_USER, () -> this.ticketReport.exportTickets(new TicketFilter(null, null, false), file));

		assertThat(written).isZero();
		assertThat(Files.readAllLines(file)).hasSize(1);
	}

	@Test
	@DisplayName("a file that cannot be written is a ReportExportException with the IOException as its cause")
	void aFailureIsReported() {
		Path file = this.folder.resolve("no-such-folder").resolve("tickets.csv");

		assertThatThrownBy(() -> asCaller(ADMIN, () -> this.ticketReport.exportTickets(new TicketFilter(null, null, false), file)))
			.hasRootCauseInstanceOf(IOException.class)
			.hasCauseInstanceOf(ReportExportException.class);
	}

	/** Runs the work as the person, in the default tenant, as a request with their verified token would. */
	private <T> T asCaller(UUID person, Callable<T> work) {
		Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject(person.toString()).claim("email_verified", true).build();
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(new JwtAuthenticationToken(jwt));
		SecurityContextHolder.setContext(context);
		try {
			return TenantContext.callAs(TenantContext.DEFAULT_TENANT, work);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

}
