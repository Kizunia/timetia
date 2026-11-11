package com.kibootia.backend.platform.tenancy;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.ServletException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

	static final UUID OTHER = UUID.fromString("00000000-0000-7000-8000-0000000000aa");

	@AfterEach
	void clear() {
		SecurityContextHolder.clearContext();
		assertThat(TenantContext.current()).as("no tenant leaks out of a test").isEmpty();
	}

	@Test
	@DisplayName("a tenant is bound only inside runAs and the previous binding is restored")
	void runAsRestoresThePreviousBinding() {
		TenantContext.runAs(TenantContext.DEFAULT_TENANT, () -> {
			TenantContext.runAs(OTHER, () -> assertThat(TenantContext.require()).isEqualTo(OTHER));
			assertThat(TenantContext.require()).isEqualTo(TenantContext.DEFAULT_TENANT);
		});
		assertThat(TenantContext.current()).isEmpty();
	}

	@Test
	@DisplayName("the binding ends when the work throws")
	void bindingEndsOnFailure() {
		assertThatThrownBy(() -> TenantContext.runAs(OTHER, () -> {
			throw new IllegalStateException("boom");
		})).hasMessage("boom");
		assertThat(TenantContext.current()).isEmpty();
	}

	@Test
	@DisplayName("require fails without a binding")
	void requireFailsWithoutBinding() {
		assertThatThrownBy(TenantContext::require).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("the no-tenant marker and null cannot be bound")
	void rejectsUnusableTenants() {
		assertThatIllegalArgumentException().isThrownBy(() -> TenantContext.bind(null));
		assertThatIllegalArgumentException().isThrownBy(() -> TenantContext.bind(TenantContext.NO_TENANT));
	}

	@Test
	@DisplayName("the filter binds the resolved tenant for an authenticated request and unbinds afterwards")
	void filterBindsForAuthenticatedRequests() throws Exception {
		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", "n/a", "ROLE_X"));
		AtomicReference<UUID> seen = new AtomicReference<>();
		MockFilterChain chain = new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
			@Override
			protected void service(jakarta.servlet.http.HttpServletRequest req,
					jakarta.servlet.http.HttpServletResponse res) {
				seen.set(TenantContext.current().orElse(null));
			}
		});

		new TenantContextFilter((authentication) -> OTHER).doFilter(new MockHttpServletRequest(),
				new MockHttpServletResponse(), chain);

		assertThat(seen.get()).isEqualTo(OTHER);
		assertThat(TenantContext.current()).isEmpty();
	}

	@Test
	@DisplayName("the filter binds nothing for an unauthenticated request")
	void filterBindsNothingWithoutAuthentication() throws Exception {
		AtomicReference<Object> seen = new AtomicReference<>("untouched");
		MockFilterChain chain = new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
			@Override
			protected void service(jakarta.servlet.http.HttpServletRequest req,
					jakarta.servlet.http.HttpServletResponse res) {
				seen.set(TenantContext.current().orElse(null));
			}
		});

		new TenantContextFilter((authentication) -> OTHER).doFilter(new MockHttpServletRequest(),
				new MockHttpServletResponse(), chain);

		assertThat(seen.get()).isNull();
	}

	@Test
	@DisplayName("the filter unbinds when the rest of the chain fails")
	void filterUnbindsOnFailure() {
		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", "n/a", "ROLE_X"));
		MockFilterChain chain = new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
			@Override
			protected void service(jakarta.servlet.http.HttpServletRequest req,
					jakarta.servlet.http.HttpServletResponse res) throws ServletException {
				throw new ServletException("downstream failure");
			}
		});

		assertThatThrownBy(() -> new TenantContextFilter((authentication) -> OTHER)
			.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain))
			.isInstanceOf(ServletException.class);
	}

}
