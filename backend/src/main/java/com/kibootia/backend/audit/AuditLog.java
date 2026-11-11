package com.kibootia.backend.audit;

/**
 * The public API of the audit log (ADR 0046). Any module calls it from the use case that makes an auditable change.
 *
 * <p>The entry is written in <strong>the caller's transaction</strong>: a change cannot exist without its entry, and a
 * rolled-back change leaves no entry. Calling it outside a transaction is an error, not a silent separate write.
 */
public interface AuditLog {

	/**
	 * Records the entry for the current caller, tenant and trace. The actor is whoever is acting (the nil UUID for the
	 * system); it cannot be passed in, so a module cannot record an action on behalf of someone else.
	 */
	void record(AuditEntry entry);

}
