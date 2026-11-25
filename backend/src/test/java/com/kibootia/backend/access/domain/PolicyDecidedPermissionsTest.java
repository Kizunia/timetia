package com.kibootia.backend.access.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.kibootia.backend.platform.authorization.Caller;
import com.kibootia.backend.platform.authorization.GrantedScope;
import com.kibootia.backend.platform.authorization.Permission;
import com.kibootia.backend.platform.authorization.Reach;
import com.kibootia.backend.platform.authorization.ResourcePolicy;
import com.kibootia.backend.platform.authorization.Scope;
import com.kibootia.backend.platform.authorization.ScopedGrants;
import com.kibootia.backend.platform.authorization.ScopedResourcePolicy;
import com.kibootia.backend.platform.authorization.TenantBound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Policy-decided permissions (Timetia ADR 0004): the flat checks never satisfy them, a record check needs the permission
 * through some assignment and a policy that supports the record, and a policy can narrow but never grant.
 */
class PolicyDecidedPermissionsTest {

	static final UUID TENANT = UUID.fromString("00000000-0000-7000-8000-000000000001");

	static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-7000-8000-000000000002");

	static final UUID ALICE = UUID.fromString("11111111-1111-7111-8111-111111111111");

	static final UUID DEPARTMENT = UUID.fromString("dddddddd-dddd-7ddd-8ddd-dddddddddddd");

	static final String REVIEW = "reviews.tickets.review";

	static final String UPDATE = "widgets.widgets.update";

	static final PermissionRegistry REGISTRY = new PermissionRegistry(List.of(
			Permission.policyDecided(REVIEW, "Review tickets"), Permission.forEveryUser(UPDATE, "Change own widgets"),
			Permission.restricted("widgets.widgets.update-any", "Change anyone's widgets")));

	record Ticket(UUID tenantId) implements TenantBound {

	}

	/** A policy that supports tickets, records what it was asked, and answers as told. */
	static final class RecordingPolicy implements ScopedResourcePolicy {

		final List<List<GrantedScope>> seen = new ArrayList<>();

		boolean answer = true;

		@Override
		public boolean supports(Object resource) {
			return resource instanceof Ticket;
		}

		@Override
		public boolean allows(Caller caller, String action, Object resource, List<GrantedScope> grants) {
			this.seen.add(grants);
			return this.answer;
		}

	}

	static Caller alice() {
		return Caller.user(ALICE, TENANT, true);
	}

	static DefaultDecisionPoint point(List<GrantedScope> grants, Set<String> flat, ScopedResourcePolicy... policies) {
		ScopedGrants held = (caller, action) -> action.equals(REVIEW) ? grants : List.of();
		return new DefaultDecisionPoint(REGISTRY, (caller) -> flat, held, List.of(), List.of(policies));
	}

	@Nested
	@DisplayName("the declaration")
	class Declaration {

		@Test
		@DisplayName("is known to the registry, and the other permissions are not policy-decided")
		void registry() {
			assertThat(REGISTRY.isPolicyDecided(REVIEW)).isTrue();
			assertThat(REGISTRY.isPolicyDecided(UPDATE)).isFalse();
			assertThat(REGISTRY.isPolicyDecided("nobody.declared.this")).isFalse();
		}

