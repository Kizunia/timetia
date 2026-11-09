package com.kibootia.backend.platform.authorization;

import java.util.UUID;

/**
 * A record that belongs to a tenant. The decision point denies access when the record's tenant is not the caller's,
 * as a second line behind the tenant filter on queries (ADR 0028).
 */
public interface TenantBound {

	UUID tenantId();

}
