package com.kibootia.backend.files.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Where the metadata of stored files is kept (ADR 0003). Every method acts on the current tenant. {@link #find} sees live
 * files only; the methods about deleted files and about erasure reach every row, because the retention job and the
 * erasure of a user must.
 */
public interface StoredFileStore {

	/** A new file ID, a UUIDv7 (ADR 0033). The ID is needed before the row exists, because the object keys contain it. */
	UUID nextId();

	void insert(StoredFile file);

	/** A live file, or empty when there is none or it was deleted. */
	Optional<StoredFile> find(UUID id);

	/** Marks a live file deleted. Returns false when there was none. */
	boolean softDelete(UUID id);

	/** Files deleted before the cutoff, oldest first, at most {@code limit} of them. */
	List<StoredFile> findDeletedBefore(Instant cutoff, int limit);

	/** Every file of the owner, live or deleted. */
	List<StoredFile> findByOwner(UUID owner);

	/** Removes the rows for good. */
	void deleteAll(Collection<UUID> ids);

	/** Which of these IDs have a row, live or deleted. */
	Set<UUID> existing(Collection<UUID> ids);

	/**
	 * The IDs of rows, live or deleted, created before the instant, in key order and after {@code after} (null for the
	 * first page). Reconciliation pages through these to find files whose objects are missing.
	 */
	List<UUID> idsCreatedBefore(Instant before, UUID after, int limit);

	/**
	 * @param category the name of the {@link com.kibootia.backend.platform.files.FileCategory}
	 * @param renditions the names of the stored renditions
	 */
	record StoredFile(UUID id, UUID tenantId, UUID ownerId, String category, String contentType,
			List<String> renditions) {

		public String key(String rendition) {
			return StoredFile.key(this.tenantId, this.category, this.id, rendition);
		}

		public List<String> keys() {
			return this.renditions.stream().map(this::key).toList();
		}

		public static String key(UUID tenant, String category, UUID id, String rendition) {
			return tenant + "/" + category + "/" + id + "/" + rendition;
		}

		/** The prefix every object of the tenant starts with. */
		public static String tenantPrefix(UUID tenant) {
			return tenant + "/";
		}

	}

}
