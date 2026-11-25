package com.kibootia.backend.identity.application;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.errors.ApiFieldError;
import com.kibootia.backend.platform.errors.ProblemCodes;

/**
 * Reads the provisioning import file (Timetia ADR 0006): UTF-8 text, comma separated, one assignment per row, with a
 * header naming the columns {@code email}, {@code role} and {@code scope} in any order ({@code scope} may be left out
 * when no row needs one). A strict subset of RFC 4180: a field may be quoted, a quote inside it is doubled, a quoted
 * field may span lines, rows end with LF or CRLF, a UTF-8 byte order mark is ignored, and blank lines are skipped.
 *
 * <p>The file as a whole is refused, with a message, when it cannot be read at all: bad encoding, a quote that is never
 * closed or is in the wrong place, a header that is missing or wrong, no rows, or too many. A single bad row is not a
 * reason to refuse the file: it comes back as a {@link Row} with a problem, and the import reports it.
 *
 * <p>Nothing here looks at what the cells mean. That is the import's job.
 */
final class ImportCsv {

	static final int MAX_EMAIL = 320;

	static final int MAX_NAME = 100;

	private static final List<String> COLUMNS = List.of("email", "role", "scope");

	private ImportCsv() {
	}

	/**
	 * One data row. {@code line} is the line in the file where the row starts (the header is line 1). When
	 * {@code problemCode} is set the row cannot be used and the cells may be empty.
	 */
	record Row(int line, String email, String role, String scope, String problemCode, String problemMessage) {

		boolean hasProblem() {
			return this.problemCode != null;
		}

		static Row of(int line, String email, String role, String scope) {
			return new Row(line, email, role, scope, null, null);
		}

		static Row refused(int line, String code, String message) {
			return new Row(line, "", "", "", code, message);
		}

	}

	/** Parses the file, or throws a 400 (or 413 for too many rows) describing why it cannot be read at all. */
	static List<Row> parse(byte[] bytes, int maxRows) {
		String text = decode(bytes);
		List<CsvRecord> records = tokenize(text);
		if (records.isEmpty()) {
			throw unreadable("empty_file", "The file is empty. The first line must name the columns: email, role, scope.");
		}
		Map<String, Integer> columns = header(records.get(0));
		if (records.size() == 1) {
			throw unreadable("no_rows", "The file has a header but no rows.");
		}
		if (records.size() - 1 > maxRows) {
			throw ApiException.payloadTooLarge("identity.import_too_large",
					"The file has more than " + maxRows + " rows. Split it into smaller files; importing the same file again is harmless.");
		}
		List<Row> rows = new ArrayList<>();
		for (CsvRecord record : records.subList(1, records.size())) {
			rows.add(row(record, columns));
		}
		return rows;
	}

	// ---------------------------------------------------------------------------------------------------------------

