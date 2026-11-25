package com.kibootia.backend.platform.files;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** A stored file being read. The caller streams it to the client and closes it, which releases the storage connection. */
public record FileContent(InputStream content, long length, String contentType) implements AutoCloseable {

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
