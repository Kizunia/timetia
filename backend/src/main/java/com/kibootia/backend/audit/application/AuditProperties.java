package com.kibootia.backend.audit.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

/**
 * Audit settings (ADR 0046).
 *
 * @param retentionDays how long entries are kept before the purge job removes them; 365 by default
 */
@ConfigurationProperties("kibootia.audit")
@Validated
public record AuditProperties(@DefaultValue("365") @Min(1) int retentionDays) {

}
