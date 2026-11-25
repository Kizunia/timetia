package com.kibootia.backend.platform.authorization;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kibootia.backend.GrantedPermissions;
import com.kibootia.backend.TestcontainersConfiguration;
import com.kibootia.backend.platform.tenancy.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization through the real context: {@code @RequiresPermission} enforced by method security, the decision point
 * from the {@code access} module, record checks, scopes, the system caller and the 403 the API returns. Each protected
 * operation has an allowed and a denied case (verification standard).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, GrantedPermissions.class, AuthorizationIT.Declares.class,
		AuthorizationIT.ProbeService.class, AuthorizationIT.ProbeController.class })
class AuthorizationIT {

	static final UUID ALICE = UUID.fromString("11111111-1111-7111-8111-111111111111");

	static final UUID BOB = UUID.fromString("22222222-2222-7222-8222-222222222222");

	static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-7000-8000-0000000000bb");

	static final String READ = "probe.items.read";

	static final String UPDATE = "probe.items.update";

	static final String UPDATE_ANY = "probe.items.update-any";

	static final String PURGE = "probe.items.purge";

	record Item(UUID ownerId, UUID tenantId) implements Owned, TenantBound {

	}

	@Component
	static class Declares implements PermissionProvider {

		@Override
		public List<Permission> permissions() {
			return List.of(Permission.forEveryUser(READ, "Read items"),
					Permission.forEveryUser(UPDATE, "Change own items"),
					Permission.restricted(UPDATE_ANY, "Change anyone's items"),
					Permission.restricted(PURGE, "Purge items"));
		}

	}

	@Service
	static class ProbeService {

		private final Authorization authorization;

		ProbeService(Authorization authorization) {
			this.authorization = authorization;
		}

		@RequiresPermission(READ)
		public String read() {
			return "read";
		}

		@RequiresPermission(PURGE)
		public String purge() {
			return "purged";
		}

		public String update(Item item) {
			this.authorization.require(UPDATE, item);
			return "updated";
		}

		public Scope scopeOfUpdates() {
			return this.authorization.requireScope(UPDATE);
		}

		public Scope scopeOfPurges() {
			return this.authorization.requireScope(PURGE);
		}

		@NoAuthorization("returns nothing about anyone")
		public String open() {
			return "open";
		}

		public UUID whoIsCalling() {
			return this.authorization.currentUserId();
		}

	}

	@RestController
	@RequestMapping("/api/v1/probe-auth")
	static class ProbeController {

		private final ProbeService service;

		ProbeController(ProbeService service) {
			this.service = service;
		}

		@GetMapping("/read")
		String read() {
			return this.service.read();
		}

		@GetMapping("/purge")
		String purge() {
			return this.service.purge();
		}

	}

	@Autowired
	ProbeService service;

	@Autowired
	Authorization authorization;

	@Autowired
	MockMvc mvc;

	@AfterEach
	void clear() {
		SecurityContextHolder.clearContext();
		GrantedPermissions.reset();
	}

