package com.kibootia.backend.files.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.files.FilesPermissions;
import com.kibootia.backend.files.application.StoredFileStore.StoredFile;
import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.authorization.RequiresPermission;
import com.kibootia.backend.platform.jobs.JobsProperties;
import com.kibootia.backend.platform.jobs.RetentionPurgeable;

/**
 * Removes files that were deleted longer ago than the retention period (ADR 0034): the stored objects first, then the
 * rows. In that order, so a failure in between leaves rows that name objects that are gone, and the next run finishes
 * the job; the other order would leave objects nothing refers to. Works in batches so one run is bounded.
 */
@Component
@ConditionalOnFilesEnabled
class FileRetention implements RetentionPurgeable {

	/** A run removes at most this many batches; the next day's run continues. */
	private static final int MAX_BATCHES = 20;

	private final StoredFileStore files;

	private final ObjectStorage storage;

	private final FilesProperties properties;

	private final JobsProperties jobs;

	private final Clock clock;

	FileRetention(StoredFileStore files, ObjectStorage storage, FilesProperties properties, JobsProperties jobs,
			Clock clock) {
		this.files = files;
		this.storage = storage;
		this.properties = properties;
		this.jobs = jobs;
		this.clock = clock;
	}

	@Override
	@NoAuthorization("returns a constant label for the logs")
	public String name() {
		return "deleted-files";
	}

	@Override
	@Transactional
	@RequiresPermission(FilesPermissions.PURGE)
	public int purge() {
		Instant cutoff = this.clock.instant().minus(Duration.ofDays(this.jobs.softDeleteRetentionDays()));
		int removed = 0;
		for (int batch = 0; batch < MAX_BATCHES; batch++) {
			List<StoredFile> expired = this.files.findDeletedBefore(cutoff, this.properties.purgeBatchSize());
			if (expired.isEmpty()) {
				break;
			}
			this.storage.deleteAll(expired.stream().flatMap((file) -> file.keys().stream()).toList());
			List<UUID> ids = expired.stream().map(StoredFile::id).toList();
			this.files.deleteAll(ids);
			removed += ids.size();
		}
		return removed;
	}

}
