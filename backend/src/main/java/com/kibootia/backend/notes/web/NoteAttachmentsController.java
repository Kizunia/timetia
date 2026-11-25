package com.kibootia.backend.notes.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.kibootia.backend.notes.application.NoteAttachmentService;
import com.kibootia.backend.notes.application.NoteAttachmentSize;
import com.kibootia.backend.platform.errors.ApiException;

/**
 * The picture of a note, a sub-resource of the note (ADR 0029, ADR 0043). Uploads and downloads stream browser, Next.js
 * server, this application, storage; the storage server has no public address. The picture is served from this endpoint
 * on every view, never from a public bucket or a long-lived link, so the permission check is made each time. The browser
 * may keep a copy and ask whether it is current, which costs the check but not the transfer.
 */
@RestController
@RequestMapping("/api/v1/notes/{id}/attachment")
class NoteAttachmentsController {

	private final NoteAttachmentService attachments;

	NoteAttachmentsController(NoteAttachmentService attachments) {
		this.attachments = attachments;
	}

	/** Makes the uploaded image the note's picture. JPEG, PNG or WebP up to 5 MB; it is re-encoded and stripped. */
	@PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	NoteResponse upload(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
		try (InputStream content = file.getInputStream()) {
			return NoteMapper.toResponse(this.attachments.upload(id, content));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void remove(@PathVariable UUID id) {
		this.attachments.remove(id);
	}

	/** The picture as a PNG, whole and in proportion. Whoever may see the note may see it. */
	@GetMapping(produces = { MediaType.IMAGE_PNG_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE })
	ResponseEntity<InputStreamResource> view(@PathVariable UUID id,
			@RequestParam(defaultValue = "PREVIEW") NoteAttachmentSize size,
			@RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
		NoteAttachmentService.View view = this.attachments.view(id, size, ifNoneMatch)
			.orElseThrow(() -> ApiException.notFound(NoteAttachmentService.NOT_FOUND, "This note has no picture."));
		return switch (view) {
			case NoteAttachmentService.NotModified notModified -> ResponseEntity.status(HttpStatus.NOT_MODIFIED)
				.eTag(notModified.etag())
				.cacheControl(CacheControl.noCache().cachePrivate())
				.build();
			case NoteAttachmentService.Image image -> ResponseEntity.ok()
				.contentType(MediaType.IMAGE_PNG)
				.contentLength(image.content().length())
				.eTag(image.etag())
				.cacheControl(CacheControl.noCache().cachePrivate())
				.header("X-Content-Type-Options", "nosniff")
				.header("Content-Security-Policy", "default-src 'none'; sandbox")
				.body(new InputStreamResource(image.content().content()));
		};
	}

}
