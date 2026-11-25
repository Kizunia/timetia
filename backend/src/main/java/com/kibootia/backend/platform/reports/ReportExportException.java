package com.kibootia.backend.platform.reports;

import java.nio.file.Path;

/**
 * A report could not be written to its file: the folder is missing, the disk is full, the file is locked. It is a checked
 * exception on purpose, so the code that asks for a report has to decide what to do when there is none. The
 * {@link java.io.IOException} that caused it is always attached as the cause.
 */
public class ReportExportException extends Exception {

	private final Path file;

	public ReportExportException(Path file, Throwable cause) {
		super("Could not write the report to " + file + ": " + cause.getMessage(), cause);
		this.file = file;
	}

	/** The file the report was meant for. */
	public Path file() {
		return this.file;
	}

}
