package com.kibootia.backend.access;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.kibootia.backend.access.domain.PermissionSource;
import com.kibootia.backend.platform.authorization.Permission;
import com.kibootia.backend.platform.authorization.PermissionProvider;
import com.kibootia.backend.platform.authorization.RequiresPermission;

import static org.assertj.core.api.Assertions.assertThat;

class AccessConfigurationTest {

	static class Declares implements PermissionProvider {

		@Override
		public List<Permission> permissions() {
			return List.of(Permission.forEveryUser("probe.items.read", "Read items"));
		}

	}

	static class DeclaresPolicyDecided implements PermissionProvider {

		@Override
		public List<Permission> permissions() {
			return List.of(Permission.policyDecided("probe.items.review", "Review items"));
		}

	}

	/** A permission only a record check can satisfy, protected by the permission-only annotation: a mistake. */
	static class FlatCheckOfPolicyDecided {

		@RequiresPermission("probe.items.review")
		public void review() {
		}

	}

	static class GoodService {

		@RequiresPermission("probe.items.read")
		public void read() {
		}

	}

	static class TypoService {

		@RequiresPermission("probe.items.raed")
		public void read() {
		}

	}

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withBean(Declares.class)
		.withBean(PermissionSource.class, () -> (caller) -> Set.of())
		.withUserConfiguration(AccessConfiguration.class);

	@Test
	@DisplayName("starts when every required permission is declared")
	void startsWithDeclaredPermissions() {
		this.runner.withBean(GoodService.class).run((context) -> assertThat(context).hasNotFailed());
	}

	@Test
	@DisplayName("fails at startup, naming the method, when @RequiresPermission has a typo")
	void failsOnUndeclaredPermission() {
		this.runner.withBean(TypoService.class).run((context) -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).hasStackTraceContaining("TypoService.read")
				.hasStackTraceContaining("probe.items.raed");
		});
	}

	@Test
	@DisplayName("fails at startup, naming the method, when @RequiresPermission names a policy-decided permission")
	void failsOnPolicyDecidedPermissionInTheFlatAnnotation() {
		this.runner.withBean("policyDecided", DeclaresPolicyDecided.class).withBean(FlatCheckOfPolicyDecided.class).run((context) -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).hasStackTraceContaining("FlatCheckOfPolicyDecided.review")
				.hasStackTraceContaining("probe.items.review")
				.hasStackTraceContaining("policy-decided");
		});
	}

	@Test
	@DisplayName("starts with a policy-decided permission that nothing protects with the flat annotation")
	void startsWithPolicyDecidedPermissionUsedOnlyOnRecords() {
		this.runner.withBean("policyDecided", DeclaresPolicyDecided.class).run((context) -> assertThat(context).hasNotFailed());
	}

	@Test
	@DisplayName("fails at startup when two modules declare the same permission")
	void failsOnDuplicatePermission() {
		this.runner.withBean("again", Declares.class).run((context) -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).hasStackTraceContaining("probe.items.read");
		});
	}

}
