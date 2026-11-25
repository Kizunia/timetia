package com.kibootia.backend.platform.ratelimit;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.ratelimit.RateLimitProperties.Limit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

	static final UUID ALICE = UUID.fromString("11111111-1111-7111-8111-111111111111");

	static final UUID BOB = UUID.fromString("22222222-2222-7222-8222-222222222222");

	static RateLimiter limiter(Map<RateLimitCategory, Limit> limits) {
		// The overload that names the user does not need the current caller.
		return new RateLimiter(null, new RateLimitProperties(limits));
	}

	@Test
	@DisplayName("allows the capacity, then answers 429 with a Retry-After of at least a second")
	void refusesWhenTheBucketIsEmpty() {
		RateLimiter limiter = limiter(Map.of(RateLimitCategory.INVITATIONS, new Limit(2, Duration.ofHours(1))));

		limiter.acquire(RateLimitCategory.INVITATIONS, ALICE);
		limiter.acquire(RateLimitCategory.INVITATIONS, ALICE);

		assertThatThrownBy(() -> limiter.acquire(RateLimitCategory.INVITATIONS, ALICE))
			.isInstanceOfSatisfying(ApiException.class, (ex) -> {
				assertThat(ex.status().value()).isEqualTo(429);
				assertThat(ex.code()).isEqualTo("platform.rate_limited");
				assertThat(Long.parseLong(ex.headers().getFirst("Retry-After"))).isBetween(1L, 3600L);
			});
	}

	@Test
	@DisplayName("keeps a bucket per user, so one user's flood does not block another")
	void bucketsArePerUser() {
		RateLimiter limiter = limiter(Map.of(RateLimitCategory.INVITATIONS, new Limit(1, Duration.ofHours(1))));

		limiter.acquire(RateLimitCategory.INVITATIONS, ALICE);

		assertThatThrownBy(() -> limiter.acquire(RateLimitCategory.INVITATIONS, ALICE)).isInstanceOf(ApiException.class);
		assertThatCode(() -> limiter.acquire(RateLimitCategory.INVITATIONS, BOB)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("keeps a bucket per category, so invitations do not use up administrative actions")
	void bucketsArePerCategory() {
		RateLimiter limiter = limiter(Map.of(RateLimitCategory.INVITATIONS, new Limit(1, Duration.ofHours(1))));

		limiter.acquire(RateLimitCategory.INVITATIONS, ALICE);

		assertThatCode(() -> limiter.acquire(RateLimitCategory.ADMIN_ACTIONS, ALICE)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("a category that is not configured keeps its default")
	void defaultsApply() {
		RateLimiter limiter = limiter(Map.of());

		for (int i = 0; i < RateLimitCategory.VERIFICATION_RESEND.defaultCapacity(); i++) {
			limiter.acquire(RateLimitCategory.VERIFICATION_RESEND, ALICE);
		}

		assertThatThrownBy(() -> limiter.acquire(RateLimitCategory.VERIFICATION_RESEND, ALICE))
			.isInstanceOf(ApiException.class);
	}

	@Test
	@DisplayName("refuses a limit that cannot work")
	void refusesNonsenseLimits() {
		assertThatThrownBy(() -> limiter(Map.of(RateLimitCategory.UPLOADS, new Limit(0, Duration.ofMinutes(1)))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("uploads");
		assertThatThrownBy(() -> limiter(Map.of(RateLimitCategory.UPLOADS, new Limit(5, Duration.ZERO))))
			.isInstanceOf(IllegalStateException.class);
	}

}