	private static String decode(byte[] bytes) {
		String text;
		try {
			text = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes))
				.toString();
		}
		catch (CharacterCodingException ex) {
			throw unreadable("not_utf8", "The file is not UTF-8 text. In a spreadsheet, save it as 'CSV UTF-8'.");
		}
		return text.startsWith("﻿") ? text.substring(1) : text;
	}

	private static Map<String, Integer> header(CsvRecord record) {
		Map<String, Integer> columns = new HashMap<>();
		for (int i = 0; i < record.fields().size(); i++) {
			String name = record.fields().get(i).strip().toLowerCase(Locale.ROOT);
			if (!COLUMNS.contains(name)) {
				throw unreadable("unknown_column", "Unknown column '" + shortened(name)
						+ "'. The columns are email, role and scope.");
			}
			if (columns.put(name, i) != null) {
				throw unreadable("duplicate_column", "The column '" + name + "' appears twice.");
			}
		}
		for (String required : List.of("email", "role")) {
			if (!columns.containsKey(required)) {
				throw unreadable("missing_column", "The header has no '" + required + "' column.");
			}
		}
		return columns;
	}

	private static Row row(CsvRecord record, Map<String, Integer> columns) {
		if (record.fields().size() != columns.size()) {
			return Row.refused(record.line(), "wrong_field_count",
					"Expected " + columns.size() + " columns but found " + record.fields().size());
		}
		String email = cell(record, columns, "email");
		String role = cell(record, columns, "role");
		String scope = cell(record, columns, "scope");
		if (email.length() > MAX_EMAIL || role.length() > MAX_NAME || scope.length() > MAX_NAME) {
			return Row.refused(record.line(), "too_long", "A value is too long");
		}
		if (email.isEmpty()) {
			return Row.refused(record.line(), "email_required", "The email is empty");
		}
		if (role.isEmpty()) {
			return new Row(record.line(), email, role, scope, "role_required", "The role is empty");
		}
		return Row.of(record.line(), email, role, scope);
	}

	private static String cell(CsvRecord record, Map<String, Integer> columns, String name) {
		Integer index = columns.get(name);
		return (index == null) ? "" : record.fields().get(index).strip();
	}

	private enum State {

		FIELD_START, UNQUOTED, QUOTED, AFTER_QUOTE

	}

	/** The fields of one record and the line it starts on. */
	private record CsvRecord(int line, List<String> fields) {

	}

	/** Splits the text into records of fields. Blank lines are dropped. */
	private static List<CsvRecord> tokenize(String text) {
		List<CsvRecord> records = new ArrayList<>();
		List<String> fields = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		State state = State.FIELD_START;
		int line = 1;
		int recordLine = 1;
		boolean anyContent = false;
		int length = text.length();
		for (int i = 0; i < length; i++) {
			char c = text.charAt(i);
			boolean newline = c == '\n' || c == '\r';
			if (c == '\r' && i + 1 < length && text.charAt(i + 1) == '\n') {
				i++;
			}
			switch (state) {
				case FIELD_START -> {
					if (c == '"') {
						state = State.QUOTED;
						anyContent = true;
					}
					else if (c == ',') {
						fields.add("");
						anyContent = true;
					}
					else if (newline) {
						if (anyContent) {
							fields.add("");
							records.add(new CsvRecord(recordLine, fields));
							fields = new ArrayList<>();
						}
						line++;
						recordLine = line;
						anyContent = false;
					}
					else {
						field.append(c);
						state = State.UNQUOTED;
						anyContent = true;
					}
				}
				case UNQUOTED -> {
					if (c == ',') {
						fields.add(field.toString());
						field.setLength(0);
						state = State.FIELD_START;
					}
					else if (newline) {
						fields.add(field.toString());
						field.setLength(0);
						records.add(new CsvRecord(recordLine, fields));
						fields = new ArrayList<>();
						state = State.FIELD_START;
						line++;
						recordLine = line;
						anyContent = false;
					}
					else if (c == '"') {
						throw unreadable("bad_quote", "A quote on line " + line
								+ " is in the middle of a value. Put the whole value in quotes, and double any quote inside it.");
					}
					else {
						field.append(c);
					}
				}
				case QUOTED -> {
					if (c == '"') {
						if (i + 1 < length && text.charAt(i + 1) == '"') {
							field.append('"');
							i++;
						}
						else {
							state = State.AFTER_QUOTE;
						}
					}
					else {
						if (newline) {
							line++;
							field.append('\n');
						}
						else {
							field.append(c);
						}
					}
				}
				case AFTER_QUOTE -> {
					if (c == ',') {
						fields.add(field.toString());
						field.setLength(0);
						state = State.FIELD_START;
					}
					else if (newline) {
						fields.add(field.toString());
						field.setLength(0);
						records.add(new CsvRecord(recordLine, fields));
						fields = new ArrayList<>();
						state = State.FIELD_START;
						line++;
						recordLine = line;
						anyContent = false;
					}
					else {
						throw unreadable("bad_quote", "On line " + line + " a closing quote is followed by more text.");
					}
				}
			}
		}
		switch (state) {
			case QUOTED -> throw unreadable("bad_quote", "The quote that opens on line " + recordLine + " is never closed.");
			case UNQUOTED, AFTER_QUOTE -> {
				fields.add(field.toString());
				records.add(new CsvRecord(recordLine, fields));
			}
			case FIELD_START -> {
				if (anyContent) {
					fields.add("");
					records.add(new CsvRecord(recordLine, fields));
				}
			}
		}
		return records.stream().filter((record) -> !isBlank(record)).toList();
	}

	/** A record that is a single empty value: a line with nothing (or only spaces) on it. */
	private static boolean isBlank(CsvRecord record) {
		return record.fields().size() == 1 && record.fields().get(0).isBlank();
	}

	private static String shortened(String value) {
		return (value.length() > 30) ? value.substring(0, 30) + "..." : value;
	}

	private static ApiException unreadable(String code, String message) {
		return ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The file cannot be read.",
				List.of(new ApiFieldError("file", code, message)));
	}

}
