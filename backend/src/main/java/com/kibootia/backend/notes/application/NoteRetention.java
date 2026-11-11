package com.kibootia.backend.notes.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.notes.NotePurged;
import com.kibootia.backend.notes.NotesPermissions;
import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.authorization.RequiresPermission;
import com.kibootia.backend.platform.events.EventContextFactory;
import com.kibootia.backend.platform.jobs.JobsProperties;
import com.kibootia.backend.platform.jobs.RetentionPurgeable;

/**
 * Permanently removes notes that were deleted longer ago than the retention period (ADR 0034). A note with a picture
 * also publishes {@link NotePurged} in the same transaction, so its file is removed afterwards, even if the storage
 * server is not reachable right now.
 */
@Component
class NoteRetention implements RetentionPurgeable {

	private final NoteStore notes;

	private final Clock clock;

	private final JobsProperties properties;

	private final ApplicationEventPublisher events;

	private final EventContextFactory eventContexts;

	NoteRetention(NoteStore notes, Clock clock, JobsProperties properties, ApplicationEventPublisher events,
			EventContextFactory eventContexts) {
		this.notes = notes;
		this.clock = clock;
		this.properties = properties;
		this.events = events;
		this.eventContexts = eventContexts;
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
		Instant cutoff = this.clock.instant().minus(Duration.ofDays(this.properties.softDeleteRetentionDays()));
		// Asked before the rows are removed: afterwards nothing says which files belonged to them.
		for (NoteStore.AttachedFile attached : this.notes.attachmentsDeletedBefore(cutoff)) {
			this.events.publishEvent(new NotePurged(attached.noteId(), attached.fileId(), this.eventContexts.capture()));
		}
		return this.notes.purgeDeletedBefore(cutoff);
	}

}
