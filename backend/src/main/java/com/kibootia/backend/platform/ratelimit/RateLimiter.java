package com.kibootia.backend.platform.ratelimit;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.security.SecurityEvents;

/**
 * The in-app rate limiter for sensitive actions (API conventions). One token bucket per user and category, held in
 * memory in a size-bounded cache, so it protects each application instance separately; production also limits at the
 * edge. A bucket that has refilled completely is the same as no bucket, so idle ones expire and the memory stays
 * bounded however many users there are.
 *
 * <p>Call {@link #acquire} in the use case, after its authorization check and before the work, so an unauthorized
 * caller does not use up anyone's allowance.
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimiter {

	/** The most users one category tracks at once. The least recently used are dropped first. */
	static final int MAX_TRACKED_USERS = 10_000;

	private final Authorization authorization;

	private final SecurityEvents events;

	private final Map<RateLimitCategory, Limiter> limiters = new EnumMap<>(RateLimitCategory.class);

	@Autowired
	RateLimiter(Authorization authorization, RateLimitProperties properties, ObjectProvider<SecurityEvents> events) {
		this(authorization, properties, events.getIfAvailable(() -> SecurityEvents.NONE));
	}

	RateLimiter(Authorization authorization, RateLimitProperties properties) {
		this(authorization, properties, SecurityEvents.NONE);
	}

	RateLimiter(Authorization authorization, RateLimitProperties properties, SecurityEvents events) {
		this.authorization = authorization;
		this.events = events;
		properties.effective().forEach((category, limit) -> this.limiters.put(category,
				new Limiter(limit.capacity(), limit.period())));
	}

	/**
	 * Takes one token from the current user's bucket for the category.
	 *
	 * @throws ApiException 429 with {@code Retry-After} when the bucket is empty
	 */
	public void acquire(RateLimitCategory category) {
		acquire(category, this.authorization.currentUserId());
	}

	/** As {@link #acquire(RateLimitCategory)}, for work done on behalf of a named user. */
	public void acquire(RateLimitCategory category, UUID user) {
		ConsumptionProbe probe = this.limiters.get(category).bucketOf(user).tryConsumeAndReturnRemaining(1);
		if (!probe.isConsumed()) {
			this.events.rateLimited(user, category.name().toLowerCase(Locale.ROOT).replace('_', '-'));
			throw ApiException.tooManyRequests("Too many requests. Wait a moment and try again.",
					Duration.ofNanos(probe.getNanosToWaitForRefill()));
		}
	}

	private static final class Limiter {

		private final int capacity;

		private final Duration period;

		private final Cache<UUID, Bucket> buckets;

		Limiter(int capacity, Duration period) {
			this.capacity = capacity;
			this.period = period;
			this.buckets = Caffeine.newBuilder().maximumSize(MAX_TRACKED_USERS).expireAfterAccess(period).build();
		}

		Bucket bucketOf(UUID user) {
			return this.buckets.get(user, (key) -> Bucket.builder()
				.addLimit(Bandwidth.builder().capacity(this.capacity).refillGreedy(this.capacity, this.period).build())
				.build());
		}

	}

}
