package com.kibootia.backend.platform.events;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Settings for durable events (ADR 0031).
 *
 * @param maxAttempts how many times a failing event is delivered before the retry job stops and leaves it in the
 * failed-event view for an administrator
 * @param initialBackoff the wait before the second attempt; each further wait doubles, up to {@code maxBackoff}
 * @param maxBackoff the longest wait between attempts
 * @param staleAfter how long a delivery may stay unfinished before it counts as lost (the instance died) and is retried
 * @param pollInterval how often the retry job looks for events to retry
 * @param completedRetentionDays how long a delivered event stays in the registry before the retention purge removes it
 * @param processedRetentionDays how long the "already handled" marks are kept; must outlast the longest retry window
 */
@ConfigurationProperties("kibootia.events")
@Validated
public record EventsProperties(@DefaultValue("5") @Min(1) int maxAttempts,
		@DefaultValue("30s") @NotNull Duration initialBackoff, @DefaultValue("1h") @NotNull Duration maxBackoff,
		@DefaultValue("5m") @NotNull Duration staleAfter, @DefaultValue("30s") @NotNull Duration pollInterval,
		@DefaultValue("7") @Min(1) int completedRetentionDays, @DefaultValue("30") @Min(1) int processedRetentionDays) {

}
