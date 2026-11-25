package com.kibootia.backend.platform.jobs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Job settings (ADR 0031, ADR 0034).
 *
 * @param enabled whether scheduled jobs run in this process; on by default, switched off in tests
 * @param retentionCron when the retention purge runs, a Spring cron expression in UTC
 * @param softDeleteRetentionDays how long a soft-deleted row is kept before the purge removes it for good
 */
@ConfigurationProperties("kibootia.jobs")
@Validated
public record JobsProperties(@DefaultValue("true") boolean enabled,
		@DefaultValue("0 30 3 * * *") @NotBlank String retentionCron,
		@DefaultValue("30") @Min(1) int softDeleteRetentionDays) {

}
