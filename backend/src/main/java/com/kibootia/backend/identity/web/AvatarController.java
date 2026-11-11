package com.kibootia.backend.identity.web;

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

import com.kibootia.backend.identity.application.AvatarService;
import com.kibootia.backend.identity.application.AvatarSize;
import com.kibootia.backend.identity.application.ProfileService;
import com.kibootia.backend.platform.errors.ApiException;

/**
 * Avatars (ADR 0029, ADR 0041). Uploads and downloads stream browser, Next.js server, this application, storage; the
 * storage server has no public address. The image is served from this endpoint on every view, never from a public
 * bucket or a long-lived link, so the permission check is made each time. The browser may keep a copy and ask whether
 * it is current, which costs the check but not the transfer.
 */
@RestController
@RequestMapping("/api/v1")
class AvatarController {

	private final AvatarService avatars;

	private final ProfileService profiles;

	AvatarController(AvatarService avatars, ProfileService profiles) {
		this.avatars = avatars;
		this.profiles = profiles;
	}

	/** Makes the uploaded image the caller's avatar. JPEG, PNG or WebP up to 5 MB; it is re-encoded and stripped. */
	@PutMapping(path = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	MeResponse upload(@RequestPart("file") MultipartFile file) {
		try (InputStream content = file.getInputStream()) {
			this.avatars.upload(content);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return MeController.toResponse(this.profiles.me());
	}

	@DeleteMapping("/me/avatar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void remove() {
		this.avatars.remove();
	}

	/** The avatar as a PNG. The owner always; anyone else needs the permission to see profiles. */
	@GetMapping(path = "/users/{id}/avatar", produces = { MediaType.IMAGE_PNG_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE })
	ResponseEntity<InputStreamResource> view(@PathVariable UUID id,
			@RequestParam(defaultValue = "LARGE") AvatarSize size,
			@RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
		AvatarService.View view = this.avatars.view(id, size, ifNoneMatch)
			.orElseThrow(() -> ApiException.notFound(AvatarService.NOT_FOUND, "This user has no avatar."));
		return switch (view) {
			case AvatarService.NotModified notModified -> ResponseEntity.status(HttpStatus.NOT_MODIFIED)
				.eTag(notModified.etag())
				.cacheControl(CacheControl.noCache().cachePrivate())
				.build();
			case AvatarService.Image image -> ResponseEntity.ok()
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
