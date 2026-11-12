package com.kibootia.backend.identity;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.kibootia.backend.AdminApiTest;
import com.kibootia.backend.academic.application.RoleTemplateSeeder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The limits that keep an import bounded (Timetia ADR 0006): how often real imports may run, and how long one may take.
 * The base class's own limits are lifted for every other test, so these run in a context of their own.
 */
@SpringBootTest(properties = { "kibootia.identity.bootstrap-admin-email=" + AdminApiTest.ADMIN_EMAIL,
		"kibootia.identity.bootstrap-invite.enabled=false", "kibootia.rate-limit.limits.imports.capacity=2",
		"kibootia.rate-limit.limits.imports.period=PT1H", "kibootia.rate-limit.limits.admin-actions.capacity=10",
		"kibootia.rate-limit.limits.admin-actions.period=PT1H", "kibootia.identity.import.time-budget=PT0S",
		"kibootia.events.initial-backoff=PT0S" })
class UserImportLimitsIT extends AdminApiTest {

	@Autowired
	RoleTemplateSeeder seeder;

	@BeforeEach
	void seed() throws Exception {
		this.seeder.seed();
		ensureAdmin();
	}

	private ResultActions upload(boolean dryRun) throws Exception {
		return this.mvc.perform(multipart("/api/v1/user-imports")
			.file(new MockMultipartFile("file", "people.csv", "text/csv",
					"email,role\na@college.test,Faculty\nb@college.test,Faculty\n".getBytes(StandardCharsets.UTF_8)))
			.param("dryRun", String.valueOf(dryRun))
			.with(admin()));
	}

	@Test
	@DisplayName("real imports are limited per administrator and say when to try again; a dry run uses the general allowance for administration")
	void rateLimit() throws Exception {
		upload(false).andExpect(status().isOk());
		upload(false).andExpect(status().isOk());
		upload(false).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));

		// A dry run does not count against the limit on real imports.
		upload(true).andExpect(status().isOk());
	}

	@Test
	@DisplayName("a run that has used its time reports the people it did not reach as not processed, and changes nothing for them")
	void timeBudget() throws Exception {
		// A dry run, so this test does not use the allowance of real imports that the other test counts.
		upload(true).andExpect(status().isOk())
			.andExpect(jsonPath("$.counts.notProcessed").value(2))
			.andExpect(jsonPath("$.lines[0].code").value("time_budget_exceeded"))
			.andExpect(jsonPath("$.counts.added").value(0));
		assertThat(count("select count(*) from user_profile")).isEqualTo(1);
		assertThat(this.keycloak.countCalls("createUser")).isZero();
	}

}
