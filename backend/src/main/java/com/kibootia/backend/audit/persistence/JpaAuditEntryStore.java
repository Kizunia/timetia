package com.kibootia.backend.audit.persistence;

import java.time.Instant;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

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
	public int deleteOccurredBefore(Instant cutoff) {
		return this.entries.deleteOccurredBefore(cutoff);
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
