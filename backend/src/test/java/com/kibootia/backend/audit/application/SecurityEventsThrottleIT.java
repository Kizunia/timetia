package com.kibootia.backend.audit.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.kibootia.backend.AdminApiTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A flood of refused requests from one user cannot fill the audit log: only the first few in a minute are recorded
 * (Timetia ADR 0005). The answer to the caller is the same whether the event was recorded or dropped.
 */
@TestPropertySource(properties = "kibootia.audit.security-events.max-per-minute=3")
class SecurityEventsThrottleIT extends AdminApiTest {

	@Test
	@DisplayName("one user's refused requests are recorded up to the limit, and every one is still answered 403")
	void throttlesPerUser() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		signIn(BOB);

		for (int i = 0; i < 6; i++) {
			get(user(ALICE), "/api/v1/roles").andExpect(status().isForbidden());
		}
		assertThat(count("select count(*) from audit_entry where action = 'security.access.denied' and actor_id = unhex(?)",
				hex(ALICE))).isEqualTo(3);

		// Another user has their own allowance.
		get(user(BOB), "/api/v1/roles").andExpect(status().isForbidden());
		assertThat(count("select count(*) from audit_entry where action = 'security.access.denied' and actor_id = unhex(?)",
				hex(BOB))).isEqualTo(1);
	}

}
