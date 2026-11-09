package com.kibootia.backend.platform.authorization;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Turns on method security so {@link RequiresPermission} is enforced. An application without a decision point (no {@code
 * access} module) fails closed: {@link Authorization} then denies everything.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class AuthorizationConfiguration {

	/** Lets {@code RequiresPermission} pass its {@code value} into the {@code PreAuthorize} expression. */
	@Bean
	static AnnotationTemplateExpressionDefaults annotationTemplateExpressionDefaults() {
		return new AnnotationTemplateExpressionDefaults();
	}

}
