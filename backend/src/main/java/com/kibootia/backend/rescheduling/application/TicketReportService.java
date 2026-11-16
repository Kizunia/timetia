package com.kibootia.backend.rescheduling.application;

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
import com.kibootia.backend.rescheduling.application.TicketService.TicketFilter;
import com.kibootia.backend.timetable.LectureSlot;

/**
 * Writes tickets to a CSV file: one line for each ticket, newest first, with its state and what its first change does to
 * which lecture. The tickets are read through {@link TicketService}, so a report holds only the tickets the signed-in person
 * may see, exactly as the list does.
 */
@Service
public class TicketReportService {

	private static final int PAGE_SIZE = 200;

	private static final List<String> COLUMNS = List.of("Ticket", "Type", "Status", "Approval", "Revision", "Raised at",
			"Expires at", "Conflicted", "Operation", "Lecture", "Target date", "Target start", "Target end", "Reason",
			"Description");

	private final TicketService tickets;

	TicketReportService(TicketService tickets) {
		this.tickets = tickets;
	}

	/**
	 * Writes the report to the file, replacing it, and returns how many tickets it holds.
	 *
	 * @throws ReportExportException when the file cannot be written
	 */
	@Transactional(readOnly = true)
	@NoAuthorization("the ticket list it reads through decides which tickets the caller may see")
	public int exportTickets(TicketFilter filter, Path file) throws ReportExportException {
		try (CsvReport.Writer report = CsvReport.open(file, COLUMNS)) {
			Pageable pageable = PageRequest.of(0, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
			Page<TicketDetail> page;
			do {
				page = this.tickets.list(filter, pageable);
				for (TicketDetail detail : page) {
					report.write(row(detail));
				}
				pageable = page.nextPageable();
			}
			while (page.hasNext());
			return report.rows();
		}
	}

	private static List<String> row(TicketDetail detail) {
		Ticket ticket = detail.ticket();
		TicketChange change = detail.change();
		LectureSlot target = change.target();
		return Arrays.asList(ticket.id().toString(), ticket.type().name(), ticket.status().name(),
				(ticket.approvalPath() == null) ? null : ticket.approvalPath().name(),
				Integer.toString(ticket.revisionNo()), ticket.createdAt().toString(), ticket.expiresAt().toString(),
				Boolean.toString(ticket.conflicted()), change.operation().name(),
				(change.occurrenceId() == null) ? null : change.occurrenceId().toString(),
				(target == null) ? null : target.date().toString(),
				(target == null) ? null : target.startTime().toString(),
				(target == null) ? null : target.endTime().toString(), change.reason(), ticket.description());
	}

}
