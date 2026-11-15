package com.kibootia.backend.rescheduling.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.kibootia.backend.audit.AuditEntry;
import com.kibootia.backend.audit.AuditLog;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.tenancy.TenantContext;

/**
 * Expires the tickets whose lecture has started without them being applied (ticket lifecycle): a draft or a ticket under
 * review lapses when the earliest lecture it involves starts, and an expired ticket stays in the history. A ticket also
 * expires when someone tries to act on it after that time, so this job is what makes a ticket nobody touches show as expired.
 * {@link TicketExpirySchedule} runs it under a database lock.
 *
 * <p>Each ticket is expired in a transaction of its own and a failure of one is logged without stopping the others. A ticket
 * that was changed by its author in the meantime is left for the next run, because the update is refused when it is not the
 * version that was read.
 */
@Component
public class TicketExpiryJob {

	private static final Logger log = LoggerFactory.getLogger(TicketExpiryJob.class);

	/** How many tickets one run reads at a time; a run goes on until none are left. */
	private static final int BATCH = 200;

	private final TicketStore tickets;

	private final Authorization authorization;

	private final AuditLog auditLog;

	private final Clock clock;

	private final TransactionTemplate transactions;

	TicketExpiryJob(TicketStore tickets, Authorization authorization, AuditLog auditLog, Clock clock,
			PlatformTransactionManager transactionManager) {
		this.tickets = tickets;
		this.authorization = authorization;
		this.auditLog = auditLog;
		this.clock = clock;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	/** Expires every ticket that is due now and returns how many. Used by the schedule and by tests. */
	public int runOnce() {
		Integer expired = TenantContext.callAs(TenantContext.DEFAULT_TENANT,
				() -> this.authorization.runAsSystem("expire tickets whose lecture has started", () -> expireDue()));
		return (expired != null) ? expired : 0;
	}

	private Integer expireDue() {
		int expired = 0;
		Instant now = this.clock.instant();
		List<Ticket> due = this.transactions.execute((status) -> this.tickets.openExpiredBy(now, BATCH));
		while (due != null && !due.isEmpty()) {
			int progress = 0;
			for (Ticket ticket : due) {
				if (expireInOwnTransaction(ticket, now)) {
					progress++;
				}
			}
			expired += progress;
			if (progress == 0) {
				break;
			}
			due = this.transactions.execute((status) -> this.tickets.openExpiredBy(now, BATCH));
		}
		if (expired > 0) {
			log.info("Expired {} tickets whose lecture had started", expired);
		}
		return expired;
	}

	private boolean expireInOwnTransaction(Ticket ticket, Instant now) {
		try {
			this.transactions.executeWithoutResult((status) -> {
				Ticket expired = this.tickets.update(ticket.expired(now));
				this.auditLog.record(AuditEntry.of("rescheduling.ticket.expired", "ticket", ticket.id())
					.withChange("from", ticket.status().name())
					.withChange("to", expired.status().name()));
			});
			return true;
		}
		catch (RuntimeException ex) {
			// Most likely the author acted on it first. It is picked up by the next run if it is still due.
			log.warn("Ticket {} was not expired this time ({})", ticket.id(), ex.getClass().getSimpleName());
			return false;
		}
	}

}
