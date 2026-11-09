package com.kibootia.backend.platform.persistence;

import java.util.Optional;
import java.util.UUID;

import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.kibootia.backend.platform.tenancy.TenantContext;

/** Auditing and the tenant discriminator for every JPA entity. */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing
class PersistenceConfiguration {

	/** The actor recorded for work that no signed-in user started: jobs, event handlers, startup. */
	static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);

	@Bean
	AuditorAware<UUID> auditorAware() {
		return () -> Optional.of(currentUser().orElse(SYSTEM_ACTOR));
	}

	/** Hibernate asks for the tenant whenever a session opens. */
	@Bean
	HibernatePropertiesCustomizer tenantResolverCustomizer() {
		CurrentTenantIdentifierResolver<UUID> resolver = new CurrentTenantIdentifierResolver<>() {
			@Override
			public UUID resolveCurrentTenantIdentifier() {
				return TenantContext.current().orElse(TenantContext.NO_TENANT);
			}

			@Override
			public boolean validateExistingCurrentSessions() {
				return true;
			}
		};
		return (properties) -> properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
	}

	private static Optional<UUID> currentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof Jwt token) {
			try {
				return Optional.of(UUID.fromString(token.getSubject()));
			}
			catch (IllegalArgumentException | NullPointerException ex) {
				return Optional.empty();
			}
		}
		return Optional.empty();
	}

}
