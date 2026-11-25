package com.kibootia.backend.files.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The storage port of the {@code files} module (ADR 0029): the few operations the module needs from an S3-compatible
 * server, with no vendor type in the signatures. The S3 adapter implements it; tests use an in-memory fake. Keys are
 * built by the module ({@code <tenant>/<category>/<file>/<rendition>}), never from user input.
 *
 * <p>Every method throws {@link Unavailable} when the server cannot be reached or refuses the call, so a storage outage
 * is one failure the callers can turn into 503 and nothing else.
 */
public interface ObjectStorage {

	/** Creates the bucket when it does not exist. Safe to call again. */
	void ensureBucket();

	void put(String key, byte[] content, String contentType);

	/** The object, or empty when there is none. The caller closes the stream. */
	Optional<StoredObject> get(String key);

	/** Deleting an object that is not there is not an error. */
	void delete(String key);

	/** Deletes up to a thousand keys at once; absent keys are ignored. */
	void deleteAll(Collection<String> keys);

	/** One page of the objects whose key starts with the prefix, in key order. */
	Listing list(String prefix, String continuationToken, int maxKeys);

	/**
	 * A time-limited address the browser could upload to without the backend in between. No file category uses it yet;
	 * it exists because ADR 0029 makes presigned transfers an opt-in capability of the port.
	 */
	URL presignPut(String key, String contentType, Duration validFor);

	/** A time-limited address the browser could download from. Also unused by any category yet. */
	URL presignGet(String key, Duration validFor);

	record StoredObject(InputStream content, long length, String contentType) implements AutoCloseable {

		/** Releases the connection to the server; always close an object that was read. */
		@Override
		public void close() {
			try {
				this.content.close();
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}

	}

	record ObjectInfo(String key, long size, Instant lastModified) {

	}

	/** @param nextToken null on the last page */
	record Listing(List<ObjectInfo> objects, String nextToken) {

	}

	/** The server could not be reached, or refused the call. The message never carries a credential. */
	class Unavailable extends RuntimeException {

		public Unavailable(String message, Throwable cause) {
			super(message, cause);
		}

	}

}
