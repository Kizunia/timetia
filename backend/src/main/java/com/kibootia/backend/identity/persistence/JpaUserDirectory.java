package com.kibootia.backend.identity.persistence;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.kibootia.backend.identity.application.UserProfile;
import com.kibootia.backend.platform.directory.UserDirectory;
import com.kibootia.backend.platform.directory.UserSummary;
import com.kibootia.backend.platform.persistence.UuidBytes;
import com.kibootia.backend.platform.tenancy.TenantContext;

/**
 * Answers other modules' questions about users (ADR 0039) from the profile table. It reads deleted profiles too, so an
 * administrator who deleted a user still sees their name in the audit log until the user is erased; after that the row
 * is gone and the viewer shows "deleted user".
 */
@Component
class JpaUserDirectory implements UserDirectory {

	private final JdbcClient jdbc;

	JpaUserDirectory(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Map<UUID, UserSummary> find(Collection<UUID> ids) {
		Map<UUID, UserSummary> found = new HashMap<>();
		if (ids.isEmpty()) {
			return found;
		}
		this.jdbc.sql("select " + UserProfileMapper.COLUMNS
				+ " from user_profile where tenant_id = :tenant and id in (:ids)")
			.param("tenant", UuidBytes.toBytes(TenantContext.require()))
			.param("ids", ids.stream().map(UuidBytes::toBytes).toList())
			.query(UserProfileMapper::fromRow)
			.list()
			.forEach((profile) -> found.put(profile.id(),
					new UserSummary(profile.id(), profile.displayName(), stateOf(profile))));
		return found;
	}

	private static UserSummary.State stateOf(UserProfile profile) {
		if (profile.deleted()) {
			return UserSummary.State.DELETED;
		}
		return switch (profile.status()) {
			case INVITED -> UserSummary.State.INVITED;
			case ACTIVE -> UserSummary.State.ACTIVE;
			case DISABLED -> UserSummary.State.DISABLED;
		};
	}

}
