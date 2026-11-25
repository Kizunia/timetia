package com.kibootia.backend.identity.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import java.time.Instant;
import java.util.UUID;

import com.kibootia.backend.identity.application.UserProfile;
import com.kibootia.backend.platform.persistence.UuidBytes;

/** Converts between the persistence row and the application model by hand (ADR 0005). */
final class UserProfileMapper {

	/** The columns {@link #fromRow} reads; every native query selects exactly these. */
	static final String COLUMNS = """
			id, tenant_id, version, status, username, email, first_name, last_name, email_verified, theme,
			deleted_at, erasure_requested_at, created_at, updated_at, avatar_file_id""";

	private UserProfileMapper() {
	}

	static UserProfile toModel(UserProfileEntity entity) {
		return new UserProfile(entity.getId(), entity.getTenantId(), entity.getVersion(), entity.getStatus(),
				entity.getUsername(), entity.getEmail(), entity.getFirstName(), entity.getLastName(),
				entity.isEmailVerified(), entity.getTheme(), false, null, entity.getErasureRequestedAt(),
				entity.getCreatedAt(), entity.getUpdatedAt(), entity.getAvatarFileId());
	}

	static UserProfile fromRow(ResultSet rs, int row) throws SQLException {
		Instant deletedAt = instant(rs, "deleted_at");
		return new UserProfile(UuidBytes.fromBytes(rs.getBytes("id")), UuidBytes.fromBytes(rs.getBytes("tenant_id")),
				rs.getLong("version"), UserProfile.Status.valueOf(rs.getString("status")), rs.getString("username"),
				rs.getString("email"), rs.getString("first_name"), rs.getString("last_name"),
				rs.getBoolean("email_verified"), UserProfile.Theme.valueOf(rs.getString("theme")), deletedAt != null,
				deletedAt, instant(rs, "erasure_requested_at"), instant(rs, "created_at"), instant(rs, "updated_at"),
				uuid(rs, "avatar_file_id"));
	}

	private static UUID uuid(ResultSet rs, String column) throws SQLException {
		byte[] bytes = rs.getBytes(column);
		return (bytes != null) ? UuidBytes.fromBytes(bytes) : null;
	}

	/** DATETIME(6) columns hold UTC wall time, as Hibernate writes them. */
	private static Instant instant(ResultSet rs, String column) throws SQLException {
		LocalDateTime value = rs.getObject(column, LocalDateTime.class);
		return (value != null) ? value.toInstant(ZoneOffset.UTC) : null;
	}

}
