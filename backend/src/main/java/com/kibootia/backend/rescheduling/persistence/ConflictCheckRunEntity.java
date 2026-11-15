package com.kibootia.backend.rescheduling.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.kibootia.backend.platform.persistence.BaseEntity;
import com.kibootia.backend.rescheduling.domain.CheckTrigger;

/** One time the conflicts of a ticket were checked. Persistence only (ADR 0004). The findings belong to it. */
@Entity
@Table(name = "conflict_check_run")
class ConflictCheckRunEntity extends BaseEntity {

	@Column(name = "ticket_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID ticketId;

	@Enumerated(EnumType.STRING)
	@Column(name = "check_trigger", nullable = false, updatable = false, length = 12)
	private CheckTrigger trigger;

	@Column(name = "result", nullable = false, updatable = false, length = 8)
	private String result;

	protected ConflictCheckRunEntity() {
	}

	ConflictCheckRunEntity(UUID ticketId, CheckTrigger trigger, boolean blocked) {
		this.ticketId = ticketId;
		this.trigger = trigger;
		this.result = blocked ? "BLOCKED" : "CLEAR";
	}

}
