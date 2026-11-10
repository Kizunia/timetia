package com.kibootia.backend.files;

import java.util.List;

import org.springframework.stereotype.Component;

import com.kibootia.backend.files.application.ConditionalOnFilesEnabled;
import com.kibootia.backend.platform.authorization.Permission;
import com.kibootia.backend.platform.authorization.PermissionProvider;

/**
 * The permissions of the files module (ADR 0044). Uploading and viewing a file are decided by the module that owns it,
 * such as {@code identity} for an avatar, so the only permissions here are the two the jobs act with. No user holds them
 * except through the administrator role; the jobs run as the system, which holds every permission.
 */
@Component
@ConditionalOnFilesEnabled
public class FilesPermissions implements PermissionProvider {

	/** Permanently remove files whose retention period has passed. */
	public static final String PURGE = "files.files.purge";

	/** Remove stored objects that no metadata row refers to. */
	public static final String RECONCILE = "files.files.reconcile";

	@Override
	public List<Permission> permissions() {
		return List.of(Permission.restricted(PURGE, "Permanently remove files whose retention period has passed"),
				Permission.restricted(RECONCILE, "Remove stored objects that nothing refers to"));
	}

}
