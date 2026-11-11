package com.kibootia.backend.platform.authorization;

import java.util.UUID;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.kibootia.backend.platform.tenancy.TenantContext;

/**
 * The entry point for authorization checks inside use cases (ADR 0044). A permission-only check is declared with
 * {@link RequiresPermission}; a check that needs the record calls {@link #require(String, Object)} after loading it; a
 * list asks {@link #scopeFor(String)} and turns the answer into a query condition. Every denial is an {@link
 * AccessDeniedException}, which the web layer answers with 403.
 */
@Component
public class Authorization {

	private static final Logger log = LoggerFactory.getLogger(Authorization.class);

	private static final ThreadLocal<Caller> SYSTEM = new ThreadLocal<>();

	private final PolicyDecisionPoint decisionPoint;

	/** With no decision point (the {@code access} module is absent) everything is denied. */
	Authorization(ObjectProvider<PolicyDecisionPoint> decisionPoint) {
		this.decisionPoint = decisionPoint.getIfAvailable(Authorization::denyAll);
	}

	private static PolicyDecisionPoint denyAll() {
		return new PolicyDecisionPoint() {
			@Override
			public boolean can(Caller caller, String action, Object resource) {
				return false;
			}

			@Override
			public Scope scopeFor(Caller caller, String action) {
				return Scope.NONE;
			}
		};
	}

	/** Whether the current caller holds the permission, for itself or for every record. */
	public boolean can(String action) {
		return this.decisionPoint.scopeFor(caller(), action) != Scope.NONE;
	}

	/** Whether the current caller may perform the action on this record. */
	public boolean can(String action, Object resource) {
		return this.decisionPoint.can(caller(), action, resource);
	}

	public void require(String action) {
		if (!can(action)) {
			throw denied(action);
		}
	}

	public void require(String action, Object resource) {
		if (!can(action, resource)) {
			throw denied(action);
		}
	}

	/**
	 * For an operation on the caller's own account that needs no permission but is not open to someone who has not
	 * verified their email address (ADR 0041).
	 */
	public void requireVerified() {
		if (!caller().emailVerified()) {
			throw new EmailNotVerifiedException();
		}
	}

	public Scope scopeFor(String action) {
		return this.decisionPoint.scopeFor(caller(), action);
	}

	/** Like {@link #scopeFor} but denies when the caller may not perform the action on any record. */
	public Scope requireScope(String action) {
		Scope scope = scopeFor(action);
		if (scope == Scope.NONE) {
			throw denied(action);
		}
		return scope;
	}

	/**
	 * How far the current caller's policy-decided permission reaches, for a list (Timetia ADR 0004): everywhere, or the
	 * places where they hold it. Deny-by-default: a caller who holds it nowhere gets an empty reach, and the module should
	 * then return nothing. Only for permissions declared policy-decided; the others use {@link #scopeFor}.
	 */
	public Reach reach(String action) {
		return this.decisionPoint.reachFor(caller(), action);
	}

	/** Whether the signed-in user's email address is verified, as the token says (ADR 0041). */
	public boolean isEmailVerified() {
		return caller().emailVerified();
	}

	/** The id of the signed-in user, for ownership conditions in queries. */
	public UUID currentUserId() {
		return caller().userId();
	}

	/**
	 * Runs work that no user started (a job, an event handler) with every permission. The reason is required and
	 * logged, so the use of system authority is visible and reviewable. The tenant must already be bound.
	 */
	public <T> T runAsSystem(String reason, Callable<T> work) {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("A reason is required to act as the system");
		}
		Caller previous = SYSTEM.get();
		SYSTEM.set(Caller.system(TenantContext.require()));
		log.debug("Acting as the system: {}", reason);
		try {
			return work.call();
		}
		catch (RuntimeException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
		finally {
			if (previous == null) {
				SYSTEM.remove();
			}
			else {
				SYSTEM.set(previous);
			}
		}
	}

	public void runAsSystem(String reason, Runnable work) {
		runAsSystem(reason, () -> {
			work.run();
			return null;
		});
	}

	Caller caller() {
		Caller system = SYSTEM.get();
		if (system != null) {
			return system;
		}
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof Jwt token)) {
			throw new AccessDeniedException("There is no signed-in user");
		}
		UUID tenant = TenantContext.current().orElseThrow(() -> new AccessDeniedException("There is no tenant"));
		try {
			return Caller.user(UUID.fromString(token.getSubject()), tenant,
					Boolean.TRUE.equals(token.getClaimAsBoolean("email_verified")));
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			throw new AccessDeniedException("The token does not identify a user");
		}
	}

	/**
	 * The refusal for an action. A caller who has not verified their email address is told that, because verifying is
	 * what they can do about it; everyone else gets a plain denial.
	 */
	AccessDeniedException denied(String action) {
		return caller().emailVerified() ? new PermissionDeniedException(action) : new EmailNotVerifiedException();
	}

}
