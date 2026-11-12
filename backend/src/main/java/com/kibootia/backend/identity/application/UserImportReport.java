package com.kibootia.backend.identity.application;

import java.util.List;
import java.util.UUID;

/**
 * What an import did, or for a dry run what it would do, line by line in the order of the file (Timetia ADR 0006). It is
 * handed back to the administrator who uploaded the file and is never logged or audited: it contains the addresses.
 *
 * @param dryRun true when nothing was written
 * @param importId names the run in the audit log (a real run writes one entry with this ID); null for a dry run
 */
public record UserImportReport(boolean dryRun, UUID importId, Counts counts, List<Line> lines) {

	/**
	 * @param rows the data rows in the file
	 * @param accountsCreated people who had no account and got one (or would, in a dry run)
	 * @param added assignments added
	 * @param alreadyPresent assignments the person already held, which is what a repeated import sees
	 * @param errors rows that cannot be used, each with a code
	 * @param skipped rows left alone on purpose: a repeat of an earlier row, or the rest of a person who has an error
	 * @param notProcessed rows not reached because the import ran out of time or the identity provider was unreachable
	 */
	public record Counts(int rows, int accountsCreated, int added, int alreadyPresent, int errors, int skipped,
			int notProcessed) {

	}

	/**
	 * One row of the file. {@code email}, {@code role} and {@code scope} are as written (the email is the normalised form
	 * when it could be read). {@code userId} is the person's ID when there is one; {@code accountCreated} says this row's
	 * person got their account from this import.
	 */
	public record Line(int line, String email, String role, String scope, Outcome outcome, String code, String message,
			UUID userId, boolean accountCreated) {

	}

	/** What happened to a row. */
	public enum Outcome {

		/** The assignment was added (or would be). */
		ADDED,

		/** The person already held it, so nothing changed. */
		ALREADY_PRESENT,

		/** The row cannot be used; {@code code} and {@code message} say why. */
		ERROR,

		/** Left alone on purpose; {@code code} says why. */
		SKIPPED,

		/** Not reached. Run the same file again to finish it. */
		NOT_PROCESSED

	}

}
