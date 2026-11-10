package com.kibootia.backend.notes;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.kibootia.backend.Await;
import com.kibootia.backend.GrantedPermissions;
import com.kibootia.backend.TestcontainersConfiguration;
import com.kibootia.backend.platform.jobs.RetentionPurgeJob;
import com.kibootia.backend.platform.tenancy.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The reference feature through the real stack: filter chain, tenant, method security, decision point, MySQL (ADR 0043,
 * ADR 0044, ADR 0034). Every operation has an allowed and a denied case, lists test their scope, and the soft-delete,
 * tenant-isolation, locking and purge conventions are shown on a real resource.
 */
@SpringBootTest(properties = "kibootia.jobs.soft-delete-retention-days=30")
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, GrantedPermissions.class })
class NotesApiIT {

	static final UUID ALICE = UUID.fromString("11111111-1111-7111-8111-111111111111");

	static final UUID BOB = UUID.fromString("22222222-2222-7222-8222-222222222222");

	static final UUID ADMIN = UUID.fromString("44444444-4444-7444-8444-444444444444");

	static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-7000-8000-0000000000bb");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	RetentionPurgeJob retention;

	@BeforeEach
	@AfterEach
	void clean() {
		Await.until("in-flight deliveries to finish", () -> jdbc.queryForObject(
				"select count(*) from event_publication where completion_date is null and status in "
						+ "('PUBLISHED', 'PROCESSING', 'RESUBMITTED', 'FAILED')", Integer.class) == 0);
		jdbc.update("delete from note");
		jdbc.update("delete from audit_entry");
		jdbc.update("delete from event_publication");
		jdbc.update("delete from processed_event");
		GrantedPermissions.reset();
	}

	private static MockHttpServletRequestBuilder as(UUID user, MockHttpServletRequestBuilder request) {
		return request.with(jwt().jwt((token) -> token.subject(user.toString()).claim("email_verified", true)));
	}

