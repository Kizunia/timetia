package com.kibootia.backend.access;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.kibootia.backend.access.builtin.BuiltInRolePermissionSource;
import com.kibootia.backend.access.domain.DefaultDecisionPoint;
import com.kibootia.backend.access.domain.PermissionRegistry;
import com.kibootia.backend.access.domain.PermissionSource;
import com.kibootia.backend.platform.authorization.Permission;
import com.kibootia.backend.platform.authorization.PermissionProvider;
import com.kibootia.backend.platform.authorization.PolicyDecisionPoint;
import com.kibootia.backend.platform.authorization.RequiresPermission;
import com.kibootia.backend.platform.authorization.ResourcePolicy;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Wires the decision point (ADR 0044): the permissions every module declares, the source of a caller's permissions
 * and the Java policies. Milestone 3 swaps {@link #permissionSource} for the stored roles.
 */
@Configuration(proxyBeanMethods = false)
class AccessConfiguration {

	@Bean
	PermissionRegistry permissionRegistry(ObjectProvider<PermissionProvider> providers) {
		List<Permission> declared = new ArrayList<>();
		providers.orderedStream().forEach((provider) -> declared.addAll(provider.permissions()));
		return new PermissionRegistry(declared);
	}

	@Bean
	PermissionSource permissionSource(PermissionRegistry registry) {
		return new BuiltInRolePermissionSource(registry);
	}

	@Bean
	PolicyDecisionPoint policyDecisionPoint(PermissionRegistry registry, PermissionSource source,
			ObjectProvider<ResourcePolicy> policies) {
		return new DefaultDecisionPoint(registry, source, policies.orderedStream().toList());
	}

	/** Every {@code @RequiresPermission} must name a declared permission; a typo stops the application at startup. */
	@Bean
	SmartInitializingSingleton requiredPermissionsAreDeclared(ApplicationContext context, PermissionRegistry registry) {
		return () -> {
			List<String> unknown = new ArrayList<>();
			for (String name : context.getBeanDefinitionNames()) {
				Class<?> type = context.getType(name, false);
				if (type == null) {
					continue;
				}
				ReflectionUtils.doWithMethods(ClassUtils.getUserClass(type), (Method method) -> {
					RequiresPermission required = AnnotatedElementUtils.findMergedAnnotation(method,
							RequiresPermission.class);
					if (required != null && !registry.isRegistered(required.value())) {
						unknown.add(type.getSimpleName() + "." + method.getName() + " requires '" + required.value() + "'");
					}
				});
			}
			if (!unknown.isEmpty()) {
				throw new IllegalStateException("@RequiresPermission names a permission nobody declared: " + unknown);
			}
		};
	}

}
