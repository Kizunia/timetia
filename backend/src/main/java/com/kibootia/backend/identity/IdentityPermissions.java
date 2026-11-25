package com.kibootia.backend.identity;

import java.util.List;

import org.springframework.stereotype.Component;

import com.kibootia.backend.platform.authorization.Permission;
import com.kibootia.backend.platform.authorization.PermissionProvider;

/**
 * The permissions of the identity module (ADR 0044, ADR 0041). None is held by the built-in {@code user} role: a new
 * user sees no one else, because with open sign-up any stranger could otherwise browse the user directory. Actions on
 * one's own profile need no permission; ownership allows them.
 */
@Component
public class IdentityPermissions implements PermissionProvider {

	/** See other users: the list, their profiles and their avatars (ADR 0041). */
	public static final String VIEW_PROFILES = "identity.profiles.view";

	/** Invite someone by email and send the invitation again. */
	public static final String INVITE_USERS = "identity.users.invite";

	/** Disable and enable accounts. */
	public static final String DISABLE_USERS = "identity.users.disable";

	/** Delete accounts. A deleted account can be restored until it is erased. */
	public static final String DELETE_USERS = "identity.users.delete";

	/** Bring a deleted account back. */
	public static final String RESTORE_USERS = "identity.users.restore";

	/** Erase a deleted account for good, on a privacy request, or let the retention job do it. */
	public static final String ERASE_USERS = "identity.users.erase";

	@Override
	public List<Permission> permissions() {
		return List.of(Permission.restricted(VIEW_PROFILES, "See other users and their profiles"),
				Permission.restricted(INVITE_USERS, "Invite users by email"),
				Permission.restricted(DISABLE_USERS, "Disable and enable accounts"),
				Permission.restricted(DELETE_USERS, "Delete accounts"),
				Permission.restricted(RESTORE_USERS, "Restore deleted accounts"),
				Permission.restricted(ERASE_USERS, "Erase deleted accounts for good"));
	}

}
