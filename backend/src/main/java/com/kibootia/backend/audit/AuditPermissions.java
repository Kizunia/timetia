package com.kibootia.backend.audit;

import java.util.List;

import org.springframework.stereotype.Component;

import com.kibootia.backend.platform.authorization.Permission;
import com.kibootia.backend.platform.authorization.PermissionProvider;

/** The permissions of the audit module (ADR 0044). None is held by the built-in {@code user} role. */
@Component
public class AuditPermissions implements PermissionProvider {

	/** Read the audit log (the viewer arrives with milestone 3). */
	public static final String VIEW_ENTRIES = "audit.entries.view";

	/** Remove entries older than the retention period. */
	public static final String PURGE_ENTRIES = "audit.entries.purge";

	/** See events whose delivery keeps failing. */
	public static final String VIEW_FAILED_EVENTS = "audit.failed-events.view";

	/** Retry a failed event. */
	public static final String RETRY_FAILED_EVENTS = "audit.failed-events.retry";

	@Override
	public List<Permission> permissions() {
		return List.of(Permission.restricted(VIEW_ENTRIES, "View the audit log"),
				Permission.restricted(PURGE_ENTRIES, "Remove audit entries older than the retention period"),
				Permission.restricted(VIEW_FAILED_EVENTS, "View events whose delivery keeps failing"),
				Permission.restricted(RETRY_FAILED_EVENTS, "Retry a failed event"));
	}

}
