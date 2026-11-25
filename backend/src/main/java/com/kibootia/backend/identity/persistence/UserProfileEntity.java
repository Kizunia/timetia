package com.kibootia.backend.identity.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.kibootia.backend.identity.application.UserProfile;
import com.kibootia.backend.identity.application.UserProfileStore.TokenFacts;
import com.kibootia.backend.platform.persistence.AssignedIdEntity;

/**
 * The table row of a user profile. Persistence only: it never leaves this package (ADR 0004). The key is the Keycloak
 * user ID, given rather than generated; the tenant, version, audit columns and soft delete come from the platform base
 * types. Status and theme are plain strings so schema validation needs no native enum type.
 */
@Entity
@Table(name = "user_profile")
class UserProfileEntity extends AssignedIdEntity {

	@Column(name = "status", nullable = false, length = 20)
	private String status;

	@Column(name = "username", length = 255)
	private String username;

	@Column(name = "email", length = 320)
	private String email;

	@Column(name = "first_name", length = 255)
	private String firstName;

	@Column(name = "last_name", length = 255)
	private String lastName;

	@Column(name = "email_verified", nullable = false)
	private boolean emailVerified;

	@Column(name = "theme", nullable = false, length = 20)
	private String theme;

	@Column(name = "name_synced_at", nullable = false)
	private Instant nameSyncedAt;

	@Column(name = "erasure_requested_at")
	private Instant erasureRequestedAt;

	@Column(name = "avatar_file_id", columnDefinition = "binary(16)")
	private UUID avatarFileId;

	protected UserProfileEntity() {
	}

	UserProfileEntity(UUID id, UserProfile.Status status, TokenFacts facts, Instant syncedAt) {
		super(id);
		this.status = status.name();
		this.theme = UserProfile.Theme.SYSTEM.name();
		copy(facts, syncedAt);
	}

	UserProfile.Status getStatus() {
		return UserProfile.Status.valueOf(this.status);
	}

	String getUsername() {
		return this.username;
	}

	String getEmail() {
		return this.email;
	}

	String getFirstName() {
		return this.firstName;
	}

	String getLastName() {
		return this.lastName;
	}

	boolean isEmailVerified() {
		return this.emailVerified;
	}

	UserProfile.Theme getTheme() {
		return UserProfile.Theme.valueOf(this.theme);
	}

	Instant getNameSyncedAt() {
		return this.nameSyncedAt;
	}

	Instant getErasureRequestedAt() {
		return this.erasureRequestedAt;
	}

	UUID getAvatarFileId() {
		return this.avatarFileId;
	}

	void setAvatarFileId(UUID avatarFileId) {
		this.avatarFileId = avatarFileId;
	}

	void setStatus(UserProfile.Status status) {
		this.status = status.name();
	}

	void setTheme(UserProfile.Theme theme) {
		this.theme = theme.name();
	}

	/** Whether the token says anything the profile does not already say. */
	boolean differsFrom(TokenFacts facts) {
		return !java.util.Objects.equals(this.username, facts.username())
				|| !java.util.Objects.equals(this.email, facts.email())
				|| !java.util.Objects.equals(this.firstName, facts.firstName())
				|| !java.util.Objects.equals(this.lastName, facts.lastName()) || this.emailVerified != facts.emailVerified();
	}

	void copy(TokenFacts facts, Instant syncedAt) {
		this.username = facts.username();
		this.email = facts.email();
		this.firstName = facts.firstName();
		this.lastName = facts.lastName();
		this.emailVerified = facts.emailVerified();
		this.nameSyncedAt = syncedAt;
	}

}
