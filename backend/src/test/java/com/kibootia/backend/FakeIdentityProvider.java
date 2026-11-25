package com.kibootia.backend;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.kibootia.backend.identity.application.IdentityProviderAdmin;

/**
 * Stands in for Keycloak in every integration test except {@code KeycloakAdminAdapterIT}. It keeps accounts in memory,
 * records what it was asked to do, and can be told to fail, so the tests can show that a Keycloak outage delays a
 * change without losing it.
 *
 * <p>A user it was never told about counts as an enabled account with a verified address: tests sign in as arbitrary
 * IDs, and Keycloak knowing them is the normal case. {@link #goneFromKeycloak} makes one unknown, as after an erasure.
 */
public final class FakeIdentityProvider implements IdentityProviderAdmin {

	private final Map<UUID, Account> accounts = new ConcurrentHashMap<>();

	private final Map<String, UUID> emails = new ConcurrentHashMap<>();

	private final Set<UUID> gone = ConcurrentHashMap.newKeySet();

	private final List<String> calls = new CopyOnWriteArrayList<>();

	private final Map<String, Integer> failures = new ConcurrentHashMap<>();

	private volatile boolean everythingFails;

	/** Forgets everything: accounts, calls and failures. */
	public void reset() {
		this.accounts.clear();
		this.emails.clear();
		this.gone.clear();
		this.calls.clear();
		this.failures.clear();
		this.everythingFails = false;
	}

	/** Registers an account with these properties. */
	public void register(UUID id, boolean enabled, boolean emailVerified) {
		this.gone.remove(id);
		this.accounts.put(id, new Account(enabled, emailVerified));
	}

	/** Keycloak already has an account for this address (lower case), so creating one answers "email in use". */
	public void knowsEmail(String email) {
		this.emails.put(email, UUID.randomUUID());
	}

	/** Keycloak no longer knows this user, as after an erasure. */
	public void goneFromKeycloak(UUID id) {
		this.accounts.remove(id);
		this.gone.add(id);
	}

	/** The next {@code times} calls of this kind fail as if Keycloak were down. Kinds are the method names. */
	public void failNext(String operation, int times) {
		this.failures.put(operation, times);
	}

	/** Every call fails until {@link #reset}. */
	public void failEverything() {
		this.everythingFails = true;
	}

	/** What was asked, in order, such as {@code sendInvitation:<id>}. */
	public List<String> calls() {
		return new ArrayList<>(this.calls);
	}

	public long countCalls(String prefix) {
		return this.calls.stream().filter((call) -> call.startsWith(prefix)).count();
	}

	public Optional<Account> account(UUID id) {
		return Optional.ofNullable(this.accounts.get(id));
	}

	private void failIfAsked(String operation, UUID id) {
		Integer remaining = this.failures.get(operation);
		this.calls.add(operation + ":" + id);
		if (this.everythingFails) {
			throw new Unavailable("The fake Keycloak is down");
		}
		if (remaining != null && remaining > 0) {
			this.failures.put(operation, remaining - 1);
			throw new Unavailable("The fake Keycloak failed " + operation);
		}
	}

	@Override
	public UUID createUser(String email, boolean emailVerified, List<String> requiredActions) {
		failIfAsked("createUser", new UUID(0, 0));
		if (this.emails.containsKey(email)) {
			throw new EmailInUse();
		}
		UUID id = UUID.randomUUID();
		this.emails.put(email, id);
		this.accounts.put(id, new Account(true, emailVerified));
		return id;
	}

	@Override
	public Optional<Account> find(UUID id) {
		failIfAsked("find", id);
		if (this.gone.contains(id)) {
			return Optional.empty();
		}
		return Optional.of(this.accounts.getOrDefault(id, new Account(true, true)));
	}

	@Override
	public void setEnabled(UUID id, boolean enabled) {
		failIfAsked("setEnabled", id);
		Account current = this.accounts.getOrDefault(id, new Account(true, true));
		this.accounts.put(id, new Account(enabled, current.emailVerified()));
		this.calls.add("setEnabled=" + enabled + ":" + id);
	}

	@Override
	public void endSessions(UUID id) {
		failIfAsked("endSessions", id);
	}

	@Override
	public void sendInvitation(UUID id) {
		failIfAsked("sendInvitation", id);
	}

	@Override
	public void sendVerificationEmail(UUID id) {
		failIfAsked("sendVerificationEmail", id);
	}

	@Override
	public void delete(UUID id) {
		failIfAsked("delete", id);
		this.accounts.remove(id);
		this.gone.add(id);
		this.emails.values().remove(id);
	}

}
