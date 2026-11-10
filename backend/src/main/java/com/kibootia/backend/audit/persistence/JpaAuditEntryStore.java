package com.kibootia.backend.audit.persistence;

import java.time.Instant;
import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.kibootia.backend.audit.application.AuditEntryStore;

/** Keeps audit entries in MySQL. */
@Component
class JpaAuditEntryStore implements AuditEntryStore {

	private final AuditEntryRepository entries;

	private final JsonMapper json;

	JpaAuditEntryStore(AuditEntryRepository entries, JsonMapper json) {
		this.entries = entries;
		this.json = json;
	}

	@Override
	public void append(Entry entry) {
		this.entries.save(AuditEntryMapper.toEntity(entry, serialise(entry.changes())));
	}

	@Override
	public Page<Stored> search(Filter filter, Pageable pageable) {
		return this.entries
			.search(filter.actorId(), likePrefix(filter.actionPrefix()), filter.targetType(), filter.targetId(),
					filter.from(), filter.to(), pageable)
			.map((entity) -> AuditEntryMapper.toStored(entity, deserialise(entity.getChanges())));
	}

	@Override
	public int deleteOccurredBefore(Instant cutoff) {
		return this.entries.deleteOccurredBefore(cutoff);
	}

	/** A LIKE pattern matching the start of an action code, with the caller's own % and _ made literal. */
	private static String likePrefix(String prefix) {
		if (prefix == null || prefix.isBlank()) {
			return null;
		}
		return prefix.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
	}

	private Map<String, String> deserialise(String changes) {
		if (changes == null || changes.isBlank()) {
			return Map.of();
		}
		return this.json.readValue(changes, new TypeReference<Map<String, String>>() {
		});
	}

	private String serialise(Map<String, String> changes) {
		if (changes.isEmpty()) {
			return null;
		}
		String text = this.json.writeValueAsString(changes);
		if (text.length() > 4000) {
			throw new IllegalArgumentException("The changes of an audit entry are too large to store");
		}
		return text;
	}

}
