package com.kibootia.backend.access.domain;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.kibootia.backend.platform.authorization.Caller;
import com.kibootia.backend.platform.authorization.Owned;
import com.kibootia.backend.platform.authorization.Permission;
import com.kibootia.backend.platform.authorization.ResourcePolicy;
import com.kibootia.backend.platform.authorization.Scope;
import com.kibootia.backend.platform.authorization.TenantBound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultDecisionPointTest {

	static final UUID TENANT = UUID.fromString("00000000-0000-7000-8000-000000000001");

	static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-7000-8000-000000000002");

	static final UUID ALICE = UUID.fromString("11111111-1111-7111-8111-111111111111");

	static final UUID BOB = UUID.fromString("22222222-2222-7222-8222-222222222222");

	static final String UPDATE = "notes.notes.update";

	static final String UPDATE_ANY = "notes.notes.update-any";

	static final String RESTORE = "notes.notes.restore";

	static final PermissionRegistry REGISTRY = new PermissionRegistry(List.of(
			Permission.forEveryUser(UPDATE, "Change own notes"),
			Permission.restricted(UPDATE_ANY, "Change anyone's notes"),
			Permission.restricted(RESTORE, "Restore deleted notes")));

	record Note(UUID ownerId, UUID tenantId) implements Owned, TenantBound {

	}

	static Caller alice() {
		return Caller.user(ALICE, TENANT, true);
	}

	static DefaultDecisionPoint pointFor(Set<String> held, ResourcePolicy... policies) {
		return new DefaultDecisionPoint(REGISTRY, (caller) -> held, List.of(policies));
	}

	@Nested
	@DisplayName("the permission registry")
	class Registry {

		@Test
		@DisplayName("lists what modules declare and which the user role holds")
		void listsPermissions() {
			assertThat(REGISTRY.all()).extracting(Permission::name).containsExactly(UPDATE, UPDATE_ANY, RESTORE);
			assertThat(REGISTRY.userRolePermissions()).containsExactly(UPDATE);
			assertThat(REGISTRY.hasAnyVariant(UPDATE)).isTrue();
			assertThat(REGISTRY.hasAnyVariant(RESTORE)).isFalse();
		}

		@Test
		@DisplayName("refuses a name that is not <module>.<resource>.<action>")
		void refusesBadNames() {
			for (String bad : List.of("update", "notes.update", "Notes.notes.update", "notes.notes.Update",
					"notes.notes.update.again", "notes..update", "notes.notes.update_any")) {
				assertThatThrownBy(() -> new PermissionRegistry(List.of(Permission.restricted(bad, "x"))))
					.as(bad)
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining(bad);
			}
		}

		@Test
		@DisplayName("refuses a permission declared twice")
		void refusesDuplicates() {
			assertThatThrownBy(() -> new PermissionRegistry(
					List.of(Permission.restricted(RESTORE, "a"), Permission.restricted(RESTORE, "b"))))
				.hasMessageContaining(RESTORE);
		}

		@Test
		@DisplayName("refuses an -any permission whose plain permission is missing")
		void refusesOrphanAnyPermission() {
			assertThatThrownBy(() -> new PermissionRegistry(List.of(Permission.restricted(UPDATE_ANY, "x"))))
				.hasMessageContaining(UPDATE);
		}

	}

	@Nested
	@DisplayName("the scope of an action")
	class Scopes {

		@Test
		@DisplayName("is NONE without the permission")
		void none() {
			assertThat(pointFor(Set.of()).scopeFor(alice(), UPDATE)).isEqualTo(Scope.NONE);
			assertThat(pointFor(Set.of()).scopeFor(alice(), RESTORE)).isEqualTo(Scope.NONE);
		}

		@Test
		@DisplayName("is OWN for a permission that has an -any variant")
		void own() {
			assertThat(pointFor(Set.of(UPDATE)).scopeFor(alice(), UPDATE)).isEqualTo(Scope.OWN);
		}

		@Test
		@DisplayName("is ALL for the -any permission")
		void all() {
			assertThat(pointFor(Set.of(UPDATE, UPDATE_ANY)).scopeFor(alice(), UPDATE)).isEqualTo(Scope.ALL);
			assertThat(pointFor(Set.of(UPDATE_ANY)).scopeFor(alice(), UPDATE)).isEqualTo(Scope.ALL);
		}

		@Test
		@DisplayName("is ALL for an administrative permission that has no -any variant")
		void administrativePermission() {
			assertThat(pointFor(Set.of(RESTORE)).scopeFor(alice(), RESTORE)).isEqualTo(Scope.ALL);
		}

		@Test
		@DisplayName("holding the -any variant of another action grants nothing")
		void otherActionGrantsNothing() {
			assertThat(pointFor(Set.of(UPDATE_ANY)).scopeFor(alice(), RESTORE)).isEqualTo(Scope.NONE);
		}

		@Test
		@DisplayName("is ALL for the system")
		void system() {
			Caller system = Caller.user(UUID.randomUUID(), TENANT, true);
			assertThat(pointFor(Set.of()).scopeFor(new Caller(system.userId(), TENANT, true, true), RESTORE))
				.isEqualTo(Scope.ALL);
		}

		@Test
		@DisplayName("is an error for a permission nobody declared")
		void unknownPermission() {
			assertThatThrownBy(() -> pointFor(Set.of()).scopeFor(alice(), "notes.notes.typo"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("notes.notes.typo");
		}

	}

	@Nested
	@DisplayName("a caller who has not verified their email address")
	class Unverified {

		static final String BEFORE = "notes.notes.read-own";

		final PermissionRegistry registry = new PermissionRegistry(List.of(
				Permission.forEveryUser(UPDATE, "Change own notes"),
				Permission.restricted(UPDATE_ANY, "Change anyone's notes"),
				Permission.forEveryUser(BEFORE, "Read own notes").beforeVerification()));

		Caller unverified() {
			return Caller.user(ALICE, TENANT, false);
		}

		@Test
		@DisplayName("holds nothing, even what the user role grants")
		void holdsNothing() {
			DefaultDecisionPoint point = new DefaultDecisionPoint(this.registry, (caller) -> Set.of(UPDATE, UPDATE_ANY, BEFORE),
					List.of());

			assertThat(point.scopeFor(unverified(), UPDATE)).isEqualTo(Scope.NONE);
			assertThat(point.scopeFor(unverified(), UPDATE_ANY)).isEqualTo(Scope.NONE);
			assertThat(point.can(unverified(), UPDATE, new Note(ALICE, TENANT))).isFalse();
		}

		@Test
		@DisplayName("holds a permission marked as allowed before verification")
		void holdsMarkedPermission() {
			DefaultDecisionPoint point = new DefaultDecisionPoint(this.registry, (caller) -> Set.of(BEFORE), List.of());

			assertThat(point.scopeFor(unverified(), BEFORE)).isEqualTo(Scope.ALL);
			assertThat(point.scopeFor(Caller.user(ALICE, TENANT, true), BEFORE)).isEqualTo(Scope.ALL);
		}

		@Test
		@DisplayName("is still denied a marked permission it was never granted")
		void markedPermissionStillNeedsGranting() {
			DefaultDecisionPoint point = new DefaultDecisionPoint(this.registry, (caller) -> Set.of(), List.of());

			assertThat(point.scopeFor(unverified(), BEFORE)).isEqualTo(Scope.NONE);
		}

		@Test
		@DisplayName("is not affected for the system")
		void systemIgnoresVerification() {
			DefaultDecisionPoint point = new DefaultDecisionPoint(this.registry, (caller) -> Set.of(), List.of());

			assertThat(point.scopeFor(new Caller(ALICE, TENANT, false, true), UPDATE_ANY)).isEqualTo(Scope.ALL);
		}

	}

	@Nested
	@DisplayName("a decision about one record")
	class Records {

		@Test
		@DisplayName("allows an owner with the plain permission and denies someone else's record")
		void ownership() {
			DefaultDecisionPoint point = pointFor(Set.of(UPDATE));

			assertThat(point.can(alice(), UPDATE, new Note(ALICE, TENANT))).isTrue();
			assertThat(point.can(alice(), UPDATE, new Note(BOB, TENANT))).isFalse();
		}

		@Test
		@DisplayName("denies an own-scope permission on a record that has no owner")
		void recordWithoutOwner() {
			assertThat(pointFor(Set.of(UPDATE)).can(alice(), UPDATE, new Object())).isFalse();
		}

		@Test
		@DisplayName("allows any record in the tenant with the -any permission")
		void anyPermission() {
			assertThat(pointFor(Set.of(UPDATE_ANY)).can(alice(), UPDATE, new Note(BOB, TENANT))).isTrue();
		}

		@Test
		@DisplayName("denies without the permission, even for the owner")
		void withoutPermission() {
			assertThat(pointFor(Set.of()).can(alice(), UPDATE, new Note(ALICE, TENANT))).isFalse();
		}

		@Test
		@DisplayName("denies a record of another tenant even with the -any permission")
		void otherTenant() {
			assertThat(pointFor(Set.of(UPDATE_ANY)).can(alice(), UPDATE, new Note(ALICE, OTHER_TENANT))).isFalse();
			assertThat(pointFor(Set.of(UPDATE)).can(alice(), UPDATE, new Note(ALICE, OTHER_TENANT))).isFalse();
		}

		@Test
		@DisplayName("lets the system act on a record of its own tenant")
		void system() {
			Caller system = new Caller(new UUID(0L, 0L), TENANT, true, true);
			assertThat(pointFor(Set.of()).can(system, RESTORE, new Note(BOB, TENANT))).isTrue();
		}

		@Test
		@DisplayName("lets a module's own policy decide for the records it supports")
		void modulePolicy() {
			ResourcePolicy sharedNotes = new ResourcePolicy() {
				@Override
				public boolean supports(Object resource) {
					return resource instanceof Note;
				}

				@Override
				public boolean allows(Caller caller, String action, Object resource, Scope scope) {
					return scope == Scope.OWN && ((Note) resource).ownerId().equals(BOB);
				}
			};
			DefaultDecisionPoint point = pointFor(Set.of(UPDATE), sharedNotes);

			assertThat(point.can(alice(), UPDATE, new Note(BOB, TENANT))).isTrue();
			assertThat(point.can(alice(), UPDATE, new Note(ALICE, TENANT))).isFalse();
		}

		@Test
		@DisplayName("does not ask a module's policy when the caller lacks the permission")
		void policyIsNotConsultedWithoutPermission() {
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

			assertThat(pointFor(Set.of(), allowAll).can(alice(), UPDATE, new Note(ALICE, TENANT))).isFalse();
		}

	}

}
