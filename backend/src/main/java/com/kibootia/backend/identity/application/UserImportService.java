package com.kibootia.backend.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.kibootia.backend.access.AccessPermissions;
import com.kibootia.backend.access.RoleAssignments;
import com.kibootia.backend.access.ScopedAssignments;
import com.kibootia.backend.audit.AuditEntry;
import com.kibootia.backend.audit.AuditLog;
import com.kibootia.backend.identity.IdentityPermissions;
import com.kibootia.backend.identity.application.UserImportReport.Counts;
import com.kibootia.backend.identity.application.UserImportReport.Line;
import com.kibootia.backend.identity.application.UserImportReport.Outcome;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.authorization.RequiresPermission;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.ratelimit.RateLimitCategory;
import com.kibootia.backend.platform.ratelimit.RateLimiter;
import com.kibootia.backend.platform.tenancy.TenantContext;

/**
 * Loads people and their scoped roles from a file (Timetia ADR 0006, work package W3.1).
 *
 * <p>What it guarantees, each backed by a test:
 * <ul>
 * <li><b>Administrators only.</b> The caller must be the built-in Administrator (Super Admin), whatever other
 * permissions they hold. The same rule as assigning a scoped role through the screen.</li>
 * <li><b>Only roles that apply within a scope.</b> A file cannot name the Administrator role, the user role or a custom
 * platform role, so an import can never make anyone an administrator. Every row goes through the same checks as the
 * screen ({@link ScopedAssignments}).</li>
 * <li><b>Add only.</b> It creates missing accounts and adds missing assignments. It never removes or changes anything,
 * and it never touches a disabled or deleted person. Running the same file again changes nothing.</li>
 * <li><b>A person's rows go together or not at all.</b> If any row of a person has an error, none of theirs is applied,
 * so no account is created from half a mistake. The other people are not affected.</li>
 * <li><b>No half-made people.</b> A new person's account, profile, roles and audit entries are one transaction; if it
 * fails the identity provider's account is removed again.</li>
 * <li><b>A dry run writes nothing</b> and does not contact the identity provider. It shows what the same file would do,
 * except that it cannot know an address the identity provider already has without a profile here.</li>
 * <li><b>Bounded.</b> A size and row limit, a time budget, a stop after repeated identity provider failures, a rate
 * limit, and one real import at a time.</li>
 * </ul>
 *
 * <p>The report goes back to the administrator and nowhere else: it holds the addresses, which are never logged or put in
 * the audit log. The audit log gets one summary entry per real run, with counts only, plus the entries the invitation
 * and each added assignment write.
 */
@Service
public class UserImportService {

	public static final String IMPORT_RUNNING = "identity.import_running";

	private static final Logger log = LoggerFactory.getLogger(UserImportService.class);

	private final UserProfileStore profiles;

	private final UserAdministration users;

	private final ScopedAssignments scopedAssignments;

	private final RoleAssignments roleAssignments;

	private final Authorization authorization;

	private final AuditLog auditLog;

	private final RateLimiter rateLimiter;

	private final UserImportProperties properties;

	private final LockProvider locks;

	private final Clock clock;

	private final TransactionTemplate transactions;

