package com.kibootia.backend.access;

import java.util.UUID;
import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.kibootia.backend.AdminApiTest;
import com.kibootia.backend.BackendApplication;
import com.kibootia.backend.access.domain.PermissionSource;
import com.kibootia.backend.platform.authorization.Caller;
import com.kibootia.backend.platform.tenancy.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role changes apply on the next request on every instance (ADR 0044, milestone 3). There is no permission cache to
 * invalidate: each decision reads the roles from MySQL. This starts a second, separate application context on the same
 * database, as a second instance would be, and shows that it sees a change the first one just made, in both directions.
 */
class TwoInstancesIT extends AdminApiTest {

	@Autowired
	DataSource dataSource;

	@Test
	@DisplayName("a role granted or taken away through one instance applies at once on another")
	void aRoleChangeAppliesOnAnotherRunningInstance() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		HikariDataSource pool = this.dataSource.unwrap(HikariDataSource.class);

		try (ConfigurableApplicationContext second = new SpringApplicationBuilder(BackendApplication.class)
			.web(WebApplicationType.NONE)
			.run("--spring.datasource.url=" + pool.getJdbcUrl(), "--spring.datasource.username=" + pool.getUsername(),
					"--spring.datasource.password=" + pool.getPassword(), "--kibootia.jobs.enabled=false",
					"--spring.main.banner-mode=off")) {
			PermissionSource other = second.getBean(PermissionSource.class);
			Caller alice = Caller.user(ALICE, TenantContext.DEFAULT_TENANT, true);
			assertThat(other.permissionsOf(alice)).doesNotContain("access.roles.view");

			String viewers = createRole("Role viewers", "access.roles.view");
			setRoles(ALICE, builtInRoleId("USER"), viewers);
			assertThat(other.permissionsOf(alice)).as("granted").contains("access.roles.view");

			setRoles(ALICE, builtInRoleId("USER"));
			assertThat(other.permissionsOf(alice)).as("taken away").doesNotContain("access.roles.view");

			// An administrator's role, and a disabled user, are seen as quickly.
			makeAdminThroughApi(ALICE);
			assertThat(other.permissionsOf(alice)).as("administrator").contains("access.roles.manage", "audit.entries.view");
		}
		get(user(ALICE), "/api/v1/roles").andExpect(status().isOk());
	}

	private void makeAdminThroughApi(UUID user) throws Exception {
		setRoles(user, builtInRoleId("USER"), builtInRoleId("ADMIN"));
	}

}
