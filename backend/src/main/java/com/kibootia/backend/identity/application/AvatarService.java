package com.kibootia.backend.identity.application;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.kibootia.backend.identity.IdentityPermissions;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.files.FileCategory;
import com.kibootia.backend.platform.files.FileCategory.Rendition;
import com.kibootia.backend.platform.files.FileContent;
import com.kibootia.backend.platform.files.StoredFileRef;
import com.kibootia.backend.platform.files.StoredFiles;
import com.kibootia.backend.platform.ratelimit.RateLimitCategory;
import com.kibootia.backend.platform.ratelimit.RateLimiter;

/**
 * The avatar of a user (ADR 0029, ADR 0041). The bytes are kept by the files module through the {@link StoredFiles}
 * port, which this module asks for through an {@code ObjectProvider}: with the files module switched off the avatar
 * endpoints answer 404 {@code files.disabled} and everything else works.
 *
 * <p>Who may do what:
 *
 * <ul>
 * <li>A user changes only their own avatar, and only once their email address is verified, like any change to their own
 * account. Uploads are rate limited.</li>
 * <li>A user always sees their own avatar, verified or not. Seeing anyone else's needs {@code identity.profiles.view},
 * the same permission as seeing their profile, and never crosses tenants.</li>
 * </ul>
 *
 * <p>The methods that change data are deliberately not one transaction: the storage server is called without a database
 * transaction open, and only the switch of the profile's avatar is transactional (in the store).
 */
@Service
public class AvatarService {

	public static final String DISABLED = "files.disabled";

	public static final String NOT_FOUND = "identity.avatar_not_found";

	/** What an avatar upload may be and the sizes it is kept in. Larger images are refused with 413. */
	public static final FileCategory AVATAR = new FileCategory("avatar", 5L * 1024 * 1024,
			List.of(new Rendition(AvatarSize.SMALL.rendition(), 64), new Rendition(AvatarSize.LARGE.rendition(), 256)));

	private static final Logger log = LoggerFactory.getLogger(AvatarService.class);

	private final UserProfileStore profiles;

	private final ObjectProvider<StoredFiles> files;

	private final Authorization authorization;

	private final RateLimiter rateLimiter;

	AvatarService(UserProfileStore profiles, ObjectProvider<StoredFiles> files, Authorization authorization,
			RateLimiter rateLimiter) {
		this.profiles = profiles;
		this.files = files;
		this.authorization = authorization;
		this.rateLimiter = rateLimiter;
	}

	/**
	 * Stores the upload as the caller's avatar, replacing the one they had. The replaced file is hidden at once and
	 * removed once its retention period has passed (ADR 0034).
	 */
	public void upload(InputStream content) {
		this.authorization.requireVerified();
		StoredFiles storage = storage();
		this.rateLimiter.acquire(RateLimitCategory.UPLOADS);
		UUID me = this.authorization.currentUserId();
		StoredFileRef stored = storage.store(AVATAR, me, content);
		discardQuietly(storage, this.profiles.replaceAvatar(me, stored.id()));
	}

	/** Removes the caller's avatar. Does nothing when they have none. */
	public void remove() {
		this.authorization.requireVerified();
		StoredFiles storage = storage();
		discardQuietly(storage, this.profiles.replaceAvatar(this.authorization.currentUserId(), null));
	}

	/**
	 * The avatar of a user, or what the caller already has. A user with no avatar, an unknown user, a deleted user and a
	 * user of another tenant all give empty, so a caller who may look learns nothing from the difference.
	 *
	 * @param ifNoneMatch the {@code If-None-Match} header of the request, or null
	 */
	public Optional<View> view(UUID userId, AvatarSize size, String ifNoneMatch) {
		StoredFiles storage = storage();
		if (!userId.equals(this.authorization.currentUserId())) {
			this.authorization.require(IdentityPermissions.VIEW_PROFILES);
		}
		Optional<UUID> avatar = this.profiles.find(userId).filter((profile) -> !profile.deleted()).map(UserProfile::avatarFileId);
		if (avatar.isEmpty()) {
			return Optional.empty();
		}
		String etag = "\"" + avatar.get() + "-" + size.rendition() + "\"";
		if (matches(ifNoneMatch, etag)) {
			return Optional.of(new NotModified(etag));
		}
		return storage.open(avatar.get(), size.rendition()).map((content) -> new Image(etag, content));
	}

	/** The answer to a request for an avatar. */
	public sealed interface View permits NotModified, Image {

		String etag();

	}

	/** The caller's copy is current; nothing is sent. */
	public record NotModified(String etag) implements View {

	}

	/** The avatar as PNG bytes. The caller streams it and closes it. */
	public record Image(String etag, FileContent content) implements View {

	}

	private StoredFiles storage() {
		StoredFiles storage = this.files.getIfAvailable();
		if (storage == null) {
			throw ApiException.notFound(DISABLED, "Files are switched off in this installation.");
		}
		return storage;
	}

	private static void discardQuietly(StoredFiles storage, UUID previous) {
		if (previous == null) {
			return;
		}
		try {
			storage.discard(previous);
		}
		catch (RuntimeException ex) {
			// The new avatar is already in place. An old file that stays visible to nobody costs nothing, and it goes when
			// the user is erased.
			log.warn("Could not hide the replaced avatar file {}", previous, ex);
		}
	}

	/** Whether an {@code If-None-Match} header names this entity tag; weak tags and {@code *} count. */
	static boolean matches(String ifNoneMatch, String etag) {
		if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
			return false;
		}
		return Arrays.stream(ifNoneMatch.split(","))
			.map(String::strip)
			.anyMatch((candidate) -> candidate.equals("*") || candidate.equals(etag) || candidate.equals("W/" + etag));
	}

}
