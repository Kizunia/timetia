package com.kibootia.backend.identity.application;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kibootia.backend.platform.errors.ApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What the import file may look like, and what is refused before any person is looked at. */
class ImportCsvTest {

	private static List<ImportCsv.Row> parse(String text) {
		return ImportCsv.parse(text.getBytes(StandardCharsets.UTF_8), 100);
	}

	private static String codeOf(String text) {
		return fileError(() -> parse(text));
	}

	private static String fileError(Runnable parse) {
		try {
			parse.run();
		}
		catch (ApiException ex) {
			return ex.errors().isEmpty() ? ex.code() : ex.errors().get(0).code();
		}
		throw new AssertionError("The file was accepted");
	}

	@Test
	@DisplayName("columns may come in any order, scope may be left out, and values are trimmed")
	void columns() {
		assertThat(parse("role,email\nFaculty, a@x.test \n")).singleElement()
			.satisfies((row) -> assertThat(List.of(row.email(), row.role(), row.scope())).containsExactly("a@x.test", "Faculty", ""));
		assertThat(parse("Email , SCOPE ,Role\na@x.test,CSE,HOD\n")).singleElement()
			.satisfies((row) -> assertThat(List.of(row.email(), row.role(), row.scope())).containsExactly("a@x.test", "HOD", "CSE"));
	}

	@Test
	@DisplayName("a byte order mark, CRLF line ends, blank lines and a last line without a line end are all fine; the line numbers count them")
	void lineEnds() {
		List<ImportCsv.Row> rows = ImportCsv.parse("﻿email,role\r\n\r\na@x.test,Faculty\r\n  \r\nb@x.test,Faculty".getBytes(StandardCharsets.UTF_8),
				100);
		assertThat(rows).extracting(ImportCsv.Row::email).containsExactly("a@x.test", "b@x.test");
		assertThat(rows).extracting(ImportCsv.Row::line).containsExactly(3, 5);
	}

	@Test
	@DisplayName("quoted values may hold commas, doubled quotes and line breaks")
	void quoting() {
		List<ImportCsv.Row> rows = parse("email,role,scope\n\"a@x.test\",\"Faculty, senior\",\"say \"\"hi\"\"\"\nb@x.test,\"two\nlines\",\n");
		assertThat(rows).hasSize(2);
		assertThat(rows.get(0).role()).isEqualTo("Faculty, senior");
		assertThat(rows.get(0).scope()).isEqualTo("say \"hi\"");
		assertThat(rows.get(1).role()).isEqualTo("two\nlines");
		assertThat(rows.get(1).scope()).isEmpty();
		assertThat(rows.get(1).line()).isEqualTo(3);
	}

	@Test
	@DisplayName("a bad row is returned with a reason and does not stop the file")
	void badRows() {
		List<ImportCsv.Row> rows = parse("email,role,scope\n,Faculty,\na@x.test,,\na@x.test,Faculty\na@x.test,Faculty,CSE,extra\nok@x.test,Faculty,\n");
		assertThat(rows).extracting(ImportCsv.Row::problemCode)
			.containsExactly("email_required", "role_required", "wrong_field_count", "wrong_field_count", null);
		assertThat(rows.get(4).hasProblem()).isFalse();
	}

	@Test
	@DisplayName("a value that is far too long is refused for that row only")
	void tooLong() {
		String email = "a".repeat(ImportCsv.MAX_EMAIL) + "@x.test";
		assertThat(parse("email,role\n" + email + ",Faculty\nb@x.test,Faculty\n")).extracting(ImportCsv.Row::problemCode)
			.containsExactly("too_long", null);
	}

	@Test
	@DisplayName("a file that cannot be read at all is refused with the reason: encoding, header, quotes, no rows")
	void unreadable() {
		assertThat(fileError(() -> ImportCsv.parse(new byte[] { (byte) 0xff, (byte) 0xfe, 'a' }, 100))).isEqualTo("not_utf8");
		assertThat(codeOf("")).isEqualTo("empty_file");
		assertThat(codeOf("\n\n")).isEqualTo("empty_file");
		assertThat(codeOf("email,role\n")).isEqualTo("no_rows");
		assertThat(codeOf("email\na@x.test\n")).isEqualTo("missing_column");
		assertThat(codeOf("role\nFaculty\n")).isEqualTo("missing_column");
		assertThat(codeOf("email,role,nickname\na@x.test,Faculty,x\n")).isEqualTo("unknown_column");
		assertThat(codeOf("email,role,email\na@x.test,Faculty,b\n")).isEqualTo("duplicate_column");
		assertThat(codeOf("email,role\n\"a@x.test,Faculty\n")).isEqualTo("bad_quote");
		assertThat(codeOf("email,role\na@\"x\".test,Faculty\n")).isEqualTo("bad_quote");
		assertThat(codeOf("email,role\n\"a@x.test\"x,Faculty\n")).isEqualTo("bad_quote");
	}

	@Test
	@DisplayName("more rows than the limit is refused as too large, whatever the rows say")
	void tooManyRows() {
		StringBuilder file = new StringBuilder("email,role\n");
		for (int i = 0; i < 4; i++) {
			file.append("p").append(i).append("@x.test,Faculty\n");
		}
		assertThat(ImportCsv.parse(file.toString().getBytes(StandardCharsets.UTF_8), 4)).hasSize(4);
		assertThatThrownBy(() -> ImportCsv.parse(file.toString().getBytes(StandardCharsets.UTF_8), 3))
			.isInstanceOfSatisfying(ApiException.class, (ex) -> assertThat(ex.code()).isEqualTo("identity.import_too_large"));
	}

}
