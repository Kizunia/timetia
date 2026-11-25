package com.kibootia.backend.access;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.kibootia.backend.access.domain.DefaultDecisionPoint;
import com.kibootia.backend.access.domain.PermissionRegistry;
import com.kibootia.backend.access.domain.PermissionSource;
import com.kibootia.backend.access.domain.ScopeKinds;
import com.kibootia.backend.platform.authorization.Permission;
import com.kibootia.backend.platform.authorization.PermissionProvider;
import com.kibootia.backend.platform.authorization.PolicyDecisionPoint;
import com.kibootia.backend.platform.authorization.RequiresPermission;
import com.kibootia.backend.platform.authorization.ResourcePolicy;
import com.kibootia.backend.platform.authorization.ScopeKindProvider;
import com.kibootia.backend.platform.authorization.ScopedGrants;
import com.kibootia.backend.platform.authorization.ScopedResourcePolicy;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Wires the decision point (ADR 0044): the permissions every module declares and the Java policies. A caller's
 * permissions come from the stored roles ({@code StoredPermissionSource}, found by component scanning).
 */
@Configuration(proxyBeanMethods = false)
class AccessConfiguration {

	@Bean
	PermissionRegistry permissionRegistry(ObjectProvider<PermissionProvider> providers) {
		List<Permission> declared = new ArrayList<>();
		providers.orderedStream().forEach((provider) -> declared.addAll(provider.permissions()));
		return new PermissionRegistry(declared);
	}

	/** {@code GLOBAL} and the kinds of place modules declare (Timetia ADR 0004). */
	@Bean
	ScopeKinds scopeKinds(ObjectProvider<ScopeKindProvider> providers) {
		List<String> declared = new ArrayList<>();
		providers.orderedStream().forEach((provider) -> declared.addAll(provider.kinds()));
		return new ScopeKinds(declared);
	}

	@Bean
	PolicyDecisionPoint policyDecisionPoint(PermissionRegistry registry, PermissionSource source,
			ObjectProvider<ScopedGrants> grants, ObjectProvider<ResourcePolicy> policies,
			ObjectProvider<ScopedResourcePolicy> scopedPolicies) {
		return new DefaultDecisionPoint(registry, source, grants.getIfAvailable(() -> ScopedGrants.NONE),
				policies.orderedStream().toList(), scopedPolicies.orderedStream().toList());
	}

	/**
	 * Every {@code @RequiresPermission} must name a declared permission, and not a policy-decided one: a typo stops the
	 * application at startup, and so does a permission that only a record check can satisfy (Timetia ADR 0004).
	 */
	@Bean
	SmartInitializingSingleton requiredPermissionsAreDeclared(ApplicationContext context, PermissionRegistry registry) {
		return () -> {
			List<String> unknown = new ArrayList<>();
			List<String> policyDecided = new ArrayList<>();
			for (String name : context.getBeanDefinitionNames()) {
				Class<?> type = context.getType(name, false);
				if (type == null) {
					continue;
				}
				ReflectionUtils.doWithMethods(ClassUtils.getUserClass(type), (Method method) -> {
					RequiresPermission required = AnnotatedElementUtils.findMergedAnnotation(method,
							RequiresPermission.class);
					if (required == null) {
						return;
					}
					String where = type.getSimpleName() + "." + method.getName() + " requires '" + required.value() + "'";
					if (!registry.isRegistered(required.value())) {
						unknown.add(where);
					}
					else if (registry.isPolicyDecided(required.value())) {
						policyDecided.add(where);
					}
				});
			}
			if (!unknown.isEmpty()) {
				throw new IllegalStateException("@RequiresPermission names a permission nobody declared: " + unknown);
			}
			if (!policyDecided.isEmpty()) {
				throw new IllegalStateException("@RequiresPermission names a policy-decided permission, which only a "
						+ "check on a record can satisfy; use Authorization.require(action, record): " + policyDecided);
			}
		};
	}

}
