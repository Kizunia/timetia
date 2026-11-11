package com.kibootia.backend;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.kibootia.backend.access.domain.PermissionRegistry;
import com.kibootia.backend.access.domain.PermissionSource;

/**
 * Lets a test act as an administrator before roles exist (milestone 3). On top of the built-in {@code user} role, a
 * caller holds whatever the test has {@link #grant granted}. Import it into a test; call {@link #reset} after each test.
 */
@TestConfiguration(proxyBeanMethods = false)
public class GrantedPermissions {

	private static final Set<String> GRANTED = ConcurrentHashMap.newKeySet();

	public static void grant(String... permissions) {
		GRANTED.addAll(Set.of(permissions));
	}

	public static void reset() {
		GRANTED.clear();
	}

	@Bean
	@Primary
	PermissionSource grantedPermissionSource(PermissionRegistry registry) {
		return (caller) -> {
			Set<String> held = new HashSet<>(registry.userRolePermissions());
			held.addAll(GRANTED);
			return held;
		};
	}

}
