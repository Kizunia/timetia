package com.kibootia.backend.access.application;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.access.AccessPermissions;
import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.authorization.RequiresPermission;
import com.kibootia.backend.platform.jobs.JobsProperties;
import com.kibootia.backend.platform.jobs.RetentionPurgeable;

/** Permanently removes roles that were deleted longer ago than the retention period (ADR 0034). */
@Component
class RoleRetention implements RetentionPurgeable {

	private final RoleStore roles;

	private final Clock clock;

	private final JobsProperties properties;

	RoleRetention(RoleStore roles, Clock clock, JobsProperties properties) {
		this.roles = roles;
		this.clock = clock;
		this.properties = properties;
	}

	@Override
	@NoAuthorization("returns a constant label for the logs")
	public String name() {
		return "deleted-roles";
	}

	@Override
	@Transactional
	@RequiresPermission(AccessPermissions.MANAGE_ROLES)
	public int purge() {
		return this.roles.purgeDeletedBefore(
				this.clock.instant().minus(Duration.ofDays(this.properties.softDeleteRetentionDays())));
	}

}
