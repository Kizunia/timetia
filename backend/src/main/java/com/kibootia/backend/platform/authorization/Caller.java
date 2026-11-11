package com.kibootia.backend.platform.authorization;

import java.util.UUID;

/**
 * Who is acting, as the decision point sees them (ADR 0044). Tokens prove identity only; what the caller may do comes
 * from the decision point.
 *
 * @param userId the Keycloak user ID, or the nil UUID for the system
 * @param tenantId the tenant the work runs in
 * @param emailVerified whether the caller's email address is verified (enforced from milestone 3)
 * @param system true for jobs and event handlers that no user started; see {@link Authorization#runAsSystem}
 */
public record Caller(UUID userId, UUID tenantId, boolean emailVerified, boolean system) {

	static final UUID SYSTEM_USER = new UUID(0L, 0L);

	public static Caller user(UUID userId, UUID tenantId, boolean emailVerified) {
		return new Caller(userId, tenantId, emailVerified, false);
	}

	static Caller system(UUID tenantId) {
		return new Caller(SYSTEM_USER, tenantId, true, true);
	}

}
