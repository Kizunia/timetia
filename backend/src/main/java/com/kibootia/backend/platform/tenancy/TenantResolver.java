package com.kibootia.backend.platform.tenancy;

import java.util.UUID;

import org.springframework.security.core.Authentication;

/**
 * Decides which tenant an authenticated request belongs to. Kibootia ships {@link DefaultTenantResolver}, which always
 * answers the default tenant. An application that needs live tenant resolution (a token claim, a subdomain) replaces
 * this bean; that stays outside the foundation (ADR 0028).
 */
public interface TenantResolver {

	UUID resolve(Authentication authentication);

}