		@Test
		@DisplayName("cannot be given to every user or used before the email address is verified")
		void cannotBeForEveryUserOrBeforeVerification() {
			assertThatThrownBy(() -> new Permission("reviews.tickets.review", "x", true, false, true))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new Permission("reviews.tickets.review", "x", false, true, true))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> Permission.policyDecided(REVIEW, "x").beforeVerification())
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("cannot end in -any, because its reach comes from the scope of the assignment")
		void cannotBeAny() {
			assertThatThrownBy(() -> new PermissionRegistry(List.of(Permission.restricted("reviews.tickets.review", "x"),
					Permission.policyDecided("reviews.tickets.review-any", "y"))))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("-any");
		}

	}

	@Nested
	@DisplayName("the permission-only checks")
	class FlatChecks {

		@Test
		@DisplayName("never satisfy it, even if the name is among the flat permissions the caller holds")
		void neverSatisfied() {
			DefaultDecisionPoint point = point(List.of(GrantedScope.global()), Set.of(REVIEW, UPDATE));

			assertThat(point.scopeFor(alice(), REVIEW)).isEqualTo(Scope.NONE);
		}

		@Test
		@DisplayName("still answer ALL for the system")
		void system() {
			Caller system = new Caller(UUID.randomUUID(), TENANT, true, true);

			assertThat(point(List.of(), Set.of()).scopeFor(system, REVIEW)).isEqualTo(Scope.ALL);
		}

		@Test
		@DisplayName("are unchanged for an ordinary permission")
		void ordinaryPermissionIsUnchanged() {
			assertThat(point(List.of(), Set.of(UPDATE)).scopeFor(alice(), UPDATE)).isEqualTo(Scope.OWN);
		}

	}

	@Nested
	@DisplayName("a decision about one record")
	class Records {

		@Test
		@DisplayName("is no without the permission anywhere, and the policy is not asked")
		void noGrants() {
			RecordingPolicy policy = new RecordingPolicy();

			assertThat(point(List.of(), Set.of(), policy).can(alice(), REVIEW, new Ticket(TENANT))).isFalse();
			assertThat(policy.seen).isEmpty();
		}

		@Test
		@DisplayName("is no when no policy supports the record, even for a global grant")
		void noPolicy() {
			assertThat(point(List.of(GrantedScope.global()), Set.of()).can(alice(), REVIEW, new Ticket(TENANT))).isFalse();
			RecordingPolicy policy = new RecordingPolicy();
			assertThat(point(List.of(GrantedScope.global()), Set.of(), policy).can(alice(), REVIEW, "not a ticket")).isFalse();
			assertThat(policy.seen).isEmpty();
		}

		@Test
		@DisplayName("is what the policy answers, and the policy is told where the caller holds the permission")
		void policyDecides() {
			RecordingPolicy policy = new RecordingPolicy();
			List<GrantedScope> grants = List.of(GrantedScope.of("DEPARTMENT", DEPARTMENT));
			DefaultDecisionPoint point = point(grants, Set.of(), policy);

			assertThat(point.can(alice(), REVIEW, new Ticket(TENANT))).isTrue();
			policy.answer = false;
			assertThat(point.can(alice(), REVIEW, new Ticket(TENANT))).isFalse();
			assertThat(policy.seen).containsExactly(grants, grants);
		}

		@Test
		@DisplayName("is no for a record of another tenant, and the policy is not asked")
		void otherTenant() {
			RecordingPolicy policy = new RecordingPolicy();

			assertThat(point(List.of(GrantedScope.global()), Set.of(), policy).can(alice(), REVIEW, new Ticket(OTHER_TENANT)))
				.isFalse();
			assertThat(policy.seen).isEmpty();
		}

		@Test
		@DisplayName("is no for a caller who has not verified their email address")
		void unverified() {
			RecordingPolicy policy = new RecordingPolicy();
			Caller unverified = Caller.user(ALICE, TENANT, false);

			assertThat(point(List.of(GrantedScope.global()), Set.of(), policy).can(unverified, REVIEW, new Ticket(TENANT)))
				.isFalse();
			assertThat(policy.seen).isEmpty();
		}

		@Test
		@DisplayName("is yes for the system")
		void system() {
			Caller system = new Caller(UUID.randomUUID(), TENANT, true, true);

			assertThat(point(List.of(), Set.of()).can(system, REVIEW, new Ticket(TENANT))).isTrue();
		}

		@Test
		@DisplayName("does not use a plain resource policy, which could ignore that the caller holds nothing")
		void plainPoliciesAreNotUsed() {
			ResourcePolicy allowAll = new ResourcePolicy() {
				@Override
				public boolean supports(Object resource) {
					return true;
				}

				@Override
				public boolean allows(Caller caller, String action, Object resource, Scope scope) {
					return true;
				}
			};
			DefaultDecisionPoint point = new DefaultDecisionPoint(REGISTRY, (caller) -> Set.of(REVIEW),
					(caller, action) -> List.of(GrantedScope.global()), List.of(allowAll), List.of());

			assertThat(point.can(alice(), REVIEW, new Ticket(TENANT))).isFalse();
		}

		@Test
		@DisplayName("refuses an action nobody declared")
		void unknownAction() {
			assertThatThrownBy(() -> point(List.of(), Set.of()).can(alice(), "nobody.declared.this", new Ticket(TENANT)))
				.isInstanceOf(IllegalStateException.class);
		}

	}

	@Nested
	@DisplayName("the reach of a permission for a list")
	class Reaches {

		@Test
		@DisplayName("is nothing without the permission")
		void nothing() {
			assertThat(point(List.of(), Set.of()).reachFor(alice(), REVIEW).isEmpty()).isTrue();
		}

		@Test
		@DisplayName("is everywhere for a global grant, whatever else the caller holds")
		void everywhere() {
			Reach reach = point(List.of(GrantedScope.of("DEPARTMENT", DEPARTMENT), GrantedScope.global()), Set.of())
				.reachFor(alice(), REVIEW);

			assertThat(reach.everywhere()).isTrue();
		}

		@Test
		@DisplayName("lists the ids held, per kind, for scoped grants")
		void scoped() {
			UUID other = UUID.randomUUID();
			Reach reach = point(List.of(GrantedScope.of("DEPARTMENT", DEPARTMENT), GrantedScope.of("DEPARTMENT", other),
					GrantedScope.of("BATCH", other)), Set.of()).reachFor(alice(), REVIEW);

			assertThat(reach.everywhere()).isFalse();
			assertThat(reach.idsOf("DEPARTMENT")).containsExactlyInAnyOrder(DEPARTMENT, other);
			assertThat(reach.idsOf("BATCH")).containsExactly(other);
			assertThat(reach.idsOf("DIVISION")).isEmpty();
		}

		@Test
		@DisplayName("is nothing for a caller who has not verified their email address, and everywhere for the system")
		void unverifiedAndSystem() {
			DefaultDecisionPoint point = point(List.of(GrantedScope.global()), Set.of());

			assertThat(point.reachFor(Caller.user(ALICE, TENANT, false), REVIEW).isEmpty()).isTrue();
			assertThat(point.reachFor(new Caller(UUID.randomUUID(), TENANT, true, true), REVIEW).everywhere()).isTrue();
		}

		@Test
		@DisplayName("is refused for a permission that is not policy-decided, which has scopeFor")
		void onlyForPolicyDecided() {
			assertThatThrownBy(() -> point(List.of(), Set.of(UPDATE)).reachFor(alice(), UPDATE))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("scopeFor");
		}

	}

	@Nested
	@DisplayName("grants")
	class Grants {

		@Test
		@DisplayName("only the global scope has no id, and every other kind needs one")
		void idRules() {
			assertThat(GrantedScope.global().isGlobal()).isTrue();
			assertThatThrownBy(() -> GrantedScope.of("DEPARTMENT", null)).isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> GrantedScope.of(GrantedScope.GLOBAL, DEPARTMENT)).isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> GrantedScope.of(" ", DEPARTMENT)).isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("a reach that cannot be changed from outside")
		void reachIsImmutable() {
			Reach reach = Reach.of(List.of(GrantedScope.of("BATCH", DEPARTMENT)));

			assertThatThrownBy(() -> reach.idsOf("BATCH").add(UUID.randomUUID())).isInstanceOf(UnsupportedOperationException.class);
			assertThat(Reach.nothing().isEmpty()).isTrue();
			assertThat(Reach.everything().isEmpty()).isFalse();
		}

	}

}
