package com.kibootia.backend.access.domain;

import java.util.List;
import java.util.Set;

import com.kibootia.backend.platform.authorization.Caller;
import com.kibootia.backend.platform.authorization.GrantedScope;
import com.kibootia.backend.platform.authorization.Owned;
import com.kibootia.backend.platform.authorization.PolicyDecisionPoint;
import com.kibootia.backend.platform.authorization.Reach;
import com.kibootia.backend.platform.authorization.ResourcePolicy;
import com.kibootia.backend.platform.authorization.Scope;
import com.kibootia.backend.platform.authorization.ScopedGrants;
import com.kibootia.backend.platform.authorization.ScopedResourcePolicy;
import com.kibootia.backend.platform.authorization.TenantBound;

/**
 * The Java policies behind the decision point (ADR 0027, ADR 0044, Timetia ADR 0004).
 *
 * <p>The rules, in order:
 * <ol>
 * <li>The system may do anything. It is a caller created only by {@code Authorization#runAsSystem}.</li>
 * <li>An action must be a registered permission; anything else is a programming error.</li>
 * <li>A caller who has not verified their email address holds only the permissions marked
 * {@code allowedBeforeVerification}.</li>
 * <li>A <strong>policy-decided</strong> permission is never satisfied by the permission-only checks: they answer
 * {@link Scope#NONE}. For one record it needs the caller to hold the permission through some assignment, a
 * {@link ScopedResourcePolicy} that supports the record, and that policy's yes. With no such policy the answer is no.</li>
 * <li>For any other permission, holding {@code <action>-any} gives {@link Scope#ALL}. Holding {@code <action>} gives
 * {@link Scope#OWN} when the action has an {@code -any} variant, and {@link Scope#ALL} when it has none (an
 * administrative action). Holding neither gives {@link Scope#NONE}.</li>
 * <li>For one record: the record's tenant must be the caller's; a module's {@link ResourcePolicy} decides if there is
 * one; otherwise {@code ALL} allows and {@code OWN} allows records the caller owns.</li>
 * </ol>
 */
public final class DefaultDecisionPoint implements PolicyDecisionPoint {

	private final PermissionRegistry registry;

	private final PermissionSource source;

	private final ScopedGrants grants;

	private final List<ResourcePolicy> policies;

	private final List<ScopedResourcePolicy> scopedPolicies;

	public DefaultDecisionPoint(PermissionRegistry registry, PermissionSource source, List<ResourcePolicy> policies) {
		this(registry, source, ScopedGrants.NONE, policies, List.of());
	}

	public DefaultDecisionPoint(PermissionRegistry registry, PermissionSource source, ScopedGrants grants,
			List<ResourcePolicy> policies, List<ScopedResourcePolicy> scopedPolicies) {
		this.registry = registry;
		this.source = source;
		this.grants = grants;
		this.policies = List.copyOf(policies);
		this.scopedPolicies = List.copyOf(scopedPolicies);
	}

	@Override
	public Scope scopeFor(Caller caller, String action) {
		requireRegistered(action);
		if (caller.system()) {
			return Scope.ALL;
		}
		if (!caller.emailVerified() && !this.registry.isAllowedBeforeVerification(action)) {
			// ADR 0041: until the address is verified only the operations marked for it work.
			return Scope.NONE;
		}
		if (this.registry.isPolicyDecided(action)) {
			// Timetia ADR 0004: only a record check can satisfy it, never the permission alone.
			return Scope.NONE;
		}
		Set<String> held = this.source.permissionsOf(caller);
		if (held.contains(action + PermissionRegistry.ANY_SUFFIX)) {
			return Scope.ALL;
		}
		if (held.contains(action)) {
			return this.registry.hasAnyVariant(action) ? Scope.OWN : Scope.ALL;
		}
		return Scope.NONE;
	}

	@Override
	public boolean can(Caller caller, String action, Object resource) {
		if (this.registry.isPolicyDecided(action)) {
			return canPolicyDecided(caller, action, resource);
		}
		Scope scope = scopeFor(caller, action);
		if (scope == Scope.NONE) {
			return false;
		}
		if (caller.system()) {
			return true;
		}
		if (resource instanceof TenantBound bound && !caller.tenantId().equals(bound.tenantId())) {
			return false;
		}
		for (ResourcePolicy policy : this.policies) {
			if (policy.supports(resource)) {
				return policy.allows(caller, action, resource, scope);
			}
		}
		if (scope == Scope.ALL) {
			return true;
		}
		return resource instanceof Owned owned && caller.userId().equals(owned.ownerId());
	}

	@Override
	public Reach reachFor(Caller caller, String action) {
		requireRegistered(action);
		if (!this.registry.isPolicyDecided(action)) {
			throw new IllegalStateException("'" + action + "' is not policy-decided; use scopeFor for it");
		}
		if (caller.system()) {
			return Reach.everything();
		}
		if (!caller.emailVerified()) {
			return Reach.nothing();
		}
		return Reach.of(this.grants.grantsOf(caller, action));
	}

	private boolean canPolicyDecided(Caller caller, String action, Object resource) {
		requireRegistered(action);
		if (caller.system()) {
			return true;
		}
		if (!caller.emailVerified()) {
			return false;
		}
		if (resource instanceof TenantBound bound && !caller.tenantId().equals(bound.tenantId())) {
			return false;
		}
		List<GrantedScope> held = this.grants.grantsOf(caller, action);
		if (held.isEmpty()) {
			return false;
		}
		for (ScopedResourcePolicy policy : this.scopedPolicies) {
			if (policy.supports(resource)) {
				return policy.allows(caller, action, resource, held);
			}
		}
		return false;
	}

	private void requireRegistered(String action) {
		if (!this.registry.isRegistered(action)) {
			throw new IllegalStateException("'" + action + "' is not a declared permission");
		}
	}

}
