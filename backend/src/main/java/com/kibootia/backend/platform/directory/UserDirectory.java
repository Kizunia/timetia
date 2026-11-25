package com.kibootia.backend.platform.directory;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * What other modules may ask about users without depending on the {@code identity} module (ADR 0039). The audit viewer
 * needs names, and the administrator safeguards in {@code access} need to know who can still sign in. {@code identity}
 * implements it; the port lives here so the dependency points at {@code platform} and no module cycle appears.
 */
public interface UserDirectory {

	/**
	 * The users among {@code ids} that Kibootia still holds, in the caller's tenant. A user who was erased, or never
	 * existed, is absent: callers show "deleted user" for them.
	 */
	Map<UUID, UserSummary> find(Collection<UUID> ids);

}
