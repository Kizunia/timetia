package com.kibootia.backend.platform.ratelimit;

import java.time.Duration;

/**
 * The kinds of sensitive action the in-app limiter protects (API conventions, threat model). Each has its own bucket
 * per user, so a flood of one kind does not use up another's allowance. The limits are the defaults; deployments change
 * them with {@code kibootia.rate-limit.limits.<category>.capacity} and {@code .period}.
 */
public enum RateLimitCategory {

	/** Inviting users: each one sends an email through Keycloak. */
	INVITATIONS(20, Duration.ofHours(1)),

	/** Administrative changes to users, roles and assignments. */
	ADMIN_ACTIONS(120, Duration.ofMinutes(1)),

	/** Asking for the verification email again. */
	VERIFICATION_RESEND(3, Duration.ofHours(1)),

	/** Uploading files; the storage module (milestone 4) applies it. */
	UPLOADS(30, Duration.ofMinutes(10));

	private final int defaultCapacity;

	private final Duration defaultPeriod;

	RateLimitCategory(int defaultCapacity, Duration defaultPeriod) {
		this.defaultCapacity = defaultCapacity;
		this.defaultPeriod = defaultPeriod;
	}

	int defaultCapacity() {
		return this.defaultCapacity;
	}

	Duration defaultPeriod() {
		return this.defaultPeriod;
	}

}
