package com.kibootia.backend.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.identity.IdentityPermissions;
import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.authorization.RequiresPermission;
import com.kibootia.backend.platform.jobs.JobsProperties;
import com.kibootia.backend.platform.jobs.RetentionPurgeable;

/**
 * Starts erasing users who were deleted longer ago than the retention period (ADR 0034). It only begins the erasure,
 * exactly as an administrator's request does: the Keycloak account, the profile and what other modules hold are removed
 * by durable handlers that retry until they succeed. A user whose erasure has begun is not picked again.
 */
@Component
class UserRetention implements RetentionPurgeable {

	private final UserProfileStore profiles;

	private final ErasureStarter erasures;

	private final Clock clock;

	private final JobsProperties properties;

	UserRetention(UserProfileStore profiles, ErasureStarter erasures, Clock clock, JobsProperties properties) {
		this.profiles = profiles;
		this.erasures = erasures;
		this.clock = clock;
		this.properties = properties;
	}

	@Override
	@NoAuthorization("returns a constant label for the logs")
	public String name() {
		return "deleted-users";
	}

	@Override
	@Transactional
	@RequiresPermission(IdentityPermissions.ERASE_USERS)
	public int purge() {
		List<UserProfile> expired = this.profiles.findExpiredDeleted(
				this.clock.instant().minus(Duration.ofDays(this.properties.softDeleteRetentionDays())));
		expired.forEach((profile) -> this.erasures.begin(profile.id()));
		return expired.size();
	}

}
