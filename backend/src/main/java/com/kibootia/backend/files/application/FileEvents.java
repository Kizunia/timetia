package com.kibootia.backend.files.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import com.kibootia.backend.files.application.StoredFileStore.StoredFile;
import com.kibootia.backend.identity.UserErased;
import com.kibootia.backend.platform.events.DurableEventListener;

/**
 * What the files module does when something happens elsewhere (ADR 0031). The method is not public, so it is not a use
 * case: it is reached only through the event, as the system, in the event's tenant, once per event even if the delivery
 * repeats.
 */
@Component
@ConditionalOnFilesEnabled
class FileEvents {

	private static final Logger log = LoggerFactory.getLogger(FileEvents.class);

	private final StoredFileStore files;

	private final ObjectStorage storage;

	FileEvents(StoredFileStore files, ObjectStorage storage) {
		this.files = files;
		this.storage = storage;
	}

	/**
	 * A user was erased (ADR 0034): everything they stored goes at once, deleted files included, without waiting for the
	 * retention period. The objects first, then the rows; if the storage server is down the handler fails and is tried
	 * again, and deleting what is already gone is harmless.
	 */
	@DurableEventListener
	void on(UserErased event) {
		List<StoredFile> owned = this.files.findByOwner(event.userId());
		if (owned.isEmpty()) {
			return;
		}
		this.storage.deleteAll(owned.stream().flatMap((file) -> file.keys().stream()).toList());
		this.files.deleteAll(owned.stream().map(StoredFile::id).toList());
		log.info("Removed {} files of erased user {}", owned.size(), event.userId());
	}

}
