package com.kibootia.backend.platform.tenancy;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * The tenant of the work running on this thread (ADR 0028). Kibootia runs a single default tenant, but every
 * foundation table carries {@code tenant_id} and every query is filtered by it.
 *
 * <p>The context is bound by the web filter for an API request, and by {@link #runAs} for jobs and event handlers.
 * Hibernate reads it when a session opens, so bind it <em>before</em> starting the transaction.
 */
public final class TenantContext {

	/** The one tenant of a default installation. A version 7 UUID with a fixed value. */
	public static final UUID DEFAULT_TENANT = UUID.fromString("00000000-0000-7000-8000-000000000001");

	/**
	 * What Hibernate sees when no tenant is bound. Nothing is stored under it, so reads find nothing (fail closed);
	 * writes are refused by the entity listener.
	 */
	public static final UUID NO_TENANT = new UUID(0L, 0L);

	private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

	private TenantContext() {
	}

	public static Optional<UUID> current() {
		return Optional.ofNullable(CURRENT.get());
	}

	/** The bound tenant, or an {@link IllegalStateException} when none is bound. */
	public static UUID require() {
		UUID tenant = CURRENT.get();
		if (tenant == null) {
			throw new IllegalStateException("No tenant is bound to this thread");
		}
		return tenant;
	}

	/** Binds the tenant until the returned scope is closed, then restores whatever was bound before. */
	public static Scope bind(UUID tenant) {
		if (tenant == null || NO_TENANT.equals(tenant)) {
			throw new IllegalArgumentException("A real tenant is required");
		}
		UUID previous = CURRENT.get();
		CURRENT.set(tenant);
		return () -> {
			if (previous == null) {
				CURRENT.remove();
			}
			else {
				CURRENT.set(previous);
			}
		};
	}

	public static void runAs(UUID tenant, Runnable work) {
		try (Scope scope = bind(tenant)) {
			work.run();
		}
	}

	public static <T> T callAs(UUID tenant, Callable<T> work) {
		try (Scope scope = bind(tenant)) {
			return work.call();
		}
		catch (RuntimeException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** A binding that ends when closed. Closing does not throw. */
	@FunctionalInterface
	public interface Scope extends AutoCloseable {

		@Override
		void close();

	}

}
