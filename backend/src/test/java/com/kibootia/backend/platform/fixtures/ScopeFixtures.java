package com.kibootia.backend.platform.fixtures;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

	/** The places that exist, by kind and id, and the codes of those that have one. Codes compare exactly. */
	public static final class FakeScopeDirectory implements ScopeDirectory {

		private final Set<String> known = ConcurrentHashMap.newKeySet();

		private final Map<String, UUID> byCode = new ConcurrentHashMap<>();

		private final Map<String, String> codes = new ConcurrentHashMap<>();

		public void add(String kind, UUID id) {
			this.known.add(kind + ":" + id);
		}

		/** A place that can be named by its code. */
		public void add(String kind, UUID id, String code) {
			add(kind, id);
			this.byCode.put(kind + ":" + code, id);
			this.codes.put(kind + ":" + id, code);
		}

		public void clear() {
			this.known.clear();
			this.byCode.clear();
			this.codes.clear();
		}

		@Override
		public boolean exists(String kind, UUID id) {
			return this.known.contains(kind + ":" + id);
		}

		@Override
		public Optional<UUID> idOf(String kind, String code) {
			return Optional.ofNullable(this.byCode.get(kind + ":" + code));
		}

		@Override
		public Map<UUID, String> codesOf(String kind, Collection<UUID> ids) {
			Map<UUID, String> result = new HashMap<>();
			for (UUID id : ids) {
				String code = this.codes.get(kind + ":" + id);
				if (code != null) {
					result.put(id, code);
				}
			}
			return result;
		}

	}

}
