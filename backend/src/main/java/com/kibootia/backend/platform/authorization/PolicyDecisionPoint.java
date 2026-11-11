package com.kibootia.backend.platform.authorization;

/**
 * The one place that answers authorization questions (ADR 0027, ADR 0044). The {@code access} module implements it with
 * Java policies; an external engine such as OPA can replace it, which is why it is a port. Modules do not call it
 * directly; they use {@link Authorization}.
 */
public interface PolicyDecisionPoint {

	/** May this caller perform the action on this record? */
	boolean can(Caller caller, String action, Object resource);

	/** May this caller perform the action at all, and on which records? */
	Scope scopeFor(Caller caller, String action);

}
