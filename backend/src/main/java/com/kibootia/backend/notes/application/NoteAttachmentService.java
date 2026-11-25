package com.kibootia.backend.notes.application;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.kibootia.backend.notes.NotesPermissions;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.files.FileCategory;
import com.kibootia.backend.platform.files.FileCategory.Rendition;
import com.kibootia.backend.platform.files.FileContent;
import com.kibootia.backend.platform.files.StoredFileRef;
import com.kibootia.backend.platform.files.StoredFiles;
import com.kibootia.backend.platform.ratelimit.RateLimitCategory;
import com.kibootia.backend.platform.ratelimit.RateLimiter;

/**
 * The picture attached to a note: the reference for a feature that keeps a file next to its own data (ADR 0029, ADR
 * 0051). The bytes are kept by the files module through the {@link StoredFiles} port, which this module asks for through
 * an {@code ObjectProvider}: with the files module switched off the attachment endpoints answer 404 {@code files.disabled}
 * and the rest of the notes work. The module declares its {@link #CATEGORY}, authorizes first, calls the port, and stores
 * the returned ID in its own table.
 *
 * <p>Who may do what follows the note, not the file:
 *
 * <ul>
 * <li>Whoever may see a note may see its picture, checked on every view. A note the caller may not see is 404, and so is
 * a note without a picture, so nothing is revealed.</li>
 * <li>Whoever may change a note may attach, replace or remove its picture. Uploads are rate limited, and a caller who is
 * refused spends none of their allowance.</li>
 * <li>The file belongs to the note's owner, whoever uploads it, so erasing the owner removes it with their notes.</li>
 * </ul>
 *
 * <p>The methods that change data are deliberately not one transaction: the storage server is called without a database
 * transaction open, and only the switch of the note's picture is transactional (in the store). A deleted note keeps its
 * picture, hidden with it, so restoring the note brings it back; {@code NotePurged} removes the file for good.
 */
@Service
public class NoteAttachmentService {

	public static final String DISABLED = "files.disabled";

	public static final String NOT_FOUND = "notes.attachment_not_found";

	/** What a note's picture may be and the sizes it is kept in: whole, with its proportions. Larger uploads are 413. */
	public static final FileCategory CATEGORY = new FileCategory("note-image", 5L * 1024 * 1024,
			List.of(Rendition.fit(NoteAttachmentSize.PREVIEW.rendition(), 480),
					Rendition.fit(NoteAttachmentSize.FULL.rendition(), 1600)));

	private static final Logger log = LoggerFactory.getLogger(NoteAttachmentService.class);

	private final NoteStore notes;

	private final ObjectProvider<StoredFiles> files;

	private final Authorization authorization;

	private final RateLimiter rateLimiter;

	NoteAttachmentService(NoteStore notes, ObjectProvider<StoredFiles> files, Authorization authorization,
			RateLimiter rateLimiter) {
		this.notes = notes;
		this.files = files;
		this.authorization = authorization;
		this.rateLimiter = rateLimiter;
	}

	/**
	 * Makes the upload the picture of the note, replacing the one it had, and returns the note as it is now. The replaced
	 * file is hidden at once and removed once its retention period has passed (ADR 0034).
	 */
	public Note upload(UUID noteId, InputStream content) {
		StoredFiles storage = storage();
		Note note = this.notes.find(noteId).orElseThrow(NoteAttachmentService::noteNotFound);
		if (!this.authorization.can(NotesPermissions.VIEW, note)) {
			throw noteNotFound();
		}
		this.authorization.require(NotesPermissions.UPDATE, note);
		this.rateLimiter.acquire(RateLimitCategory.UPLOADS);
		StoredFileRef stored = storage.store(CATEGORY, note.ownerId(), content);
		Optional<NoteStore.AttachmentChange> change = this.notes.replaceAttachment(noteId, stored.id());
		if (change.isEmpty()) {
			// The note was deleted while the file was being stored: the new file belongs to nobody.
			discardQuietly(storage, stored.id());
			throw noteNotFound();
		}
		discardQuietly(storage, change.get().previousFileId());
		return this.notes.find(noteId).orElseThrow(NoteAttachmentService::noteNotFound);
	}

	/** Removes the picture of the note. Does nothing when it has none. */
	public void remove(UUID noteId) {
		StoredFiles storage = storage();
		Note note = this.notes.find(noteId).orElseThrow(NoteAttachmentService::noteNotFound);
		if (!this.authorization.can(NotesPermissions.VIEW, note)) {
			throw noteNotFound();
		}
		this.authorization.require(NotesPermissions.UPDATE, note);
		this.notes.replaceAttachment(noteId, null).ifPresent((change) -> discardQuietly(storage, change.previousFileId()));
	}

	/**
	 * The picture of a note, or what the caller already has. A note without a picture gives empty, which the caller sees
	 * as the same 404 as a note they may not see.
	 *
	 * @param ifNoneMatch the {@code If-None-Match} header of the request, or null
	 */
	public Optional<View> view(UUID noteId, NoteAttachmentSize size, String ifNoneMatch) {
		StoredFiles storage = storage();
		Note note = this.notes.find(noteId).orElseThrow(NoteAttachmentService::noteNotFound);
		if (!this.authorization.can(NotesPermissions.VIEW, note)) {
			throw noteNotFound();
		}
		if (note.attachmentFileId() == null) {
			return Optional.empty();
		}
		String etag = "\"" + note.attachmentFileId() + "-" + size.rendition() + "\"";
		if (matches(ifNoneMatch, etag)) {
			return Optional.of(new NotModified(etag));
		}
		return storage.open(note.attachmentFileId(), size.rendition()).map((content) -> new Image(etag, content));
	}

	/** The answer to a request for a picture. */
	public sealed interface View permits NotModified, Image {

		String etag();

	}

	/** The caller's copy is current; nothing is sent. */
	public record NotModified(String etag) implements View {

	}

	/** The picture as PNG bytes. The caller streams it and closes it. */
	public record Image(String etag, FileContent content) implements View {

	}

	private StoredFiles storage() {
		StoredFiles storage = this.files.getIfAvailable();
		if (storage == null) {
			throw ApiException.notFound(DISABLED, "Files are switched off in this installation.");
		}
		return storage;
	}

	private static ApiException noteNotFound() {
		return ApiException.notFound(NoteService.NOT_FOUND, "There is no note with that ID.");
	}

	private static void discardQuietly(StoredFiles storage, UUID previous) {
		if (previous == null) {
			return;
		}
		try {
			storage.discard(previous);
		}
		catch (RuntimeException ex) {
			// The new picture is already in place. An old file that is visible to nobody costs nothing, and it goes when the
			// owner is erased.
			log.warn("Could not hide the replaced note picture {}", previous, ex);
		}
	}

	/** Whether an {@code If-None-Match} header names this entity tag; weak tags and {@code *} count. */
	static boolean matches(String ifNoneMatch, String etag) {
		if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
			return false;
		}
		return Arrays.stream(ifNoneMatch.split(","))
			.map(String::strip)
			.anyMatch((candidate) -> candidate.equals("*") || candidate.equals(etag) || candidate.equals("W/" + etag));
	}

}
