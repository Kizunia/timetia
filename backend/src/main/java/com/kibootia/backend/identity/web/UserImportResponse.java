package com.kibootia.backend.identity.web;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.kibootia.backend.identity.application.UserImportReport;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * What an import did, or for a dry run what it would do, row by row in the order of the file. It holds the addresses
 * from the file, so it goes only to the administrator who uploaded it.
 */
public record UserImportResponse(
		@Schema(requiredMode = REQUIRED, description = "True when nothing was written.") boolean dryRun,
		@Schema(nullable = true, description = "Names the run in the audit log; null for a dry run.") UUID importId,
		@Schema(requiredMode = REQUIRED) Counts counts,
		@Schema(requiredMode = REQUIRED) List<Line> lines) {

	/** Totals over the rows. */
	public record Counts(
			@Schema(requiredMode = REQUIRED) int rows,
			@Schema(requiredMode = REQUIRED, description = "People who got an account from this import (or would).") int accountsCreated,
			@Schema(requiredMode = REQUIRED) int added,
			@Schema(requiredMode = REQUIRED, description = "Assignments the person already held.") int alreadyPresent,
			@Schema(requiredMode = REQUIRED) int errors,
			@Schema(requiredMode = REQUIRED) int skipped,
			@Schema(requiredMode = REQUIRED, description = "Rows not reached; run the same file again.") int notProcessed) {

	}

	/** One row of the file. */
	public record Line(
			@Schema(requiredMode = REQUIRED, description = "The line in the file where the row starts; the header is line 1.") int line,
			@Schema(requiredMode = REQUIRED) String email,
			@Schema(requiredMode = REQUIRED) String role,
			@Schema(requiredMode = REQUIRED) String scope,
			@Schema(requiredMode = REQUIRED, allowableValues = { "ADDED", "ALREADY_PRESENT", "ERROR", "SKIPPED",
					"NOT_PROCESSED" }) String outcome,
			@Schema(nullable = true, description = "Why the row has the outcome, for ERROR, SKIPPED and NOT_PROCESSED.") String code,
			@Schema(nullable = true) String message,
			@Schema(nullable = true) UUID userId,
			@Schema(requiredMode = REQUIRED, description = "True when this row's person got their account from this import.") boolean accountCreated) {

	}

	static UserImportResponse of(UserImportReport report) {
		UserImportReport.Counts counts = report.counts();
		return new UserImportResponse(report.dryRun(), report.importId(),
				new Counts(counts.rows(), counts.accountsCreated(), counts.added(), counts.alreadyPresent(), counts.errors(),
						counts.skipped(), counts.notProcessed()),
				report.lines()
					.stream()
					.map((line) -> new Line(line.line(), line.email(), line.role(), line.scope(), line.outcome().name(),
							line.code(), line.message(), line.userId(), line.accountCreated()))
					.toList());
	}

}
