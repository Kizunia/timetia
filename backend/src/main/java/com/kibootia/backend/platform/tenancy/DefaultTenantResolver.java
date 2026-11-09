package com.kibootia.backend.platform.tenancy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;

/** The single-tenant installation: every request belongs to {@link TenantContext#DEFAULT_TENANT}. */
@Configuration(proxyBeanMethods = false)
class DefaultTenantResolver {

	@Bean
	@ConditionalOnMissingBean(TenantResolver.class)
	TenantResolver tenantResolver() {
		return (Authentication authentication) -> TenantContext.DEFAULT_TENANT;
	}

}
