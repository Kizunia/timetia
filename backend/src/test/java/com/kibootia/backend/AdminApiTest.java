package com.kibootia.backend;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.kibootia.backend.platform.fixtures.WidgetFixtures;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The base of the integration tests that drive the identity, access and audit APIs through the real stack: filter chain,
 * request gate, method security, decision point, MySQL, with a fake in place of Keycloak. Every subclass has the same
 * configuration, so they share one Spring context and one MySQL container.
 *
 * <p>The base knows no feature module beyond the core ones: it imports {@link WidgetFixtures} for permissions to work
 * with and a protected URL, and never names the notes module. A test that creates notes cleans the note table itself.
 *
 * <p>{@link #ADMIN} is the bootstrap administrator: the first request they make with a verified email address grants the
 * administrator role, exactly as in production ({@code kibootia.identity.bootstrap-admin-email}). Everyone else starts
 * with the built-in user role only.
 */
@SpringBootTest(properties = { "kibootia.identity.bootstrap-admin-email=" + AdminApiTest.ADMIN_EMAIL,
		"kibootia.jobs.soft-delete-retention-days=30",
		// The limiter has its own test; here a long run of actions by one administrator must not trip it.
		"kibootia.rate-limit.limits.admin-actions.capacity=100000", "kibootia.rate-limit.limits.invitations.capacity=100000",
		"kibootia.events.initial-backoff=PT0S", "kibootia.events.max-backoff=PT0S", "kibootia.events.max-attempts=4",
		"kibootia.events.stale-after=PT1H" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, WidgetFixtures.class })
public abstract class AdminApiTest {

	public static final String ADMIN_EMAIL = "admin@kibootia.test";

	public static final UUID ADMIN = UUID.fromString("a0000000-0000-7000-8000-000000000001");

	public static final UUID SECOND_ADMIN = UUID.fromString("a0000000-0000-7000-8000-000000000002");

	public static final UUID ALICE = UUID.fromString("11111111-1111-7111-8111-111111111111");

	public static final UUID BOB = UUID.fromString("22222222-2222-7222-8222-222222222222");

	public static final UUID CAROL = UUID.fromString("33333333-3333-7333-8333-333333333333");

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected FakeIdentityProvider keycloak;

	@Autowired
	protected FakeObjectStorage objectStorage;

	@BeforeEach
	@AfterEach
	protected void cleanDatabase() {
		Await.until("in-flight deliveries to finish", () -> this.jdbc.queryForObject(
				"select count(*) from event_publication where completion_date is null and status in "
						+ "('PUBLISHED', 'PROCESSING', 'RESUBMITTED')", Integer.class) == 0);
		this.jdbc.update("delete from role_assignment");
		this.jdbc.update("delete from role_permission");
		this.jdbc.update("delete from role_permission_seed");
		this.jdbc.update("delete from role");
		this.jdbc.update("delete from stored_file");
		this.jdbc.update("delete from user_profile");
		this.jdbc.update("delete from audit_entry");
		this.jdbc.update("delete from event_publication");
		this.jdbc.update("delete from processed_event");
		this.keycloak.reset();
		this.objectStorage.clear();
	}

	// --- who is calling ---------------------------------------------------------------------------------------------

	protected static RequestPostProcessor token(UUID user, String email, boolean emailVerified) {
		return jwt().jwt((token) -> token.subject(user.toString())
			.claim("email", email)
			.claim("email_verified", emailVerified)
			.claim("preferred_username", email.substring(0, email.indexOf('@')))
			.claim("given_name", "Given")
			.claim("family_name", user.toString().substring(0, 4)));
	}

	protected static RequestPostProcessor admin() {
		return token(ADMIN, ADMIN_EMAIL, true);
	}

	protected static RequestPostProcessor user(UUID user) {
		return token(user, user.toString().substring(0, 8) + "@kibootia.test", true);
	}

	protected static RequestPostProcessor unverified(UUID user) {
		return token(user, user.toString().substring(0, 8) + "@kibootia.test", false);
	}

	// --- requests ---------------------------------------------------------------------------------------------------

	protected ResultActions get(RequestPostProcessor who, String path) throws Exception {
		return this.mvc.perform(MockMvcRequestBuilders.get(path).with(who));
	}

	protected ResultActions post(RequestPostProcessor who, String path, String body) throws Exception {
		MockHttpServletRequestBuilder request = MockMvcRequestBuilders.post(path).with(who);
		return this.mvc.perform(body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	protected ResultActions put(RequestPostProcessor who, String path, String body) throws Exception {
		return this.mvc
			.perform(MockMvcRequestBuilders.put(path).with(who).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	protected ResultActions delete(RequestPostProcessor who, String path) throws Exception {
		return this.mvc.perform(MockMvcRequestBuilders.delete(path).with(who));
	}

	// --- building a situation ---------------------------------------------------------------------------------------

	/** Makes the bootstrap administrator exist by letting them make their first request. */
	protected void ensureAdmin() throws Exception {
		get(admin(), "/api/v1/me").andExpect(status().isOk());
	}

	/** Lets the user make their first request, so their profile exists, and returns their ID. */
	protected UUID signIn(UUID user) throws Exception {
		get(user(user), "/api/v1/me").andExpect(status().isOk());
		return user;
	}

	protected static String read(ResultActions result, String path) throws Exception {
		Object value = JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
		return String.valueOf(value);
	}

	/** The ID of a built-in role: {@code ADMIN} or {@code USER}. */
	protected String builtInRoleId(String builtIn) throws Exception {
		String json = get(admin(), "/api/v1/roles?size=100").andReturn().getResponse().getContentAsString();
		java.util.List<String> ids = JsonPath.read(json, "$.items[?(@.builtIn == '" + builtIn + "')].id");
		return ids.get(0);
	}

	/** Creates a role as the administrator and returns its ID. */
	protected String createRole(String name, String... permissions) throws Exception {
		String list = String.join("\",\"", permissions);
		String body = "{\"name\":\"" + name + "\",\"description\":\"Test role\",\"permissions\":["
				+ (permissions.length == 0 ? "" : "\"" + list + "\"") + "]}";
		return read(post(admin(), "/api/v1/roles", body).andExpect(status().isCreated()), "$.id");
	}

	/** Sets the roles of a user as the administrator. */
	protected void setRoles(UUID user, String... roleIds) throws Exception {
		String body = "{\"roleIds\":[" + (roleIds.length == 0 ? "" : "\"" + String.join("\",\"", roleIds) + "\"") + "]}";
		put(admin(), "/api/v1/users/" + user + "/roles", body).andExpect(status().isOk());
	}

	/**
	 * Makes the user an administrator without going through the API, so a test can set a situation up. The user must
	 * have made a first request, so the built-in roles exist.
	 */
	protected void makeAdministrator(UUID user) {
		byte[] adminRole = this.jdbc.queryForObject("select id from role where built_in = 'ADMIN' and deleted_at is null",
				byte[].class);
		this.jdbc.update("""
				insert ignore into role_assignment (tenant_id, user_id, role_id, assigned_at, assigned_by)
				values (unhex('00000000000070008000000000000001'), unhex(?), ?, utc_timestamp(6), unhex(?))
				""", hex(user), adminRole, hex(user));
	}

	protected int count(String sql, Object... args) {
		return this.jdbc.queryForObject(sql, Integer.class, args);
	}

	protected static String hex(UUID id) {
		return id.toString().replace("-", "");
	}

}
