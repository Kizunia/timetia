package com.kibootia.backend.files.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import com.kibootia.backend.files.application.ObjectStorage.Listing;
import com.kibootia.backend.files.application.ObjectStorage.ObjectInfo;
import com.kibootia.backend.files.application.StoredFileStore.StoredFile;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.tenancy.TenantContext;

/**
 * Brings the object store and the metadata back in step (ADR 0029). Two things can drift: an upload that failed between
 * writing its objects and its row leaves objects nothing refers to, and an object lost from the store leaves a row with
 * nothing behind it.
 *
 * <ul>
 * <li>An object whose file has no row is <b>removed</b>, once it is older than the grace period, so an upload that is
 * still being written is never touched.</li>
 * <li>A row whose objects are missing is only <b>reported</b>. Metadata is never deleted on the word of a listing: a
 * partial answer or an outage would otherwise erase the record of a file that is fine.</li>
 * </ul>
 *
 * <p>{@link ReconciliationSchedule} runs it daily under a database lock; it never holds a database transaction while the
 * storage server is being called.
 */
@Component
@ConditionalOnFilesEnabled
public class ReconciliationJob {

	/** What a run found, for the logs and for tests. */
	public record Result(int orphanObjectsRemoved, int filesMissingObjects) {

	}

	private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

	private static final int PAGE = 1000;

	/** Tenants the foundation runs: the single default tenant. A multi-tenant application supplies its own list. */
	private static final List<UUID> TENANTS = List.of(TenantContext.DEFAULT_TENANT);

	private final StoredFileStore files;

	private final ObjectStorage storage;

	private final FilesProperties properties;

	private final Authorization authorization;

	private final Clock clock;

	ReconciliationJob(StoredFileStore files, ObjectStorage storage, FilesProperties properties,
			Authorization authorization, Clock clock) {
		this.files = files;
		this.storage = storage;
		this.properties = properties;
		this.authorization = authorization;
		this.clock = clock;
	}

	/** Reconciles every tenant now. A tenant that fails is logged and the others still run. */
	@NoAuthorization("work no user started: it runs as the system, with the reason logged, from the schedule")
	public Result runOnce() {
		int removed = 0;
		int missing = 0;
		for (UUID tenant : TENANTS) {
			try {
				Result result = TenantContext.callAs(tenant,
						() -> this.authorization.runAsSystem("files reconciliation", () -> reconcile(tenant)));
				removed += result.orphanObjectsRemoved();
				missing += result.filesMissingObjects();
			}
			catch (RuntimeException ex) {
				log.error("Reconciling the files of tenant {} failed; the other tenants continue", tenant, ex);
			}
		}
		return new Result(removed, missing);
	}

	private Result reconcile(UUID tenant) {
		Instant olderThan = this.clock.instant().minus(this.properties.reconcileGrace());
		Set<UUID> idsWithObjects = new HashSet<>();
		int removed = 0;
		String token = null;
		do {
			Listing page = this.storage.list(StoredFile.tenantPrefix(tenant), token, PAGE);
			List<ObjectInfo> candidates = new ArrayList<>();
			Set<UUID> ids = new HashSet<>();
			for (ObjectInfo object : page.objects()) {
				UUID id = fileIdOf(object.key());
				if (id == null) {
					continue;
				}
				idsWithObjects.add(id);
				if (object.lastModified().isBefore(olderThan)) {
					candidates.add(object);
					ids.add(id);
				}
			}
			Set<UUID> known = this.files.existing(ids);
			List<String> orphans = candidates.stream()
				.filter((object) -> !known.contains(fileIdOf(object.key())))
				.map(ObjectInfo::key)
				.toList();
			if (!orphans.isEmpty()) {
				this.storage.deleteAll(orphans);
				removed += orphans.size();
			}
			token = page.nextToken();
		}
		while (token != null);

		int missing = countFilesMissingObjects(olderThan, idsWithObjects);
		log.info("Files reconciliation for tenant {} removed {} orphan objects and found {} files with no object", tenant,
				removed, missing);
		return new Result(removed, missing);
	}

	/** Rows older than the grace period whose file has no object in the listing. They are reported, never deleted. */
	private int countFilesMissingObjects(Instant olderThan, Set<UUID> idsWithObjects) {
		int missing = 0;
		UUID after = null;
		while (true) {
			List<UUID> page = this.files.idsCreatedBefore(olderThan, after, PAGE);
			if (page.isEmpty()) {
				return missing;
			}
			for (UUID id : page) {
				if (!idsWithObjects.contains(id)) {
					missing++;
					log.warn("File {} has metadata but no stored object", id);
				}
			}
			after = page.get(page.size() - 1);
		}
	}

	/** The file ID in {@code <tenant>/<category>/<file>/<rendition>}, or null for a key that does not follow it. */
	static UUID fileIdOf(String key) {
		String[] parts = key.split("/");
		if (parts.length != 4) {
			return null;
		}
		try {
			return UUID.fromString(parts[2]);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

}
