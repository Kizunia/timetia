package com.kibootia.backend.audit.persistence;

import java.util.Map;

import com.kibootia.backend.audit.application.AuditEntryStore;

/** Converts the application's entry to the persistence entity by hand (ADR 0005). */
final class AuditEntryMapper {

	private AuditEntryMapper() {
	}

	static AuditEntryStore.Stored toStored(AuditEntryEntity entity, Map<String, String> changes) {
		return new AuditEntryStore.Stored(entity.getId(), entity.getOccurredAt(), entity.getActorId(), entity.getAction(),
				entity.getTargetType(), entity.getTargetId(), changes, entity.getOutcome(), entity.getTraceId());
	}

	static AuditEntryEntity toEntity(AuditEntryStore.Entry entry, String serialisedChanges) {
		return new AuditEntryEntity(entry.occurredAt(), entry.actorId(), entry.action(), entry.targetType(),
				entry.targetId(), entry.outcome(), entry.traceId(), serialisedChanges);
	}

}
