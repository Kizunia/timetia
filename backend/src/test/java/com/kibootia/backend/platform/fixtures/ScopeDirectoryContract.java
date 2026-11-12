package com.kibootia.backend.platform.fixtures;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kibootia.backend.academic.AcademicScopes;
import com.kibootia.backend.platform.authorization.ScopeDirectory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What every {@link ScopeDirectory} must do, as a test the owner of department, division and batch data extends (Timetia
 * ADR 0004 and 0006, integration contract). The security code relies on exactly these answers to decide whether a role may
 * be given in a place, and it never checks them again, so a provider that breaks one of them lets a role be given in a
 * place that does not exist, or in the wrong one.
 *
 * <p>To use it, extend this class in the provider's own test, build the provider against a test database, and implement
 * the three hooks to create and delete places the way the module itself would. The hooks act on the <em>current</em>
 * tenant unless they say otherwise. Nothing here assumes how codes are compared (case, spaces): that is the provider's
 * rule. It only requires that the rule is applied the same way every time and that a code never names two places.
 *
 * <p>{@code ScopeDirectoryContractTest} runs it against the test double, so the double is held to the same rules.
 */
public abstract class ScopeDirectoryContract {

	/** A fresh provider with no places, for the current tenant. Called before every test. */
	protected abstract ScopeDirectory newDirectory();

	/** Adds a live place of this kind with this code to the current tenant. */
	protected abstract void addPlace(String kind, UUID id, String code);

	/** Deletes the place the way the owning module does: it is no longer live. */
	protected abstract void deletePlace(String kind, UUID id);

	/**
	 * Adds a live place to <em>another</em> tenant. A provider has to prove that tenants are separate; a test double with
	 * no notion of a tenant records the place somewhere it is never read.
	 */
	protected abstract void addPlaceInAnotherTenant(String kind, UUID id, String code);

	private static final List<String> KINDS = List.of(AcademicScopes.DEPARTMENT, AcademicScopes.DIVISION, AcademicScopes.BATCH);

	@Test
	@DisplayName("a live place exists, is found by its code and has that code, for each kind")
	void livePlace() {
		ScopeDirectory directory = newDirectory();
		for (String kind : KINDS) {
			UUID id = UUID.randomUUID();
			addPlace(kind, id, "CODE-" + kind);

			assertThat(directory.exists(kind, id)).as(kind + " exists").isTrue();
			assertThat(directory.idOf(kind, "CODE-" + kind)).as(kind + " by code").contains(id);
			assertThat(directory.codesOf(kind, List.of(id))).as(kind + " code").containsExactly(Map.entry(id, "CODE-" + kind));
		}
	}

	@Test
	@DisplayName("a place nobody knows does not exist, has no id and has no code")
	void unknownPlace() {
		ScopeDirectory directory = newDirectory();
		UUID known = UUID.randomUUID();
		addPlace(AcademicScopes.DEPARTMENT, known, "CSE");

		assertThat(directory.exists(AcademicScopes.DEPARTMENT, UUID.randomUUID())).isFalse();
		assertThat(directory.idOf(AcademicScopes.DEPARTMENT, "NOPE")).isEmpty();
		assertThat(directory.codesOf(AcademicScopes.DEPARTMENT, List.of(UUID.randomUUID()))).isEmpty();
	}

	@Test
	@DisplayName("a place is only of its own kind: a division is not a department, and its code finds nothing among departments")
	void kindsAreSeparate() {
		ScopeDirectory directory = newDirectory();
		UUID division = UUID.randomUUID();
		addPlace(AcademicScopes.DIVISION, division, "CSE-A");

		assertThat(directory.exists(AcademicScopes.DEPARTMENT, division)).isFalse();
		assertThat(directory.exists(AcademicScopes.BATCH, division)).isFalse();
		assertThat(directory.idOf(AcademicScopes.DEPARTMENT, "CSE-A")).isEmpty();
		assertThat(directory.idOf(AcademicScopes.BATCH, "CSE-A")).isEmpty();
		assertThat(directory.codesOf(AcademicScopes.DEPARTMENT, List.of(division))).isEmpty();
	}

