package com.kibootia.backend.access.domain;

import java.util.List;
import java.util.Set;

import com.kibootia.backend.platform.authorization.Caller;
import com.kibootia.backend.platform.authorization.Owned;
import com.kibootia.backend.platform.authorization.PolicyDecisionPoint;
import com.kibootia.backend.platform.authorization.ResourcePolicy;
import com.kibootia.backend.platform.authorization.Scope;
import com.kibootia.backend.platform.authorization.TenantBound;

/**
 * The Java policies behind the decision point (ADR 0027, ADR 0044).
 *
 * <p>The rules, in order:
 * <ol>
 * <li>The system may do anything. It is a caller created only by {@code Authorization#runAsSystem}.</li>
 * <li>An action must be a registered permission; anything else is a programming error.</li>
 * <li>Holding {@code <action>-any} gives {@link Scope#ALL}. Holding {@code <action>} gives {@link Scope#OWN} when the
 * action has an {@code -any} variant, and {@link Scope#ALL} when it has none (an administrative action). Holding
 * neither gives {@link Scope#NONE}.</li>
 * <li>For one record: the record's tenant must be the caller's; a module's {@link ResourcePolicy} decides if there is
 * one; otherwise {@code ALL} allows and {@code OWN} allows records the caller owns.</li>
 * </ol>
 */
public final class DefaultDecisionPoint implements PolicyDecisionPoint {

	private final PermissionRegistry registry;

	private final PermissionSource source;

	private final List<ResourcePolicy> policies;

	public DefaultDecisionPoint(PermissionRegistry registry, PermissionSource source, List<ResourcePolicy> policies) {
		this.registry = registry;
		this.source = source;
		this.policies = List.copyOf(policies);
	}

	@Override
	public Scope scopeFor(Caller caller, String action) {
		requireRegistered(action);
		if (caller.system()) {
			return Scope.ALL;
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

	private void requireRegistered(String action) {
		if (!this.registry.isRegistered(action)) {
			throw new IllegalStateException("'" + action + "' is not a declared permission");
		}
	}

}
