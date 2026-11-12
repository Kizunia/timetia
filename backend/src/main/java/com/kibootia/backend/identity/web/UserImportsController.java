package com.kibootia.backend.identity.web;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.kibootia.backend.identity.application.UserImportService;

/**
 * Loads people and their scoped roles from a CSV file (Timetia ADR 0006). Administrators only. {@code dryRun=true} shows
 * what the file would do and writes nothing; the same file can then be sent for real, and sent again later without
 * changing anything.
 */
@RestController
@RequestMapping("/api/v1/user-imports")
class UserImportsController {

	private final UserImportService imports;

	UserImportsController(UserImportService imports) {
		this.imports = imports;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	UserImportResponse run(@RequestPart("file") MultipartFile file,
			@RequestParam(name = "dryRun", defaultValue = "false") boolean dryRun) {
		try {
			return UserImportResponse.of(this.imports.run(file.getBytes(), dryRun));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
