package com.kibootia.backend.notes;

import java.util.List;

import org.springframework.stereotype.Component;

import com.kibootia.backend.platform.authorization.Permission;
import com.kibootia.backend.platform.authorization.PermissionProvider;

/**
 * The permissions of the notes feature (ADR 0044). A permission with an {@code -any} variant means "my own notes";
 * the variant widens it to every note of the tenant. Restoring has no such split: it is an administrative action.
 */
@Component
public class NotesPermissions implements PermissionProvider {

	public static final String CREATE = "notes.notes.create";

	public static final String VIEW = "notes.notes.view";

	public static final String VIEW_ANY = "notes.notes.view-any";

	public static final String UPDATE = "notes.notes.update";

	public static final String UPDATE_ANY = "notes.notes.update-any";

	public static final String DELETE = "notes.notes.delete";

	public static final String DELETE_ANY = "notes.notes.delete-any";

	public static final String RESTORE = "notes.notes.restore";

	public static final String PURGE = "notes.notes.purge";

	@Override
	public List<Permission> permissions() {
		return List.of(Permission.forEveryUser(CREATE, "Create notes"),
				Permission.forEveryUser(VIEW, "View your own notes"),
				Permission.restricted(VIEW_ANY, "View anyone's notes"),
				Permission.forEveryUser(UPDATE, "Change your own notes"),
				Permission.restricted(UPDATE_ANY, "Change anyone's notes"),
				Permission.forEveryUser(DELETE, "Delete your own notes"),
				Permission.restricted(DELETE_ANY, "Delete anyone's notes"),
				Permission.restricted(RESTORE, "Restore deleted notes"),
				Permission.restricted(PURGE, "Permanently remove notes whose retention period has passed"));
	}

}