	private ResultActions create(UUID user, String title) throws Exception {
		return mvc.perform(as(user, post("/api/v1/notes")).contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"" + title + "\",\"body\":\"Body of " + title + "\"}"));
	}

	private String created(UUID user, String title) throws Exception {
		String json = create(user, title).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.id");
	}

	private ResultActions update(UUID user, String id, String title, long version) throws Exception {
		return mvc.perform(as(user, put("/api/v1/notes/" + id)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"" + title + "\",\"body\":\"Changed\",\"version\":" + version + "}"));
	}

	private static String hex(UUID id) {
		return id.toString().replace("-", "");
	}

	private int count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

	/** A live note of another tenant, written straight into the table. */
	private String noteOfAnotherTenant(String title) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into note (id, tenant_id, version, created_at, created_by, updated_at, updated_by, owner_id, "
				+ "title, body) values (unhex(?), unhex(?), 0, now(6), unhex(?), now(6), unhex(?), unhex(?), ?, 'x')",
				hex(id), hex(OTHER_TENANT), hex(ALICE), hex(ALICE), hex(ALICE), title);
		return id.toString();
	}

	@Test
	@DisplayName("without a token every notes endpoint is 401")
	void unauthenticated() throws Exception {
		mvc.perform(get("/api/v1/notes")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/notes").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/notes/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("creating a note returns 201 with its location, a UUIDv7 id, the owner and version 0")
	void createNote() throws Exception {
		create(ALICE, "First").andExpect(status().isCreated())
			.andExpect(header().string("Location", containsString("/api/v1/notes/")))
			.andExpect(jsonPath("$.title").value("First"))
			.andExpect(jsonPath("$.body").value("Body of First"))
			.andExpect(jsonPath("$.ownerId").value(ALICE.toString()))
			.andExpect(jsonPath("$.version").value(0))
			.andExpect(jsonPath("$.createdAt").isString())
			.andExpect(jsonPath("$.tenantId").doesNotExist())
			.andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.matchesPattern(
					"[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")));
		assertThat(count("select count(*) from note")).isEqualTo(1);
	}

	@Test
	@DisplayName("a title is trimmed, and a blank or oversize title or body is 400 with one entry per field")
	void createValidation() throws Exception {
		create(ALICE, "  Padded  ").andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("Padded"));

		mvc.perform(as(ALICE, post("/api/v1/notes")).contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"  \",\"body\":null}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("platform.validation_failed"))
			.andExpect(jsonPath("$.errors[?(@.field=='title')].code").value("required"))
			.andExpect(jsonPath("$.errors[?(@.field=='body')].code").value("required"));
		mvc.perform(as(ALICE, post("/api/v1/notes")).contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"" + "x".repeat(121) + "\",\"body\":\"" + "y".repeat(4001) + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.length()").value(2));
	}

	@Test
	@DisplayName("two live notes of one owner cannot share a title, but another owner may use it")
	void duplicateTitle() throws Exception {
		created(ALICE, "Same");

		create(ALICE, "Same").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("notes.duplicate_title"));
		create(BOB, "Same").andExpect(status().isCreated());
	}

	@Test
	@DisplayName("a deleted note's title can be used again")
	void titleCanBeReusedAfterDelete() throws Exception {
		String id = created(ALICE, "Reusable");
		mvc.perform(as(ALICE, delete("/api/v1/notes/" + id))).andExpect(status().isNoContent());

		String again = created(ALICE, "Reusable");

		assertThat(again).isNotEqualTo(id);
		assertThat(count("select count(*) from note")).isEqualTo(2);
	}

	@Test
	@DisplayName("an owner reads their note; someone else gets 404, not 403, so its existence is not revealed")
	void readIsOwnerOnlyAndHidesExistence() throws Exception {
		String id = created(ALICE, "Private");

		mvc.perform(as(ALICE, get("/api/v1/notes/" + id))).andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Private"));
		mvc.perform(as(BOB, get("/api/v1/notes/" + id)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("notes.not_found"));
		mvc.perform(as(BOB, get("/api/v1/notes/" + UUID.randomUUID())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("notes.not_found"));
	}

	@Test
	@DisplayName("a caller who may view any note reads someone else's")
	void viewAnyReadsOthers() throws Exception {
		String id = created(ALICE, "Shared with admins");
		GrantedPermissions.grant(NotesPermissions.VIEW_ANY);

		mvc.perform(as(ADMIN, get("/api/v1/notes/" + id))).andExpect(status().isOk());
	}

	@Test
	@DisplayName("an id that is not a UUID is 400")
	void malformedId() throws Exception {
		mvc.perform(as(ALICE, get("/api/v1/notes/not-a-uuid"))).andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("an owner updates their note and the version goes up")
	void updateOwnNote() throws Exception {
		String id = created(ALICE, "Draft");

		update(ALICE, id, "Final", 0).andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Final"))
			.andExpect(jsonPath("$.body").value("Changed"))
			.andExpect(jsonPath("$.version").value(1));
		assertThat(jdbc.queryForObject("select hex(updated_by) from note", String.class)).isEqualTo(hex(ALICE).toUpperCase());
	}

	@Test
	@DisplayName("an update with an old version is 409 and changes nothing")
	void staleUpdate() throws Exception {
		String id = created(ALICE, "Contended");
		update(ALICE, id, "First change", 0).andExpect(status().isOk());

		update(ALICE, id, "Second change", 0).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("platform.concurrent_modification"));

		assertThat(jdbc.queryForObject("select title from note", String.class)).isEqualTo("First change");
	}

	@Test
	@DisplayName("an update without a version is 400")
	void updateNeedsAVersion() throws Exception {
		String id = created(ALICE, "Needs version");

		mvc.perform(as(ALICE, put("/api/v1/notes/" + id)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"x\",\"body\":\"y\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("version"));
	}

	@Test
	@DisplayName("renaming to a title the owner already uses is 409")
	void renameToDuplicate() throws Exception {
		created(ALICE, "Taken");
		String id = created(ALICE, "Other");

		update(ALICE, id, "Taken", 0).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("notes.duplicate_title"));
	}

	@Test
	@DisplayName("changing a note you can see but may not change is 403; one you cannot see is 404")
	void updateDenied() throws Exception {
		String id = created(ALICE, "Alice's");

		update(BOB, id, "Hijacked", 0).andExpect(status().isNotFound());

		GrantedPermissions.grant(NotesPermissions.VIEW_ANY);
		update(BOB, id, "Hijacked", 0).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("platform.forbidden"));
		assertThat(jdbc.queryForObject("select title from note", String.class)).isEqualTo("Alice's");
	}

	@Test
	@DisplayName("a caller who may update any note changes someone else's")
	void updateAny() throws Exception {
		String id = created(ALICE, "Alice's");
		GrantedPermissions.grant(NotesPermissions.VIEW_ANY, NotesPermissions.UPDATE_ANY);

		update(ADMIN, id, "Moderated", 0).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Moderated"));
		assertThat(jdbc.queryForObject("select hex(owner_id) from note", String.class)).isEqualTo(hex(ALICE).toUpperCase());
		assertThat(jdbc.queryForObject("select hex(updated_by) from note", String.class))
			.isEqualTo(hex(ADMIN).toUpperCase());
	}

	@Test
	@DisplayName("deleting hides the note, keeps the row, audits it and raises a durable event that is handled")
	void deleteOwnNote() throws Exception {
		String id = created(ALICE, "Doomed");

		mvc.perform(as(ALICE, delete("/api/v1/notes/" + id))).andExpect(status().isNoContent());

		mvc.perform(as(ALICE, get("/api/v1/notes/" + id))).andExpect(status().isNotFound());
		assertThat(count("select count(*) from note where deleted_at is not null")).isEqualTo(1);
		var audit = jdbc.queryForMap("select hex(actor_id) actor, action, hex(target_id) target from audit_entry");
		assertThat(audit.get("actor")).isEqualTo(hex(ALICE).toUpperCase());
		assertThat(audit.get("action")).isEqualTo("notes.note.deleted");
		assertThat(audit.get("target")).isEqualTo(hex(UUID.fromString(id)).toUpperCase());
		Await.until("the NoteDeleted event to be handled", () -> count(
				"select count(*) from event_publication where completion_date is not null and event_type like '%NoteDeleted'")
				== 1);
	}

	@Test
	@DisplayName("deleting someone else's note is 404, or 403 if you can see it but may not delete it")
	void deleteDenied() throws Exception {
		String id = created(ALICE, "Alice's");

		mvc.perform(as(BOB, delete("/api/v1/notes/" + id))).andExpect(status().isNotFound());
		GrantedPermissions.grant(NotesPermissions.VIEW_ANY);
		mvc.perform(as(BOB, delete("/api/v1/notes/" + id))).andExpect(status().isForbidden());

		assertThat(count("select count(*) from note where deleted_at is null")).isEqualTo(1);
		assertThat(count("select count(*) from audit_entry")).isZero();
	}

	@Test
	@DisplayName("a caller who may delete any note deletes someone else's")
	void deleteAny() throws Exception {
		String id = created(ALICE, "Alice's");
		GrantedPermissions.grant(NotesPermissions.VIEW_ANY, NotesPermissions.DELETE_ANY);

		mvc.perform(as(ADMIN, delete("/api/v1/notes/" + id))).andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("select hex(actor_id) from audit_entry", String.class))
			.isEqualTo(hex(ADMIN).toUpperCase());
	}

	@Test
	@DisplayName("deleting a note twice is 404 the second time")
	void deleteTwice() throws Exception {
		String id = created(ALICE, "Once");
		mvc.perform(as(ALICE, delete("/api/v1/notes/" + id))).andExpect(status().isNoContent());

		mvc.perform(as(ALICE, delete("/api/v1/notes/" + id))).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("a list holds only the caller's notes, newest first, in the standard page shape")
	void listIsLimitedToOwnNotes() throws Exception {
		created(ALICE, "Alice one");
		Thread.sleep(5);
		created(ALICE, "Alice two");
		created(BOB, "Bob one");

		mvc.perform(as(ALICE, get("/api/v1/notes"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].title").value("Alice two"))
			.andExpect(jsonPath("$.items[1].title").value("Alice one"))
			.andExpect(jsonPath("$.page.totalElements").value(2))
			.andExpect(jsonPath("$.page.size").value(20))
			.andExpect(jsonPath("$.page.totalPages").value(1));
	}

	@Test
	@DisplayName("a caller who may view any note sees everyone's, and can filter by owner")
	void listAll() throws Exception {
		created(ALICE, "Alice one");
		created(BOB, "Bob one");
		GrantedPermissions.grant(NotesPermissions.VIEW_ANY);

		mvc.perform(as(ADMIN, get("/api/v1/notes"))).andExpect(jsonPath("$.page.totalElements").value(2));
		mvc.perform(as(ADMIN, get("/api/v1/notes?owner=" + BOB))).andExpect(jsonPath("$.page.totalElements").value(1))
			.andExpect(jsonPath("$.items[0].title").value("Bob one"));
	}

	@Test
	@DisplayName("asking for another owner's notes without permission gives an empty page, not an error")
	void listOtherOwnerIsEmpty() throws Exception {
		created(BOB, "Bob one");

		mvc.perform(as(ALICE, get("/api/v1/notes?owner=" + BOB))).andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(0))
			.andExpect(jsonPath("$.page.totalElements").value(0));
	}

	@Test
	@DisplayName("a list is filtered by text and treats % and _ as ordinary characters")
	void listFilter() throws Exception {
		created(ALICE, "Groceries");
		created(ALICE, "100% done");
		created(ALICE, "snake_case");

		assertThat(matches("GROCER")).isEqualTo(1);
		assertThat(matches("body of groceries")).as("the body is searched too").isEqualTo(1);
		assertThat(matches("100%")).as("a percent sign is literal").isEqualTo(1);
		assertThat(matches("%")).as("a lone percent sign is not a wildcard").isEqualTo(1);
		assertThat(matches("snake_c")).as("an underscore is literal").isEqualTo(1);
		assertThat(matches("snake%c")).isZero();
		assertThat(matches("nothing like this")).isZero();
		mvc.perform(as(ALICE, get("/api/v1/notes")).param("q", "x".repeat(101))).andExpect(status().isBadRequest());
	}

	private int matches(String text) throws Exception {
		String json = mvc.perform(as(ALICE, get("/api/v1/notes")).param("q", text))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(json, "$.page.totalElements");
	}

	@Test
	@DisplayName("a list pages and sorts by an allowed field, and rejects any other")
	void listPagingAndSorting() throws Exception {
		created(ALICE, "B note");
		created(ALICE, "A note");
		created(ALICE, "C note");

		mvc.perform(as(ALICE, get("/api/v1/notes?sort=title,asc&size=2")))
			.andExpect(jsonPath("$.items[0].title").value("A note"))
			.andExpect(jsonPath("$.items[1].title").value("B note"))
			.andExpect(jsonPath("$.page.totalElements").value(3))
			.andExpect(jsonPath("$.page.totalPages").value(2));
		mvc.perform(as(ALICE, get("/api/v1/notes?sort=title,asc&size=2&page=1")))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].title").value("C note"));
		mvc.perform(as(ALICE, get("/api/v1/notes?sort=ownerId,asc"))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("sort"));
		mvc.perform(as(ALICE, get("/api/v1/notes?size=101"))).andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("deleted notes are not listed")
	void listSkipsDeleted() throws Exception {
		String id = created(ALICE, "Gone");
		created(ALICE, "Here");
		mvc.perform(as(ALICE, delete("/api/v1/notes/" + id))).andExpect(status().isNoContent());

		mvc.perform(as(ALICE, get("/api/v1/notes"))).andExpect(jsonPath("$.page.totalElements").value(1))
			.andExpect(jsonPath("$.items[0].title").value("Here"));
	}

	@Test
	@DisplayName("a note of another tenant is invisible: not listed, not readable, not changeable")
	void tenantIsolation() throws Exception {
		String foreign = noteOfAnotherTenant("Foreign");
		created(ALICE, "Mine");
		GrantedPermissions.grant(NotesPermissions.VIEW_ANY, NotesPermissions.UPDATE_ANY, NotesPermissions.DELETE_ANY);

		mvc.perform(as(ADMIN, get("/api/v1/notes"))).andExpect(jsonPath("$.page.totalElements").value(1));
		mvc.perform(as(ADMIN, get("/api/v1/notes/" + foreign))).andExpect(status().isNotFound());
		update(ADMIN, foreign, "Hijacked", 0).andExpect(status().isNotFound());
		mvc.perform(as(ADMIN, delete("/api/v1/notes/" + foreign))).andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("select title from note where tenant_id = unhex(?)", String.class,
				hex(OTHER_TENANT))).isEqualTo("Foreign");
	}

	@Test
	@DisplayName("restoring is refused without the permission")
	void restoreDenied() throws Exception {
		String id = created(ALICE, "Deleted");
		mvc.perform(as(ALICE, delete("/api/v1/notes/" + id))).andExpect(status().isNoContent());

		mvc.perform(as(ALICE, post("/api/v1/notes/" + id + "/restore"))).andExpect(status().isForbidden());

		assertThat(count("select count(*) from note where deleted_at is not null")).isEqualTo(1);
	}

	@Test
	@DisplayName("an administrator restores a deleted note, which then reappears and is audited")
	void restore() throws Exception {
		String id = created(ALICE, "Comes back");
		update(ALICE, id, "Comes back", 0).andExpect(status().isOk());
		mvc.perform(as(ALICE, delete("/api/v1/notes/" + id))).andExpect(status().isNoContent());
		GrantedPermissions.grant(NotesPermissions.RESTORE);

		mvc.perform(as(ADMIN, post("/api/v1/notes/" + id + "/restore"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id))
			.andExpect(jsonPath("$.title").value("Comes back"))
			.andExpect(jsonPath("$.version").value(org.hamcrest.Matchers.greaterThan(1)));

		mvc.perform(as(ALICE, get("/api/v1/notes/" + id))).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select hex(updated_by) from note", String.class))
			.isEqualTo(hex(ADMIN).toUpperCase());
		assertThat(count("select count(*) from audit_entry where action = 'notes.note.restored' and actor_id = unhex(?)",
				hex(ADMIN))).isEqualTo(1);
	}

	@Test
	@DisplayName("restoring a note that is not deleted, or does not exist, is 404 and writes no audit entry")
	void restoreUnknown() throws Exception {
		String live = created(ALICE, "Live");
		GrantedPermissions.grant(NotesPermissions.RESTORE);

		mvc.perform(as(ADMIN, post("/api/v1/notes/" + live + "/restore"))).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("notes.not_found"));
		mvc.perform(as(ADMIN, post("/api/v1/notes/" + UUID.randomUUID() + "/restore"))).andExpect(status().isNotFound());
		assertThat(count("select count(*) from audit_entry")).isZero();
	}

	@Test
	@DisplayName("a note of another tenant cannot be restored")
	void restoreOtherTenant() throws Exception {
		String foreign = noteOfAnotherTenant("Foreign");
		jdbc.update("update note set deleted_at = now(6)");
		GrantedPermissions.grant(NotesPermissions.RESTORE);

		mvc.perform(as(ADMIN, post("/api/v1/notes/" + foreign + "/restore"))).andExpect(status().isNotFound());

		assertThat(count("select count(*) from note where deleted_at is not null")).isEqualTo(1);
	}

	@Test
	@DisplayName("restoring a note whose title was taken meanwhile is 409")
	void restoreIntoDuplicate() throws Exception {
		String id = created(ALICE, "Contested");
		mvc.perform(as(ALICE, delete("/api/v1/notes/" + id))).andExpect(status().isNoContent());
		created(ALICE, "Contested");
		GrantedPermissions.grant(NotesPermissions.RESTORE);

		mvc.perform(as(ADMIN, post("/api/v1/notes/" + id + "/restore"))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("platform.duplicate"));

		assertThat(count("select count(*) from note where deleted_at is not null")).isEqualTo(1);
		assertThat(count("select count(*) from audit_entry where action = 'notes.note.restored'")).isZero();
	}

	@Test
	@DisplayName("someone who may restore notes lists everyone's deleted notes, filtered, sorted and paged, and no other tenant's")
	void listDeleted() throws Exception {
		String alpha = created(ALICE, "Alpha");
		String beta = created(ALICE, "Beta");
		String gamma = created(BOB, "Gamma");
		created(ALICE, "Still alive");
		mvc.perform(as(ALICE, delete("/api/v1/notes/" + alpha))).andExpect(status().isNoContent());
		mvc.perform(as(ALICE, delete("/api/v1/notes/" + beta))).andExpect(status().isNoContent());
		mvc.perform(as(BOB, delete("/api/v1/notes/" + gamma))).andExpect(status().isNoContent());
		String foreign = noteOfAnotherTenant("Foreign");
		jdbc.update("update note set deleted_at = now(6) where id = unhex(?)", hex(UUID.fromString(foreign)));
		GrantedPermissions.grant(NotesPermissions.RESTORE);

		mvc.perform(as(ADMIN, get("/api/v1/notes?deleted=true&sort=title,asc"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].title", org.hamcrest.Matchers.contains("Alpha", "Beta", "Gamma")))
			.andExpect(jsonPath("$.items[*].id", org.hamcrest.Matchers.contains(alpha, beta, gamma)))
			.andExpect(jsonPath("$.page.totalElements").value(3));
		mvc.perform(as(ADMIN, get("/api/v1/notes?deleted=true&sort=title,desc&size=2&page=1"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].title", org.hamcrest.Matchers.contains("Alpha")))
			.andExpect(jsonPath("$.page.totalPages").value(2));
		mvc.perform(as(ADMIN, get("/api/v1/notes?deleted=true&owner=" + BOB))).andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].title", org.hamcrest.Matchers.contains("Gamma")));
		mvc.perform(as(ADMIN, get("/api/v1/notes?deleted=true&q=BET"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].title", org.hamcrest.Matchers.contains("Beta")));
		mvc.perform(as(ADMIN, get("/api/v1/notes?deleted=true&sort=body,asc"))).andExpect(status().isBadRequest());
		// A listed note can be found and restored.
		mvc.perform(as(ADMIN, post("/api/v1/notes/" + beta + "/restore"))).andExpect(status().isOk());
		mvc.perform(as(ADMIN, get("/api/v1/notes?deleted=true"))).andExpect(jsonPath("$.page.totalElements").value(2));
	}

	@Test
	@DisplayName("the deleted notes are an administrative view: without the permission to restore, asking for them is 403")
	void listDeletedDenied() throws Exception {
		String id = created(ALICE, "Mine and deleted");
		mvc.perform(as(ALICE, delete("/api/v1/notes/" + id))).andExpect(status().isNoContent());
		GrantedPermissions.grant(NotesPermissions.VIEW_ANY);

		mvc.perform(as(ALICE, get("/api/v1/notes?deleted=true"))).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("platform.forbidden"));
		mvc.perform(as(ADMIN, get("/api/v1/notes?deleted=true"))).andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("a caller whose email address is not verified is refused every notes operation")
	void unverifiedCaller() throws Exception {
		String id = created(ALICE, "Mine");
		var unverified = jwt().jwt((token) -> token.subject(ALICE.toString()).claim("email_verified", false));

		mvc.perform(get("/api/v1/notes").with(unverified)).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("platform.email_not_verified"));
		mvc.perform(post("/api/v1/notes").with(unverified).contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"New\",\"body\":\"b\"}")).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("platform.email_not_verified"));
		mvc.perform(get("/api/v1/notes/" + id).with(unverified)).andExpect(status().isNotFound());
		mvc.perform(put("/api/v1/notes/" + id).with(unverified).contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"x\",\"body\":\"y\",\"version\":0}")).andExpect(status().isNotFound());
		mvc.perform(delete("/api/v1/notes/" + id).with(unverified)).andExpect(status().isNotFound());
		assertThat(count("select count(*) from note where deleted_at is null")).isEqualTo(1);
	}

	@Test
	@DisplayName("the retention purge removes notes deleted long ago and keeps live and recently deleted ones")
	void retentionPurge() throws Exception {
		String old = created(ALICE, "Deleted long ago");
		String recent = created(ALICE, "Deleted recently");
		created(ALICE, "Alive");
		String foreign = noteOfAnotherTenant("Foreign and old");
		jdbc.update("update note set deleted_at = now(6) - interval 40 day where id = unhex(?) or id = unhex(?)",
				hex(UUID.fromString(old)), hex(UUID.fromString(foreign)));
		jdbc.update("update note set deleted_at = now(6) - interval 5 day where id = unhex(?)",
				hex(UUID.fromString(recent)));

		int removed = retention.runOnce();

		assertThat(removed).isEqualTo(1);
		assertThat(count("select count(*) from note")).isEqualTo(3);
		assertThat(count("select count(*) from note where id = unhex(?)", hex(UUID.fromString(old)))).isZero();
		assertThat(count("select count(*) from note where id = unhex(?)", hex(UUID.fromString(foreign)))).isEqualTo(1);
	}

	@Test
	@DisplayName("an error never leaks the tenant or internal detail")
	void errorsAreClean() throws Exception {
		mvc.perform(as(ALICE, get("/api/v1/notes/" + UUID.randomUUID())))
			.andExpect(content().string(not(containsString("tenant"))))
			.andExpect(content().string(not(containsString("jdbc"))));
		assertThat(TenantContext.current()).isEmpty();
	}

}
