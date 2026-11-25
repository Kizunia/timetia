package com.kibootia.backend.platform.tenancy;

import java.util.UUID;

import org.springframework.security.core.Authentication;

/**
 * Decides which tenant an authenticated request belongs to. Without a bean of this type every request belongs to the
 * default tenant ({@link #singleTenant()}). An application that needs live tenant resolution (a token claim, a
 * subdomain) defines its own bean; that stays outside the foundation (ADR 0028).
 */
public interface TenantResolver {

	UUID resolve(Authentication authentication);

	/** The single-tenant installation: every request belongs to {@link TenantContext#DEFAULT_TENANT}. */
	static TenantResolver singleTenant() {
		return (authentication) -> TenantContext.DEFAULT_TENANT;
	}

}