	private static void signInAs(UUID user) {
		Jwt token = Jwt.withTokenValue("t")
			.header("alg", "none")
			.subject(user.toString())
			.claim("email_verified", true)
			.build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));
	}

	private static <T> T inTenant(java.util.concurrent.Callable<T> work) {
		return TenantContext.callAs(TenantContext.DEFAULT_TENANT, work);
	}

	@Test
	@DisplayName("a permission the user role holds is allowed")
	void allowedPermission() {
		signInAs(ALICE);

		assertThat(inTenant(service::read)).isEqualTo("read");
	}

	@Test
	@DisplayName("a permission nobody was granted is denied")
	void deniedPermission() {
		signInAs(ALICE);

		assertThatThrownBy(() -> inTenant(service::purge)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("a method marked @NoAuthorization needs no permission")
	void noAuthorizationMarker() {
		signInAs(ALICE);

		assertThat(inTenant(service::open)).isEqualTo("open");
	}

	@Test
	@DisplayName("a record the caller owns is allowed, someone else's is denied")
	void recordOwnership() {
		signInAs(ALICE);

		assertThat(inTenant(() -> service.update(new Item(ALICE, TenantContext.DEFAULT_TENANT)))).isEqualTo("updated");
		assertThatThrownBy(() -> inTenant(() -> service.update(new Item(BOB, TenantContext.DEFAULT_TENANT))))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("a record of another tenant is denied even to its owner")
	void recordOfAnotherTenant() {
		signInAs(ALICE);

		assertThatThrownBy(() -> inTenant(() -> service.update(new Item(ALICE, OTHER_TENANT))))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("a list is limited to the caller's own records without the -any permission")
	void listScope() {
		signInAs(ALICE);

		assertThat(inTenant(service::scopeOfUpdates)).isEqualTo(Scope.OWN);
		assertThatThrownBy(() -> inTenant(service::scopeOfPurges)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("the system may do anything, in the tenant it runs in, and says why")
	void systemCaller() {
		signInAs(ALICE);

		String result = inTenant(() -> authorization.runAsSystem("test: purge expired items", service::purge));

		assertThat(result).isEqualTo("purged");
		assertThatThrownBy(() -> inTenant(service::purge)).as("system authority ends with the block")
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("acting as the system needs a reason and a bound tenant")
	void systemNeedsReasonAndTenant() {
		assertThatIllegalArgumentException().isThrownBy(() -> inTenant(() -> {
			authorization.runAsSystem(" ", (Runnable) () -> {
			});
			return null;
		}));
		assertThatThrownBy(() -> authorization.runAsSystem("no tenant", () -> {
		})).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("with nobody signed in, everything is denied")
	void nobodySignedIn() {
		assertThatThrownBy(() -> inTenant(service::read)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("a token whose subject is not a user ID is denied")
	void subjectIsNotAUser() {
		Jwt token = Jwt.withTokenValue("t").header("alg", "none").subject("not-a-uuid").build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));

		assertThatThrownBy(() -> inTenant(service::read)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("a call outside any tenant is denied")
	void noTenant() {
		signInAs(ALICE);

		assertThatThrownBy(service::read).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("the current user ID comes from the token")
	void currentUserId() {
		signInAs(BOB);

		assertThat(inTenant(service::whoIsCalling)).isEqualTo(BOB);
	}

	@Test
	@DisplayName("over HTTP an allowed call is 200 and a denied one is 403 Problem Details")
	void overHttp() throws Exception {
		var alice = jwt().jwt((token) -> token.subject(ALICE.toString()).claim("email_verified", true));

		this.mvc.perform(get("/api/v1/probe-auth/read").with(alice)).andExpect(status().isOk());
		this.mvc.perform(get("/api/v1/probe-auth/purge").with(alice))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("platform.forbidden"))
			.andExpect(jsonPath("$.detail").value("Access is denied"));
	}

	@Test
	@DisplayName("over HTTP an unverified caller is refused with email_not_verified, not a plain forbidden")
	void overHttpUnverified() throws Exception {
		var unverified = jwt().jwt((token) -> token.subject(ALICE.toString()).claim("email_verified", false));

		this.mvc.perform(get("/api/v1/probe-auth/read").with(unverified))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("platform.email_not_verified"));
		this.mvc.perform(get("/api/v1/probe-auth/read").with(jwt().jwt((token) -> token.subject(ALICE.toString()))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("platform.email_not_verified"));
	}

	@Test
	@DisplayName("requireVerified lets a verified caller through and refuses an unverified one")
	void requireVerified() {
		signInAs(ALICE);
		inTenant(() -> {
			authorization.requireVerified();
			return null;
		});

		Jwt unverified = Jwt.withTokenValue("t").header("alg", "none").subject(ALICE.toString()).build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(unverified));
		assertThatThrownBy(() -> inTenant(() -> {
			authorization.requireVerified();
			return null;
		})).isInstanceOf(EmailNotVerifiedException.class);
	}

	@Test
	@DisplayName("over HTTP without a token is 401")
	void overHttpUnauthenticated() throws Exception {
		this.mvc.perform(get("/api/v1/probe-auth/read")).andExpect(status().isUnauthorized());
	}

}
