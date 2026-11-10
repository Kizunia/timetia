package com.kibootia.backend.audit.application;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.audit.AuditPermissions;
import com.kibootia.backend.audit.application.AuditRecord.Person;
import com.kibootia.backend.platform.authorization.RequiresPermission;
import com.kibootia.backend.platform.directory.UserDirectory;
import com.kibootia.backend.platform.directory.UserSummary;

/**
 * Shows the audit log to an administrator (ADR 0046). An entry holds only user IDs, so names are looked up here, when
 * the entry is shown, through the {@code platform} user directory: a user's current name appears, and a user who was
 * erased shows as erased, which pseudonymises the log without rewriting it.
 */
@Service
public class AuditViewer {

	/** The actor of work that no user started. */
	static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);

	private static final String USER_TARGET = "user";

	private final AuditEntryStore entries;

	private final UserDirectory users;

	AuditViewer(AuditEntryStore entries, UserDirectory users) {
		this.entries = entries;
		this.users = users;
	}

	@Transactional(readOnly = true)
	@RequiresPermission(AuditPermissions.VIEW_ENTRIES)
	public Page<AuditRecord> search(AuditEntryStore.Filter filter, Pageable pageable) {
		Page<AuditEntryStore.Stored> page = this.entries.search(filter, pageable);
		Set<UUID> people = new LinkedHashSet<>();
		for (AuditEntryStore.Stored entry : page.getContent()) {
			people.add(entry.actorId());
			if (isUserTarget(entry)) {
				people.add(entry.targetId());
			}
		}
		people.remove(SYSTEM_ACTOR);
		Map<UUID, UserSummary> known = this.users.find(people);
		return page.map((entry) -> new AuditRecord(entry.id(), entry.occurredAt(), person(entry.actorId(), known),
				entry.action(), entry.targetType(), entry.targetId(),
				isUserTarget(entry) ? person(entry.targetId(), known) : null, entry.changes(), entry.outcome(),
				entry.traceId()));
	}

	private static boolean isUserTarget(AuditEntryStore.Stored entry) {
		return USER_TARGET.equals(entry.targetType()) && entry.targetId() != null;
	}

	private static Person person(UUID id, Map<UUID, UserSummary> known) {
		if (SYSTEM_ACTOR.equals(id)) {
			return new Person(id, null, Person.Kind.SYSTEM);
		}
		UserSummary user = known.get(id);
		return (user != null) ? new Person(id, user.displayName(), Person.Kind.USER)
				: new Person(id, null, Person.Kind.ERASED);
	}

}
