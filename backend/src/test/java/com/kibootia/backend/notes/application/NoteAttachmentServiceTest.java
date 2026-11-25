package com.kibootia.backend.notes.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.kibootia.backend.platform.files.FileCategory;
import com.kibootia.backend.platform.files.FileCategory.Shape;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The parts of the attachment rules that are plain logic. The order of the checks (note visible, then allowed, then the
 * upload allowance is spent, then the file is stored) needs the real decision point and is covered through the API in
 * {@code NoteAttachmentApiIT} and {@code NoteAttachmentRateLimitIT}.
 */
class NoteAttachmentServiceTest {

	@Test
	@DisplayName("a note's picture keeps its proportions in two sizes, whose names are the ones the API accepts")
	void category() {
		FileCategory category = NoteAttachmentService.CATEGORY;

		assertThat(category.name()).isEqualTo("note-image");
		assertThat(category.maxBytes()).isEqualTo(5L * 1024 * 1024);
		assertThat(category.renditions()).extracting((r) -> r.name())
			.containsExactly(NoteAttachmentSize.PREVIEW.rendition(), NoteAttachmentSize.FULL.rendition());
		assertThat(category.renditions()).allMatch((r) -> r.shape() == Shape.FIT);
		assertThat(category.renditions()).extracting((r) -> r.pixels()).containsExactly(480, 1600);
	}

	@Test
	@DisplayName("every size the API accepts is a rendition of the category")
	void everySizeExists() {
		for (NoteAttachmentSize size : NoteAttachmentSize.values()) {
			assertThat(NoteAttachmentService.CATEGORY.renditions()).extracting((r) -> r.name()).contains(size.rendition());
		}
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "\"abc-preview\"", "W/\"abc-preview\"", "*", "\"other\", \"abc-preview\"", " \"abc-preview\" " })
	@DisplayName("an If-None-Match header that names the current version, in any of its forms, matches")
	void matches(String header) {
		assertThat(NoteAttachmentService.matches(header, "\"abc-preview\"")).isTrue();
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "\"abc-full\"", "\"abc-preview-2\"", "abc-preview", "" })
	@DisplayName("a header that names another version, or none, does not match")
	void doesNotMatch(String header) {
		assertThat(NoteAttachmentService.matches(header, "\"abc-preview\"")).isFalse();
	}

	@Test
	@DisplayName("no header does not match")
	void noHeader() {
		assertThat(NoteAttachmentService.matches(null, "\"abc-preview\"")).isFalse();
	}

}
