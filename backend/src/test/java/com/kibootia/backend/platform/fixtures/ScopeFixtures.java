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
			// The contract: within a kind a code names at most one live place. A double that overwrote would hide a test mistake.
			UUID existing = this.byCode.get(kind + ":" + code);
			if (existing != null && !existing.equals(id)) {
				throw new IllegalStateException("The code " + code + " already names another " + kind);
			}
			add(kind, id);
			this.byCode.put(kind + ":" + code, id);
			this.codes.put(kind + ":" + id, code);
		}

		private volatile boolean unavailable;

		public void clear() {
			this.known.clear();
			this.byCode.clear();
			this.codes.clear();
			this.unavailable = false;
		}

		/** Makes every question throw, as a provider whose database or service is down would. {@link #clear()} resets it. */
		public void setUnavailable(boolean unavailable) {
			this.unavailable = unavailable;
		}

		private void requireAvailable() {
			if (this.unavailable) {
				throw new IllegalStateException("scope directory is down");
			}
		}

		/** A place that was deleted: it is no longer found by id or by code, as the contract requires. */
		public void remove(String kind, UUID id) {
			this.known.remove(kind + ":" + id);
			String code = this.codes.remove(kind + ":" + id);
			if (code != null) {
				this.byCode.remove(kind + ":" + code);
			}
		}

		@Override
		public boolean exists(String kind, UUID id) {
			requireAvailable();
			return this.known.contains(kind + ":" + id);
		}

		@Override
		public Optional<UUID> idOf(String kind, String code) {
			requireAvailable();
			return Optional.ofNullable(this.byCode.get(kind + ":" + code));
		}

		@Override
		public Map<UUID, String> codesOf(String kind, Collection<UUID> ids) {
			requireAvailable();
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
