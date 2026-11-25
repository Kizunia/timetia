package com.kibootia.backend.platform.reports;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvReportTest {

	@TempDir
	Path folder;

	@Test
	@DisplayName("a report is the column names, then one line for each row, in UTF-8 with a byte order mark")
	void writesHeaderAndRows() throws Exception {
		Path file = this.folder.resolve("report.csv");

		try (CsvReport.Writer report = CsvReport.open(file, List.of("Name", "Room"))) {
			report.write(List.of("Dr Rao", "R101"));
			report.write(List.of("Dr Roy", "LAB1"));
			assertThat(report.rows()).isEqualTo(2);
		}

		String text = Files.readString(file);
		assertThat(text).isEqualTo("﻿Name,Room\r\nDr Rao,R101\r\nDr Roy,LAB1\r\n");
	}

	@Test
	@DisplayName("a field with a comma, a quote or a line break is quoted, and a quote inside it is doubled")
	void escapesFields() throws Exception {
		Path file = this.folder.resolve("report.csv");

		try (CsvReport.Writer report = CsvReport.open(file, List.of("A", "B", "C"))) {
			report.write(List.of("one, two", "say \"hi\"", "first\nsecond"));
		}

		assertThat(Files.readString(file))
			.endsWith("\"one, two\",\"say \"\"hi\"\"\",\"first\nsecond\"\r\n");
	}

	@Test
	@DisplayName("a missing value is written empty, and accents survive")
	void nullsAndAccents() throws Exception {
		Path file = this.folder.resolve("report.csv");

		try (CsvReport.Writer report = CsvReport.open(file, List.of("A", "B"))) {
			report.write(Arrays.asList(null, "Śrī"));
		}

		assertThat(Files.readString(file)).endsWith(",Śrī\r\n");
	}

	@Test
	@DisplayName("a row with the wrong number of fields is refused")
	void wrongNumberOfFields() throws Exception {
		Path file = this.folder.resolve("report.csv");

		try (CsvReport.Writer report = CsvReport.open(file, List.of("A", "B"))) {
			assertThatThrownBy(() -> report.write(List.of("only one"))).isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	@DisplayName("a file that cannot be created is a ReportExportException with the IOException as its cause")
	void unwritableFile() {
		Path file = this.folder.resolve("no-such-folder").resolve("report.csv");

		assertThatThrownBy(() -> CsvReport.open(file, List.of("A")))
			.isInstanceOfSatisfying(ReportExportException.class, (ex) -> assertThat(ex.file()).isEqualTo(file))
			.hasCauseInstanceOf(IOException.class);
	}

}
