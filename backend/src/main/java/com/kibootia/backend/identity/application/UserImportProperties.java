package com.kibootia.backend.identity.application;

import java.time.Duration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * The limits of the provisioning import (Timetia ADR 0006). They keep one request from running for minutes or sending
 * an unbounded number of emails; a file that is too big is split, and because a repeated import only adds, running the
 * same file again is always safe.
 *
 * @param maxRows the most data rows (not counting the header) in one file; more is refused
 * @param maxBytes the largest file in bytes; larger is refused with 413
 * @param timeBudget how long a real import may run; people not reached in time are reported as not processed, and a
 *        second run picks up where it stopped
 * @param maxConsecutiveUnavailable how many people in a row may fail because the identity provider is unreachable
 *        before the rest are reported as not processed
 */
@ConfigurationProperties("kibootia.identity.import")
@Validated
public record UserImportProperties(@DefaultValue("1000") @Min(1) @Max(10000) int maxRows,
		@DefaultValue("524288") @Min(1024) @Max(5242880) int maxBytes, @DefaultValue("PT2M") @NotNull Duration timeBudget,
		@DefaultValue("5") @Min(1) int maxConsecutiveUnavailable) {

}
