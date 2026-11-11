package com.kibootia.backend.audit.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

/**
 * Audit settings (ADR 0046).
 *
 * @param retentionDays how long entries are kept before the purge job removes them; 365 by default
 * @param securityEvents the limit on refusals recorded as security events (Timetia ADR 0005)
 */
@ConfigurationProperties("kibootia.audit")
@Validated
public record AuditProperties(@DefaultValue("365") @Min(1) int retentionDays,
		@DefaultValue SecurityEvents securityEvents) {

	/**
	 * @param maxPerMinute how many security events one user can cause to be recorded in a minute; the rest are dropped,
	 *        so a flood of refused requests cannot fill the log; 20 by default
	 */
	public record SecurityEvents(@DefaultValue("20") @Min(1) int maxPerMinute) {

	}

}
