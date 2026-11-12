package com.kibootia.backend.identity.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Where user profiles are kept. The use cases depend on this port, never on JPA or SQL (ADR 0003). Every method acts on
 * the current tenant. Unlike most stores, {@link #find} and {@link #search} can see soft-deleted profiles: an
 * administrator restores or erases a deleted user, and the request gate must tell a deleted user from a new one.
 */
public interface UserProfileStore {

	/** The profile, including a deleted one. */
	Optional<UserProfile> find(UUID id);

	/**
	 * Creates the profile and flushes, so a profile created at the same moment by another request is reported by this
	 * call as a {@link org.springframework.dao.DataIntegrityViolationException} instead of at commit.
	 */
	UserProfile insert(NewProfile profile);

	/**
	 * Copies what the token says into the profile, once per token: only when the token is newer than the last copy
	 * and something differs. An invited user becomes active on the first sign-in.
	 *
	 * @return the profile after the refresh
	 */
	UserProfile refresh(UUID id, TokenFacts facts, Instant tokenIssuedAt);

	void updateStatus(UUID id, UserProfile.Status status);

	void updateTheme(UUID id, UserProfile.Theme theme);

	/**
	 * Makes the file the user's avatar, or removes the avatar when {@code fileId} is null, and returns the avatar the user
	 * had before (null when there was none). It holds a lock on the profile for the change, so two uploads at the same
	 * moment leave exactly one avatar and each hands back the one it replaced, which the caller then discards.
	 */
	UUID replaceAvatar(UUID id, UUID fileId);

	/** Marks a live profile deleted. It stays until it is erased. */
	void softDelete(UUID id);

	/** Brings a deleted profile back. Returns false when there is no deleted profile that erasure has not begun on. */
	boolean restore(UUID id, UUID actor);

	/** Records that erasure began, so the retention job does not start it again. */
	void markErasureRequested(UUID id, Instant at);

	/** Removes the row for good. */
	void erase(UUID id);

	/** A page of profiles matching the filter, deleted ones only when the filter asks for them. */
	Page<UserProfile> search(UserFilter filter, Pageable pageable);

	/** Deleted profiles whose retention period has passed and whose erasure has not begun. */
	List<UserProfile> findExpiredDeleted(Instant deletedBefore);

	/**
	 * Every profile, deleted ones too, that has this email address, ignoring case. Normally at most one; the column is not
	 * unique, so a caller that needs one person must treat more than one as ambiguous.
	 */
	List<UserProfile> findByEmail(String email);

	/**
	 * The same for many addresses at once, for the import: the profiles by lower-cased address. An address nobody has is
	 * absent from the map. The addresses must already be lower-cased.
	 */
	java.util.Map<String, List<UserProfile>> findByEmails(java.util.Collection<String> emails);

	/** Whether a profile, deleted or not, already has this email address. */
	boolean emailInUse(String email);

	/** What a new profile is created from. */
	record NewProfile(UUID id, UserProfile.Status status, TokenFacts facts, Instant syncedAt) {

	}

	/** What the token tells about the user, copied into the profile. */
	record TokenFacts(String username, String email, String firstName, String lastName, boolean emailVerified) {

	}

	/**
	 * @param text only users whose name, username or email contains this text, ignoring case
	 * @param state which users to list; null lists every user that is not deleted
	 */
	record UserFilter(String text, State state) {

		public enum State {

			INVITED, ACTIVE, DISABLED, DELETED

		}

	}

}