	@Test
	@DisplayName("an unknown kind, a blank code and a null code are answered with no place, not with an exception or a guess")
	void oddQuestions() {
		ScopeDirectory directory = newDirectory();
		UUID id = UUID.randomUUID();
		addPlace(AcademicScopes.DEPARTMENT, id, "CSE");

		assertThat(directory.exists("PLANET", id)).isFalse();
		assertThat(directory.idOf("PLANET", "CSE")).isEmpty();
		assertThat(directory.codesOf("PLANET", List.of(id))).isEmpty();
		assertThat(directory.idOf(AcademicScopes.DEPARTMENT, "")).isEmpty();
		assertThat(directory.idOf(AcademicScopes.DEPARTMENT, "   ")).isEmpty();
		assertThat(directory.codesOf(AcademicScopes.DEPARTMENT, List.of())).isEmpty();
	}

	@Test
	@DisplayName("a deleted place no longer exists, is not found by its code and has no code; the places beside it are unaffected")
	void deletedPlace() {
		ScopeDirectory directory = newDirectory();
		UUID gone = UUID.randomUUID();
		UUID stays = UUID.randomUUID();
		addPlace(AcademicScopes.DEPARTMENT, gone, "OLD");
		addPlace(AcademicScopes.DEPARTMENT, stays, "NEW");

		deletePlace(AcademicScopes.DEPARTMENT, gone);

		assertThat(directory.exists(AcademicScopes.DEPARTMENT, gone)).isFalse();
		assertThat(directory.idOf(AcademicScopes.DEPARTMENT, "OLD")).isEmpty();
		assertThat(directory.codesOf(AcademicScopes.DEPARTMENT, List.of(gone, stays))).containsExactly(Map.entry(stays, "NEW"));
		assertThat(directory.exists(AcademicScopes.DEPARTMENT, stays)).isTrue();
		assertThat(directory.idOf(AcademicScopes.DEPARTMENT, "NEW")).contains(stays);
	}

	@Test
	@DisplayName("a code freed by a deleted place can name a new place, and then names only the new one")
	void codeReuse() {
		ScopeDirectory directory = newDirectory();
		UUID old = UUID.randomUUID();
		UUID renewed = UUID.randomUUID();
		addPlace(AcademicScopes.DIVISION, old, "CSE-A");
		deletePlace(AcademicScopes.DIVISION, old);
		addPlace(AcademicScopes.DIVISION, renewed, "CSE-A");

		assertThat(directory.idOf(AcademicScopes.DIVISION, "CSE-A")).contains(renewed);
		assertThat(directory.exists(AcademicScopes.DIVISION, old)).as("an assignment to the old place must not match the new one").isFalse();
	}

	@Test
	@DisplayName("codesOf answers a mixed list: only the live places, each with its own code")
	void mixedList() {
		ScopeDirectory directory = newDirectory();
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		addPlace(AcademicScopes.BATCH, a, "CSE-A1");
		addPlace(AcademicScopes.BATCH, b, "CSE-A2");

		Map<UUID, String> codes = directory.codesOf(AcademicScopes.BATCH, List.of(a, UUID.randomUUID(), b));

		assertThat(codes).containsOnly(Map.entry(a, "CSE-A1"), Map.entry(b, "CSE-A2"));
	}

	@Test
	@DisplayName("the same question gets the same answer every time")
	void stable() {
		ScopeDirectory directory = newDirectory();
		UUID id = UUID.randomUUID();
		addPlace(AcademicScopes.DEPARTMENT, id, "CSE");

		for (int i = 0; i < 3; i++) {
			assertThat(directory.idOf(AcademicScopes.DEPARTMENT, "CSE")).contains(id);
			assertThat(directory.idOf(AcademicScopes.DEPARTMENT, "CSE")).isEqualTo(directory.idOf(AcademicScopes.DEPARTMENT, "CSE"));
		}
	}

	@Test
	@DisplayName("a place of another tenant does not exist here: not by id, not by code, and its code is not shown")
	void tenantsAreSeparate() {
		ScopeDirectory directory = newDirectory();
		UUID foreign = UUID.randomUUID();
		addPlaceInAnotherTenant(AcademicScopes.DEPARTMENT, foreign, "FOREIGN");

		assertThat(directory.exists(AcademicScopes.DEPARTMENT, foreign)).isFalse();
		assertThat(directory.idOf(AcademicScopes.DEPARTMENT, "FOREIGN")).isEmpty();
		assertThat(directory.codesOf(AcademicScopes.DEPARTMENT, List.of(foreign))).isEmpty();
	}

}