	UserImportService(UserProfileStore profiles, UserAdministration users, ScopedAssignments scopedAssignments,
			RoleAssignments roleAssignments, Authorization authorization, AuditLog auditLog, RateLimiter rateLimiter,
			UserImportProperties properties, LockProvider locks, Clock clock,
			PlatformTransactionManager transactionManager) {
		this.profiles = profiles;
		this.users = users;
		this.scopedAssignments = scopedAssignments;
		this.roleAssignments = roleAssignments;
		this.authorization = authorization;
		this.auditLog = auditLog;
		this.rateLimiter = rateLimiter;
		this.properties = properties;
		this.locks = locks;
		this.clock = clock;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	/**
	 * Runs the import. Not one transaction: each person is their own, so a failure affects only that person and what was
	 * done before it stays.
	 */
	@RequiresPermission(IdentityPermissions.INVITE_USERS)
	public UserImportReport run(byte[] file, boolean dryRun) {
		this.authorization.require(AccessPermissions.ASSIGN_ROLES);
		if (!this.roleAssignments.holdsAdministratorRole(this.authorization.currentUserId())) {
			throw new AccessDeniedException("Only an administrator can import users");
		}
		if (file.length > this.properties.maxBytes()) {
			throw ApiException.payloadTooLarge("identity.import_too_large",
					"The file is larger than " + (this.properties.maxBytes() / 1024) + " KB. Split it into smaller files.");
		}
		// A dry run reads and writes nothing, so it only counts against the general limit for administration. A real run
		// can create many accounts and send as many emails, so it has its own.
		this.rateLimiter.acquire(dryRun ? RateLimitCategory.ADMIN_ACTIONS : RateLimitCategory.IMPORTS);
		List<ImportCsv.Row> rows = ImportCsv.parse(file, this.properties.maxRows());
		return dryRun ? process(rows, true) : exclusively(() -> process(rows, false));
	}

	// ---------------------------------------------------------------------------------------------------------------

	private UserImportReport process(List<ImportCsv.Row> rows, boolean dryRun) {
		Instant deadline = this.clock.instant().plus(this.properties.timeBudget());
		List<Entry> entries = rows.stream().map(Entry::new).toList();
		resolve(entries);

		Map<String, List<Entry>> people = new LinkedHashMap<>();
		for (Entry entry : entries) {
			if (entry.email != null) {
				people.computeIfAbsent(entry.email, (key) -> new ArrayList<>()).add(entry);
			}
		}
		// One person with a mistake is left out entirely; the others carry on.
		people.values().forEach(UserImportService::skipPeopleWithErrors);

		Map<String, List<UserProfile>> existing = this.profiles.findByEmails(people.entrySet()
			.stream()
			.filter((person) -> person.getValue().stream().anyMatch((entry) -> entry.outcome == null))
			.map(Map.Entry::getKey)
			.toList());

		Set<String> created = new HashSet<>();
		int unavailableInARow = 0;
		for (Map.Entry<String, List<Entry>> person : people.entrySet()) {
			List<Entry> pending = person.getValue().stream().filter((entry) -> entry.outcome == null).toList();
			if (pending.isEmpty()) {
				continue;
			}
			if (!this.clock.instant().isBefore(deadline)) {
				notProcessed(pending, "time_budget_exceeded", "The import ran out of time before this person. Run the same file again.");
				continue;
			}
			if (unavailableInARow >= this.properties.maxConsecutiveUnavailable()) {
				notProcessed(pending, "identity_unavailable",
						"The identity provider could not be reached, so the rest were not processed. Run the same file again later.");
				continue;
			}
			boolean unavailable = applyPerson(person.getKey(), pending, existing.getOrDefault(person.getKey(), List.of()),
					dryRun, created);
			unavailableInARow = unavailable ? unavailableInARow + 1 : 0;
		}

		UserImportReport report = report(dryRun, entries, created.size());
		if (!dryRun) {
			record(report);
		}
		return report;
	}

	/** Turns each usable row's role and place names into a role and a place, with the same checks as the screen. */
	private void resolve(List<Entry> entries) {
		Map<String, ScopedAssignments.Resolution> cache = new HashMap<>();
		Set<String> seen = new HashSet<>();
		for (Entry entry : entries) {
			ImportCsv.Row row = entry.row;
			if (row.hasProblem()) {
				entry.error(row.problemCode(), row.problemMessage());
				if (!row.email().isEmpty()) {
					entry.email = EmailAddresses.normalize(row.email()).orElse(null);
				}
				continue;
			}
			Optional<String> email = EmailAddresses.normalize(row.email());
			if (email.isEmpty()) {
				entry.error("invalid_email", "The email address is not valid");
				continue;
			}
			entry.email = email.get();
			ScopedAssignments.Resolution resolution = cache.computeIfAbsent(row.role() + '\u0000' + row.scope(),
					(key) -> this.scopedAssignments.resolve(row.role(), row.scope()));
			if (!resolution.isResolved()) {
				entry.error(resolution.failure().name().toLowerCase(java.util.Locale.ROOT), resolution.message());
				continue;
			}
			entry.resolution = resolution;
			if (!seen.add(entry.email + '\u0000' + resolution.roleId() + '\u0000' + resolution.scopeId())) {
				entry.outcome = Outcome.SKIPPED;
				entry.code = "duplicate_in_file";
				entry.message = "The same role in the same place is already on an earlier line for this person";
			}
		}
	}

	private static void skipPeopleWithErrors(List<Entry> person) {
		if (person.stream().noneMatch((entry) -> entry.outcome == Outcome.ERROR)) {
			return;
		}
		for (Entry entry : person) {
			if (entry.outcome == null) {
				entry.outcome = Outcome.SKIPPED;
				entry.code = "person_has_errors";
				entry.message = "Another row for this person has an error, so none of their rows were applied";
			}
		}
	}

	/** Applies one person's rows. Returns whether it failed because the identity provider could not be reached. */
	private boolean applyPerson(String email, List<Entry> pending, List<UserProfile> existing, boolean dryRun,
			Set<String> created) {
		if (existing.size() > 1) {
			pending.forEach((entry) -> entry.error("email_ambiguous",
					"More than one account has this address. Resolve it before importing"));
			return false;
		}
		try {
			if (existing.isEmpty()) {
				createAndAssign(email, pending, dryRun);
				created.add(email);
			}
			else {
				UserProfile profile = existing.get(0);
				if (profile.deleted()) {
					pending.forEach((entry) -> entry.error("account_deleted",
							"This person's account is deleted. Restore it first; an import does not"));
				}
				else if (profile.status() == UserProfile.Status.DISABLED) {
					pending.forEach((entry) -> entry.error("account_disabled",
							"This person's account is disabled. Enable it first; an import does not"));
				}
				else {
					assignToExisting(profile.id(), pending, dryRun);
				}
			}
			return false;
		}
		catch (ApiException ex) {
			if (UserAdministration.EMAIL_IN_USE.equals(ex.code())) {
				pending.forEach((entry) -> entry.error("account_without_profile",
						"The identity provider already has an account for this address that this application does not know. See the runbook"));
				return false;
			}
			boolean unavailable = ex.status() == HttpStatus.SERVICE_UNAVAILABLE;
			pending.forEach((entry) -> entry.error(unavailable ? "identity_unavailable" : codeOf(ex), ex.getMessage()));
			return unavailable;
		}
		catch (RuntimeException ex) {
			// Only the type: the message of a database error can hold the values that failed.
			log.error("An import row failed unexpectedly ({})", ex.getClass().getSimpleName());
			pending.forEach((entry) -> entry.error("internal_error", "Something went wrong; nothing was changed for this person"));
			return false;
		}
	}

	private void createAndAssign(String email, List<Entry> pending, boolean dryRun) {
		if (dryRun) {
			pending.forEach((entry) -> entry.done(Outcome.ADDED, null, true));
			return;
		}
		List<Boolean> added = new ArrayList<>();
		UUID id = this.users.inviteForImport(email, (newUser) -> {
			for (Entry entry : pending) {
				added.add(this.scopedAssignments.addIfMissing(newUser, entry.resolution));
			}
		});
		for (int i = 0; i < pending.size(); i++) {
			pending.get(i).done(added.get(i) ? Outcome.ADDED : Outcome.ALREADY_PRESENT, id, true);
		}
	}

	private void assignToExisting(UUID id, List<Entry> pending, boolean dryRun) {
		List<Boolean> added = new ArrayList<>();
		this.transactions.executeWithoutResult((status) -> {
			for (Entry entry : pending) {
				added.add(this.scopedAssignments.addIfMissing(id, entry.resolution));
			}
			if (dryRun) {
				// The same code a real run uses, so the answer is exact; it is simply never committed.
				status.setRollbackOnly();
			}
		});
		for (int i = 0; i < pending.size(); i++) {
			pending.get(i).done(added.get(i) ? Outcome.ADDED : Outcome.ALREADY_PRESENT, id, false);
		}
	}

	private static void notProcessed(List<Entry> pending, String code, String message) {
		for (Entry entry : pending) {
			entry.outcome = Outcome.NOT_PROCESSED;
			entry.code = code;
			entry.message = message;
		}
	}

	private static String codeOf(ApiException ex) {
		// The last part of "module.reason", the same way the rest of the report names things.
		String code = ex.code();
		return code.substring(code.lastIndexOf('.') + 1);
	}

	// ---------------------------------------------------------------------------------------------------------------

	private UserImportReport report(boolean dryRun, List<Entry> entries, int accountsCreated) {
		int added = 0;
		int present = 0;
		int errors = 0;
		int skipped = 0;
		int notProcessed = 0;
		List<Line> lines = new ArrayList<>();
		for (Entry entry : entries) {
			switch (entry.outcome) {
				case ADDED -> added++;
				case ALREADY_PRESENT -> present++;
				case ERROR -> errors++;
				case SKIPPED -> skipped++;
				case NOT_PROCESSED -> notProcessed++;
			}
			ImportCsv.Row row = entry.row;
			lines.add(new Line(row.line(), (entry.email != null) ? entry.email : row.email(), row.role(), row.scope(),
					entry.outcome, entry.code, entry.message, entry.userId, entry.accountCreated));
		}
		return new UserImportReport(dryRun, dryRun ? null : UUID.randomUUID(),
				new Counts(entries.size(), accountsCreated, added, present, errors, skipped, notProcessed), lines);
	}

	/** One audit entry for the run, with counts only. The people and assignments have their own entries. */
	private void record(UserImportReport report) {
		Counts counts = report.counts();
		this.transactions.executeWithoutResult((status) -> this.auditLog.record(
				AuditEntry.of("identity.user-import.completed", "user-import", report.importId())
					.withChange("rows", String.valueOf(counts.rows()))
					.withChange("accountsCreated", String.valueOf(counts.accountsCreated()))
					.withChange("added", String.valueOf(counts.added()))
					.withChange("alreadyPresent", String.valueOf(counts.alreadyPresent()))
					.withChange("errors", String.valueOf(counts.errors()))
					.withChange("skipped", String.valueOf(counts.skipped()))
					.withChange("notProcessed", String.valueOf(counts.notProcessed()))));
	}

	/** One real import at a time per tenant, across application instances. */
	private <T> T exclusively(Supplier<T> work) {
		Duration holdAtMost = this.properties.timeBudget().plusMinutes(2);
		LockConfiguration lock = new LockConfiguration(this.clock.instant(), "identity-user-import-" + TenantContext.require(),
				holdAtMost, Duration.ZERO);
		Optional<SimpleLock> held = this.locks.lock(lock);
		if (held.isEmpty()) {
			throw ApiException.conflict(IMPORT_RUNNING, "Another import is running. Wait for it to finish.");
		}
		try {
			return work.get();
		}
		finally {
			held.get().unlock();
		}
	}

	/** A row while it is being processed. */
	private static final class Entry {

		final ImportCsv.Row row;

		String email;

		ScopedAssignments.Resolution resolution;

		Outcome outcome;

		String code;

		String message;

		UUID userId;

		boolean accountCreated;

		Entry(ImportCsv.Row row) {
			this.row = row;
		}

		void error(String code, String message) {
			this.outcome = Outcome.ERROR;
			this.code = code;
			this.message = message;
			this.userId = null;
			this.accountCreated = false;
		}

		void done(Outcome outcome, UUID userId, boolean accountCreated) {
			this.outcome = outcome;
			this.userId = userId;
			this.accountCreated = accountCreated;
		}

	}

}
