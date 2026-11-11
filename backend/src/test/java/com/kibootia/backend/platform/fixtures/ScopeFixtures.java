package com.kibootia.backend.platform.fixtures;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.kibootia.backend.platform.authorization.ScopeDirectory;

/**
 * A stand-in for the module that will own departments, divisions and batches: it knows only the places a test adds. The
 * application has no such bean, so without this import a scoped role cannot be assigned (it fails closed). Imported by
 * the tests that assign scoped roles.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ScopeFixtures {

	@Bean
	FakeScopeDirectory scopeDirectory() {
		return new FakeScopeDirectory();
	}

	/** The places that exist, by kind and id. */
	public static final class FakeScopeDirectory implements ScopeDirectory {

		private final Set<String> known = ConcurrentHashMap.newKeySet();

		public void add(String kind, UUID id) {
			this.known.add(kind + ":" + id);
		}

		public void clear() {
			this.known.clear();
		}

		@Override
		public boolean exists(String kind, UUID id) {
			return this.known.contains(kind + ":" + id);
		}

	}

}
