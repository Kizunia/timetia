package com.kibootia.backend.notes.application;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.notes.NotesPermissions;
import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.authorization.RequiresPermission;
import com.kibootia.backend.platform.jobs.JobsProperties;
import com.kibootia.backend.platform.jobs.RetentionPurgeable;

/** Permanently removes notes that were deleted longer ago than the retention period (ADR 0034). */
@Component
class NoteRetention implements RetentionPurgeable {

	private final NoteStore notes;

	private final Clock clock;

	private final JobsProperties properties;

	NoteRetention(NoteStore notes, Clock clock, JobsProperties properties) {
		this.notes = notes;
		this.clock = clock;
		this.properties = properties;
	}

	@Override
	@NoAuthorization("returns a constant label for the logs")
	public String name() {
		return "deleted-notes";
	}

	@Override
	@Transactional
	@RequiresPermission(NotesPermissions.PURGE)
	public int purge() {
		return this.notes.purgeDeletedBefore(
				this.clock.instant().minus(Duration.ofDays(this.properties.softDeleteRetentionDays())));
	}

}
