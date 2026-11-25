package com.kibootia.backend.files.application;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the {@code files} module (ADR 0024, ADR 0029). The module is on by default; with
 * {@code kibootia.files.enabled=false} none of its beans exist and the application starts without storage.
 *
 * @param enabled whether the module runs
 * @param endpoint the S3 API address as the backend reaches it
 * @param region the signing region; S3-compatible servers accept any value
 * @param bucket the bucket every object lives in
 * @param accessKey required while the module is enabled; checked at startup, never printed
 * @param secretKey required while the module is enabled; checked at startup, never printed
 * @param createBucket whether the backend creates the bucket when it is missing (a deployment may manage it instead)
 * @param maxUploadSize the largest upload any category accepts; categories may allow less
 * @param reconcileGrace how old an object without a metadata row must be before reconciliation removes it, so an upload
 *        in progress is never touched
 * @param reconcileCron when reconciliation runs, a Spring cron expression in UTC
 */
@ConfigurationProperties("kibootia.files")
@Validated
public record FilesProperties(@DefaultValue("true") boolean enabled,
		@DefaultValue("http://localhost:9000") @NotBlank String endpoint,
		@DefaultValue("us-east-1") @NotBlank String region, @DefaultValue("kibootia") @NotBlank String bucket,
		String accessKey, String secretKey, @DefaultValue("true") boolean createBucket,
		@DefaultValue("5MB") DataSize maxUploadSize, @DefaultValue("PT24H") Duration reconcileGrace,
		@DefaultValue("0 0 4 * * *") @NotBlank String reconcileCron,
		@DefaultValue("500") @Min(1) int purgeBatchSize) {

}
