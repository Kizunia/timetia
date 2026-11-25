package com.kibootia.backend.platform.ratelimit;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Overrides for the limits in {@link RateLimitCategory}, for example
 * {@code kibootia.rate-limit.limits.invitations.capacity=5}. A category that is not mentioned keeps its default.
 *
 * @param limits the overridden limits by category
 */
@ConfigurationProperties("kibootia.rate-limit")
public record RateLimitProperties(Map<RateLimitCategory, Limit> limits) {

	public RateLimitProperties {
		limits = (limits == null) ? Map.of() : Map.copyOf(limits);
	}

	/**
	 * @param capacity how many actions a user may take in one period; also the largest burst
	 * @param period the time in which the whole capacity refills
	 */
	public record Limit(Integer capacity, Duration period) {

	}

	/** The limit in force for the category: the override where there is one, the default otherwise. */
	Map<RateLimitCategory, Limit> effective() {
		Map<RateLimitCategory, Limit> result = new EnumMap<>(RateLimitCategory.class);
		for (RateLimitCategory category : RateLimitCategory.values()) {
			Limit override = this.limits.get(category);
			int capacity = (override != null && override.capacity() != null) ? override.capacity()
					: category.defaultCapacity();
			Duration period = (override != null && override.period() != null) ? override.period()
					: category.defaultPeriod();
			if (capacity < 1 || period.isZero() || period.isNegative()) {
				throw new IllegalStateException("kibootia.rate-limit.limits." + category.name().toLowerCase()
						+ " needs a capacity of at least 1 and a positive period");
			}
			result.put(category, new Limit(capacity, period));
		}
		return result;
	}

}
