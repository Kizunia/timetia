package com.kibootia.backend.platform.reports;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes a report to a CSV file (RFC 4180): one line for the column names and one for each row, a field in quotes when it
 * holds a comma, a quote or a line break, and a quote inside a field doubled. The file is UTF-8 with a byte order mark, so a
 * spreadsheet opens names with accents correctly, and lines end with {@code \r\n}.
 *
 * <p>A report is built the way a file is written: open it, write the header, write the rows one by one as they are produced,
 * close it. {@link #open} gives the {@link Writer}; use it in a try-with-resources block so the file is closed even when
 * producing a row fails.
 */
public final class CsvReport {

	private static final String LINE_END = "\r\n";

	private static final char BYTE_ORDER_MARK = '﻿';

	private CsvReport() {
	}

	/** Creates the file, or replaces it, and writes the column names. */
	public static Writer open(Path file, List<String> columns) throws ReportExportException {
		try {
			BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
			Writer writer = new Writer(file, out, columns.size());
			try {
				out.write(BYTE_ORDER_MARK);
				writer.writeLine(columns);
			}
			catch (IOException | ReportExportException ex) {
				out.close();
				throw ex;
			}
			return writer;
		}
		catch (IOException ex) {
			throw new ReportExportException(file, ex);
		}
	}

	/** An open report. */
	public static final class Writer implements AutoCloseable {

		private final Path file;

		private final BufferedWriter out;

		private final int columns;

		private int rows;

		private Writer(Path file, BufferedWriter out, int columns) {
			this.file = file;
			this.out = out;
			this.columns = columns;
		}

		/** Writes one row. It must have as many fields as the report has columns; a null field is written empty. */
		public void write(List<String> fields) throws ReportExportException {
			if (fields.size() != this.columns) {
				throw new IllegalArgumentException(
						"A row has " + fields.size() + " fields but the report has " + this.columns + " columns");
			}
			writeLine(fields);
			this.rows++;
		}

		/** Writes one line of fields; used for the column names as well as for the rows. */
		private void writeLine(List<String> fields) throws ReportExportException {
			try {
				for (int i = 0; i < fields.size(); i++) {
					if (i > 0) {
						this.out.write(',');
					}
					this.out.write(escape(fields.get(i)));
				}
				this.out.write(LINE_END);
			}
			catch (IOException ex) {
				throw new ReportExportException(this.file, ex);
			}
		}

		/** How many rows have been written, not counting the line of column names. */
		public int rows() {
			return this.rows;
		}

		@Override
		public void close() throws ReportExportException {
			try {
				this.out.close();
			}
			catch (IOException ex) {
				throw new ReportExportException(this.file, ex);
			}
		}

	}

	/** The field as it is written: unchanged, or in quotes with its quotes doubled when it needs them. */
	static String escape(String field) {
		if (field == null) {
			return "";
		}
		boolean needsQuotes = field.indexOf(',') >= 0 || field.indexOf('"') >= 0 || field.indexOf('\n') >= 0
				|| field.indexOf('\r') >= 0;
		return needsQuotes ? '"' + field.replace("\"", "\"\"") + '"' : field;
	}

}
