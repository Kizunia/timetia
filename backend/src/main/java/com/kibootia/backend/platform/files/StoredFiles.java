package com.kibootia.backend.platform.files;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps files for the other modules (ADR 0029). It holds the bytes and applies the rules of the category; it decides
 * nothing about who may upload or see a file. The module that owns the file does that first, through the decision
 * point, and then calls this.
 *
 * <p>Failures reach the caller as {@code ApiException}: 413 when the upload is larger than the category allows, 415 when
 * its bytes are not an allowed type, 400 when the image cannot be read, and 503 when the storage server cannot be
 * reached.
 */
public interface StoredFiles {

	/**
	 * Checks the upload from its bytes, makes every rendition of the category and stores them. The file belongs to the
	 * owner, who is erased with them.
	 *
	 * @param content the upload; it is read at most {@code maxBytes + 1} bytes far and not closed
	 */
	StoredFileRef store(FileCategory category, UUID owner, InputStream content);

	/** The stored rendition of a file in the current tenant, or empty when there is no such file or rendition. */
	Optional<FileContent> open(UUID fileId, String rendition);

	/**
	 * Hides the file at once and removes it for good once the retention period has passed (ADR 0034). Does nothing when
	 * the file is already gone.
	 */
	void discard(UUID fileId);

}
