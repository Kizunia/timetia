package com.kibootia.backend.identity;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.kibootia.backend.AdminApiTest;
import com.kibootia.backend.Await;
import com.kibootia.backend.academic.AcademicPermissions;
import com.kibootia.backend.academic.AcademicScopes;
import com.kibootia.backend.academic.Facts;
import com.kibootia.backend.academic.application.RoleTemplateSeeder;
import com.kibootia.backend.platform.fixtures.ScopeFixtures;
import com.kibootia.backend.platform.fixtures.ScopeFixtures.FakeScopeDirectory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The provisioning import (Timetia ADR 0006, W3.1): what it creates, that a repeat changes nothing, that it never goes
 * beyond what the screen would allow, and what it leaves alone. The tests that need no places are in
 * {@link UserImportWithoutDirectoryIT}.
 */
@Import(ScopeFixtures.class)
class UserImportIT extends AdminApiTest {

	private static final String FILE = "email,role,scope\n";

	@Autowired
	FakeScopeDirectory directory;

	@Autowired
	RoleTemplateSeeder seeder;

	@Autowired
	LockProvider locks;

	@BeforeEach
	void seedRolesAndPlaces() throws Exception {
		this.directory.clear();
		this.directory.add(AcademicScopes.DEPARTMENT, Facts.DEPARTMENT_A, "CSE");
		this.directory.add(AcademicScopes.DEPARTMENT, Facts.DEPARTMENT_B, "ME");
		this.directory.add(AcademicScopes.DIVISION, Facts.DIVISION_1, "CSE-A");
		this.directory.add(AcademicScopes.BATCH, Facts.BATCH_1, "CSE-A1");
		this.seeder.seed();
		ensureAdmin();
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	private ResultActions upload(RequestPostProcessor who, String csv, boolean dryRun) throws Exception {
		return this.mvc.perform(multipart("/api/v1/user-imports")
			.file(new MockMultipartFile("file", "people.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
			.param("dryRun", String.valueOf(dryRun))
			.with(who));
	}

	private ResultActions importFile(String csv) throws Exception {
		return upload(admin(), csv, false).andExpect(status().isOk());
	}

	private static String field(ResultActions result, String path) throws Exception {
		return String.valueOf((Object) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path));
	}

	private int assignments() {
		return count("select count(*) from role_assignment ra join role r on r.id = ra.role_id where r.scope_type is not null");
	}

	private int audits(String action) {
		return count("select count(*) from audit_entry where action = ?", action);
	}

	private int profiles() {
		return count("select count(*) from user_profile");
	}

	// --- creating people and giving roles ----------------------------------------------------------------------------

	@Test
	@DisplayName("a file creates the people who have no account, invites each once and gives each role in its place; the same file again changes nothing")
	void importAndRepeat() throws Exception {
		String csv = FILE + "Anita@college.test,HOD,CSE\nanita@college.test,Faculty,\nravi@college.test,CR,CSE-A\n"
				+ "sneha@college.test,BR,CSE-A1\nmeera@college.test,Student,CSE-A1\n";

		importFile(csv).andExpect(jsonPath("$.dryRun").value(false))
			.andExpect(jsonPath("$.importId").isNotEmpty())
			.andExpect(jsonPath("$.counts.rows").value(5))
			.andExpect(jsonPath("$.counts.accountsCreated").value(4))
			.andExpect(jsonPath("$.counts.added").value(5))
			.andExpect(jsonPath("$.counts.errors").value(0))
			.andExpect(jsonPath("$.lines[0].outcome").value("ADDED"))
			.andExpect(jsonPath("$.lines[0].accountCreated").value(true))
			.andExpect(jsonPath("$.lines[0].email").value("anita@college.test"));

		assertThat(profiles()).as("the administrator and four new people").isEqualTo(5);
		assertThat(count("select count(*) from user_profile where status = 'INVITED'")).isEqualTo(4);
		assertThat(this.keycloak.countCalls("createUser")).isEqualTo(4);
		Await.until("the invitations to be sent", () -> this.keycloak.countCalls("sendInvitation") == 4);
		assertThat(assignments()).isEqualTo(5);
		assertThat(audits("identity.user.invited")).isEqualTo(4);
		assertThat(count("select count(*) from audit_entry where action = 'identity.user.invited' and changes like '%import%'"))
			.isEqualTo(4);
		assertThat(audits("access.role.assigned")).isEqualTo(5);
		assertThat(audits("identity.user-import.completed")).isEqualTo(1);
		assertThat(count("select count(*) from audit_entry where changes like '%@%' or changes like '%college%'"))
			.as("no address in the audit log").isZero();

		// The same file again.
		importFile(csv).andExpect(jsonPath("$.counts.accountsCreated").value(0))
			.andExpect(jsonPath("$.counts.added").value(0))
			.andExpect(jsonPath("$.counts.alreadyPresent").value(5))
			.andExpect(jsonPath("$.lines[0].outcome").value("ALREADY_PRESENT"))
			.andExpect(jsonPath("$.lines[0].accountCreated").value(false));
		assertThat(profiles()).isEqualTo(5);
		assertThat(this.keycloak.countCalls("createUser")).isEqualTo(4);
		assertThat(assignments()).isEqualTo(5);
		assertThat(audits("identity.user.invited")).isEqualTo(4);
		assertThat(audits("access.role.assigned")).isEqualTo(5);
		assertThat(audits("identity.user-import.completed")).as("one summary per real run").isEqualTo(2);
	}

	@Test
	@DisplayName("a person the import created signs in with the address it used and has the permissions of their role in their place")
	void importedPersonSignsIn() throws Exception {
		ResultActions result = importFile(FILE + "anita@college.test,HOD,CSE\n");
		UUID anita = UUID.fromString(field(result, "$.lines[0].userId"));

		get(token(anita, "anita@college.test", true), "/api/v1/me").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.administrator").value(false))
			.andExpect(jsonPath("$.permissions", hasItem(AcademicPermissions.TICKETS_REVERT_IN_SCOPE)));
		get(admin(), "/api/v1/users/" + anita + "/scoped-roles").andExpect(jsonPath("$[0].scopeCode").value("CSE"));
	}

	@Test
	@DisplayName("a person who already has an account only gets the missing roles; no account is created and nothing else about them changes")
	void existingPerson() throws Exception {
		signIn(ALICE);
		String email = ALICE.toString().substring(0, 8) + "@kibootia.test";

		importFile(FILE + email + ",Faculty,\n" + email + ",HOD,CSE\n").andExpect(jsonPath("$.counts.accountsCreated").value(0))
			.andExpect(jsonPath("$.counts.added").value(2))
			.andExpect(jsonPath("$.lines[0].userId").value(ALICE.toString()))
			.andExpect(jsonPath("$.lines[0].accountCreated").value(false));
		assertThat(this.keycloak.countCalls("createUser")).isZero();
		assertThat(this.keycloak.countCalls("sendInvitation")).isZero();
		get(admin(), "/api/v1/users/" + ALICE + "/scoped-roles").andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	@DisplayName("a dry run says what the file would do, exactly, and writes nothing and calls nobody")
	void dryRun() throws Exception {
		signIn(ALICE);
		String email = ALICE.toString().substring(0, 8) + "@kibootia.test";
		String csv = FILE + email + ",Faculty,\nnew@college.test,CR,CSE-A\n";
		int auditBefore = count("select count(*) from audit_entry");

		upload(admin(), csv, true).andExpect(status().isOk())
			.andExpect(jsonPath("$.dryRun").value(true))
			.andExpect(jsonPath("$.importId").doesNotExist())
			.andExpect(jsonPath("$.counts.added").value(2))
			.andExpect(jsonPath("$.counts.accountsCreated").value(1))
			.andExpect(jsonPath("$.lines[0].outcome").value("ADDED"))
			.andExpect(jsonPath("$.lines[1].accountCreated").value(true))
			.andExpect(jsonPath("$.lines[1].userId").doesNotExist());

		assertThat(assignments()).isZero();
		assertThat(profiles()).isEqualTo(2);
		assertThat(count("select count(*) from audit_entry")).isEqualTo(auditBefore);
		assertThat(this.keycloak.countCalls("createUser")).isZero();

		// Once she holds it, the dry run knows.
		importFile(FILE + email + ",Faculty,\n");
		upload(admin(), FILE + email + ",Faculty,\n", true).andExpect(jsonPath("$.lines[0].outcome").value("ALREADY_PRESENT"));
	}

	// --- what an import will not do ---------------------------------------------------------------------------------

	@Test
	@DisplayName("a file cannot give a platform role, the Administrator role least of all")
	void platformRoles() throws Exception {
		importFile(FILE + "a@college.test,Administrator,\nb@college.test,User,\nc@college.test,Nonexistent,\n")
			.andExpect(jsonPath("$.counts.errors").value(3))
			.andExpect(jsonPath("$.lines[0].code").value("role_not_assignable"))
			.andExpect(jsonPath("$.lines[1].code").value("role_not_assignable"))
			.andExpect(jsonPath("$.lines[2].code").value("unknown_role"));
		assertThat(profiles()).as("nobody was invited from a file of mistakes").isEqualTo(1);
		assertThat(this.keycloak.countCalls("createUser")).isZero();
		assertThat(count("select count(*) from role_assignment ra join role r on r.id = ra.role_id where r.built_in = 'ADMIN'"))
			.as("only the bootstrap administrator").isEqualTo(1);
	}

	@Test
	@DisplayName("every row is checked as the screen would: the right scope for the role, a place that exists, an address that is one")
	void rowRules() throws Exception {
		importFile(FILE + "a@college.test,HOD,\nb@college.test,Faculty,CSE\nc@college.test,HOD,NOPE\n"
				+ "d@college.test,CR,CSE\nnot-an-address,Faculty,\ne@college.test,,\n")
			.andExpect(jsonPath("$.counts.errors").value(6))
			.andExpect(jsonPath("$.lines[0].code").value("scope_required"))
			.andExpect(jsonPath("$.lines[1].code").value("scope_not_allowed"))
			.andExpect(jsonPath("$.lines[2].code").value("unknown_scope"))
			.andExpect(jsonPath("$.lines[3].code").value("unknown_scope"))
			.andExpect(jsonPath("$.lines[4].code").value("invalid_email"))
			.andExpect(jsonPath("$.lines[5].code").value("role_required"));
		assertThat(profiles()).isEqualTo(1);
		assertThat(assignments()).isZero();
	}

	@Test
	@DisplayName("a scope directory that is down refuses the rows that name a place, invites nobody for them, and still imports roles that need no place; a deleted place is an unknown place")
	void directoryUnavailableAndDeletedPlace() throws Exception {
		this.directory.setUnavailable(true);
		for (boolean dryRun : new boolean[] { true, false }) {
			upload(admin(), FILE + "anita@college.test,HOD,CSE\nravi@college.test,Faculty,\n", dryRun).andExpect(status().isOk())
				.andExpect(jsonPath("$.lines[0].outcome").value("ERROR"))
				.andExpect(jsonPath("$.lines[0].code").value("scope_unvalidated"))
				.andExpect(jsonPath("$.lines[1].outcome").value("ADDED"));
			if (dryRun) {
				assertThat(profiles()).as("a dry run writes nothing").isEqualTo(1);
			}
		}
		assertThat(count("select count(*) from user_profile where email = 'anita@college.test'")).as("no one invited for the refused row")
			.isZero();
		assertThat(count("select count(*) from user_profile where email = 'ravi@college.test'")).as("the global role was imported")
			.isEqualTo(1);
		assertThat(assignments()).as("only ravi's Faculty; nothing in a place").isEqualTo(1);

		this.directory.setUnavailable(false);
		this.directory.remove(AcademicScopes.DEPARTMENT, Facts.DEPARTMENT_A);
		importFile(FILE + "anita@college.test,HOD,CSE\n").andExpect(jsonPath("$.lines[0].code").value("unknown_scope"));
		assertThat(count("select count(*) from user_profile where email = 'anita@college.test'")).isZero();
	}

	@Test
	@DisplayName("a person's rows go together or not at all: one error keeps the rest of that person out, and other people are unaffected")
	void personIsTheUnit() throws Exception {
		importFile(FILE + "anita@college.test,HOD,CSE\nanita@college.test,Nonexistent,\nravi@college.test,CR,CSE-A\n")
			.andExpect(jsonPath("$.lines[0].outcome").value("SKIPPED"))
			.andExpect(jsonPath("$.lines[0].code").value("person_has_errors"))
			.andExpect(jsonPath("$.lines[1].outcome").value("ERROR"))
			.andExpect(jsonPath("$.lines[2].outcome").value("ADDED"))
			.andExpect(jsonPath("$.counts.skipped").value(1));
		assertThat(this.keycloak.countCalls("createUser")).as("only ravi").isEqualTo(1);
		assertThat(count("select count(*) from user_profile where email = 'anita@college.test'")).isZero();
		assertThat(assignments()).isEqualTo(1);

		// Fixing the file and sending it again finishes the job.
		importFile(FILE + "anita@college.test,HOD,CSE\nravi@college.test,CR,CSE-A\n")
			.andExpect(jsonPath("$.counts.added").value(1))
			.andExpect(jsonPath("$.counts.alreadyPresent").value(1));
	}

	@Test
	@DisplayName("a repeated line is skipped, not an error, and does not stop the person being created")
	void duplicateRows() throws Exception {
		importFile(FILE + "anita@college.test,Faculty,\nANITA@college.test,Faculty,\n")
			.andExpect(jsonPath("$.lines[0].outcome").value("ADDED"))
			.andExpect(jsonPath("$.lines[1].outcome").value("SKIPPED"))
			.andExpect(jsonPath("$.lines[1].code").value("duplicate_in_file"))
			.andExpect(jsonPath("$.counts.errors").value(0));
		assertThat(assignments()).isEqualTo(1);
	}

	@Test
	@DisplayName("a disabled or deleted person is reported and left exactly as they are")
	void disabledAndDeleted() throws Exception {
		signIn(ALICE);
		signIn(BOB);
		post(admin(), "/api/v1/users/" + ALICE + "/disable", null).andExpect(status().isOk());
		delete(admin(), "/api/v1/users/" + BOB).andExpect(status().isNoContent());
		String alice = ALICE.toString().substring(0, 8) + "@kibootia.test";
		String bob = BOB.toString().substring(0, 8) + "@kibootia.test";

		importFile(FILE + alice + ",Faculty,\n" + bob + ",Faculty,\n").andExpect(jsonPath("$.lines[0].code").value("account_disabled"))
			.andExpect(jsonPath("$.lines[1].code").value("account_deleted"))
			.andExpect(jsonPath("$.counts.errors").value(2));
		assertThat(assignments()).isZero();
		assertThat(count("select count(*) from user_profile where status = 'DISABLED'")).isEqualTo(1);
		assertThat(count("select count(*) from user_profile where deleted_at is not null")).isEqualTo(1);
	}

	@Test
	@DisplayName("an address the identity provider knows but this application does not is reported, not invited again")
	void accountWithoutProfile() throws Exception {
		this.keycloak.knowsEmail("ghost@college.test");

		importFile(FILE + "ghost@college.test,Faculty,\nanita@college.test,Faculty,\n")
			.andExpect(jsonPath("$.lines[0].code").value("account_without_profile"))
			.andExpect(jsonPath("$.lines[1].outcome").value("ADDED"));
		assertThat(count("select count(*) from user_profile where email = 'ghost@college.test'")).isZero();
	}

	// --- failure ----------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("when the identity provider fails for one person nothing is left behind for them, the rest carry on, and sending the file again finishes it")
	void identityProviderFails() throws Exception {
		this.keycloak.failNext("createUser", 1);
		String csv = FILE + "anita@college.test,Faculty,\nravi@college.test,Faculty,\n";

		importFile(csv).andExpect(jsonPath("$.lines[0].outcome").value("ERROR"))
			.andExpect(jsonPath("$.lines[0].code").value("identity_unavailable"))
			.andExpect(jsonPath("$.lines[1].outcome").value("ADDED"));
		assertThat(count("select count(*) from user_profile where email = 'anita@college.test'")).isZero();
		assertThat(assignments()).isEqualTo(1);

		importFile(csv).andExpect(jsonPath("$.lines[0].outcome").value("ADDED"))
			.andExpect(jsonPath("$.lines[1].outcome").value("ALREADY_PRESENT"));
		assertThat(assignments()).isEqualTo(2);
	}

	@Test
	@DisplayName("when the identity provider stays down the import stops trying and reports the rest as not processed")
	void identityProviderDown() throws Exception {
		this.keycloak.failEverything();
		StringBuilder csv = new StringBuilder(FILE);
		for (int i = 0; i < 7; i++) {
			csv.append("p").append(i).append("@college.test,Faculty,\n");
		}

		importFile(csv.toString()).andExpect(jsonPath("$.counts.errors").value(5))
			.andExpect(jsonPath("$.counts.notProcessed").value(2))
			.andExpect(jsonPath("$.lines[6].outcome").value("NOT_PROCESSED"))
			.andExpect(jsonPath("$.lines[6].code").value("identity_unavailable"));
		assertThat(this.keycloak.countCalls("createUser")).isEqualTo(5);
		assertThat(profiles()).isEqualTo(1);
	}

	@Test
	@DisplayName("only one real import runs at a time")
	void oneAtATime() throws Exception {
		LockConfiguration lock = new LockConfiguration(Instant.now(),
				"identity-user-import-" + com.kibootia.backend.platform.tenancy.TenantContext.DEFAULT_TENANT, Duration.ofMinutes(1),
				Duration.ZERO);
		SimpleLock held = this.locks.lock(lock).orElseThrow();
		try {
			upload(admin(), FILE + "a@college.test,Faculty,\n", false).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("identity.import_running"));
			upload(admin(), FILE + "a@college.test,Faculty,\n", true).andExpect(status().isOk());
		}
		finally {
			held.unlock();
		}
		assertThat(profiles()).isEqualTo(1);
		upload(admin(), FILE + "a@college.test,Faculty,\n", false).andExpect(status().isOk());
	}

	// --- who may ----------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("only an administrator imports, even with every permission the work seems to need; nothing is written and the refusal is recorded")
	void onlyAdministrators() throws Exception {
		String importers = createRole("Importers", "identity.users.invite", "access.roles.assign", "access.roles.view",
				"identity.profiles.view");
		signIn(ALICE);
		signIn(BOB);
		setRoles(ALICE, builtInRoleId("USER"), importers);
		String csv = FILE + "anita@college.test,Faculty,\n";

		upload(user(ALICE), csv, false).andExpect(status().isForbidden());
		upload(user(ALICE), csv, true).andExpect(status().isForbidden());
		upload(user(BOB), csv, false).andExpect(status().isForbidden());
		upload(unverified(ALICE), csv, false).andExpect(status().isForbidden());
		// Not even a file with nothing but mistakes in it is looked at: the check is the import's own, not only the one in access.
		upload(user(ALICE), FILE + "not-an-address,Faculty,\n", false).andExpect(status().isForbidden());

		assertThat(profiles()).isEqualTo(3);
		assertThat(this.keycloak.countCalls("createUser")).isZero();
		assertThat(assignments()).isZero();
		assertThat(audits("identity.user-import.completed")).isZero();
		assertThat(audits("security.access.denied")).isPositive();
	}

	// --- the file itself --------------------------------------------------------------------------------------------

	@Test
	@DisplayName("a file that cannot be read at all, or is too big, is refused with a reason and nobody is touched")
	void unreadableFiles() throws Exception {
		upload(admin(), "email,role\n", false).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("file"))
			.andExpect(jsonPath("$.errors[0].code").value("no_rows"));
		upload(admin(), "name,role\nAnita,Faculty\n", false).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("unknown_column"));

		StringBuilder manyRows = new StringBuilder(FILE);
		for (int i = 0; i < 1001; i++) {
			manyRows.append("p").append(i).append("@college.test,Faculty,\n");
		}
		upload(admin(), manyRows.toString(), false).andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.code").value("identity.import_too_large"));

		upload(admin(), FILE + "a@college.test,Faculty,\n" + "x".repeat(600 * 1024) + "\n", false)
			.andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.code").value("identity.import_too_large"));
		assertThat(profiles()).isEqualTo(1);
		assertThat(audits("identity.user-import.completed")).as("a refused file is not a run").isZero();
	}

	@Test
	@DisplayName("the report names the lines of the file, so a mistake can be found")
	void lineNumbers() throws Exception {
		importFile(FILE + "\na@college.test,Faculty,\nb@college.test,Nonexistent,\n")
			.andExpect(jsonPath("$.lines[0].line").value(3))
			.andExpect(jsonPath("$.lines[1].line").value(4));
	}

}
