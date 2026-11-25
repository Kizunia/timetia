package com.kibootia.backend.files.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MimeTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import com.kibootia.backend.files.application.StoredFileStore.StoredFile;
import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.files.FileCategory;
import com.kibootia.backend.platform.files.FileContent;
import com.kibootia.backend.platform.files.StoredFileRef;
import com.kibootia.backend.platform.files.StoredFiles;
import com.kibootia.backend.platform.tenancy.TenantContext;

/**
 * Keeps files for the other modules (ADR 0029). The rules, in the order they are applied to an upload:
 *
 * <ol>
 * <li>the size limit, while reading, so an oversized upload is never held in memory;</li>
 * <li>the type, found from the bytes. The filename and the declared content type are never looked at, because the
 * sender chooses both;</li>
 * <li>the inspection hook, where a deployment can add a malware scan;</li>
 * <li>re-encoding: every rendition is a new image made from the pixels, so nothing hidden in the original survives.</li>
 * </ol>
 *
 * <p>The objects are written first and the metadata row last, each in its own short step, so no database transaction is
 * ever open while the storage server is being called. A failure between the two leaves an object nothing refers to,
 * which the reconciliation job removes once it is old enough to be sure no upload is still in progress.
 *
 * <p>Nothing here decides who may upload or see a file. The module that owns the file does, through the decision point.
 */
@Component
@ConditionalOnFilesEnabled
public class FileService implements StoredFiles {

	public static final String TOO_LARGE = "files.too_large";

	public static final String UNSUPPORTED_TYPE = "files.unsupported_type";

	public static final String INVALID_IMAGE = "files.invalid_image";

	/** What an image category accepts, decided from the bytes. SVG is not here on purpose: it can carry script. */
	private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

	private static final String OUTPUT_TYPE = "image/png";

	private static final Logger log = LoggerFactory.getLogger(FileService.class);

	private final StoredFileStore files;

	private final ObjectStorage storage;

	private final FileInspector inspector;

	private final FilesProperties properties;

	private final AtomicBoolean bucketReady = new AtomicBoolean();

	FileService(StoredFileStore files, ObjectStorage storage, FileInspector inspector, FilesProperties properties) {
		this.files = files;
		this.storage = storage;
		this.inspector = inspector;
		this.properties = properties;
	}

	@Override
	@NoAuthorization("holds the bytes only; the module that owns the file decides who may upload it")
	public StoredFileRef store(FileCategory category, UUID owner, InputStream content) {
		long limit = Math.min(category.maxBytes(), this.properties.maxUploadSize().toBytes());
		byte[] bytes = readAtMost(content, limit);
		String type = detectType(bytes);
		if (!IMAGE_TYPES.contains(type)) {
			throw ApiException.unsupportedMediaType(UNSUPPORTED_TYPE, "Upload a JPEG, PNG or WebP image.");
		}
		this.inspector.inspect(bytes, type);
		Map<String, byte[]> renditions = ImageProcessor.render(bytes, category.renditions());

		UUID id = this.files.nextId();
		UUID tenant = TenantContext.require();
		List<String> written = new ArrayList<>();
		try {
			ensureBucket();
			for (Map.Entry<String, byte[]> rendition : renditions.entrySet()) {
				String key = StoredFile.key(tenant, category.name(), id, rendition.getKey());
				this.storage.put(key, rendition.getValue(), OUTPUT_TYPE);
				written.add(key);
			}
			this.files.insert(new StoredFile(id, tenant, owner, category.name(), OUTPUT_TYPE,
					List.copyOf(renditions.keySet())));
		}
		catch (ObjectStorage.Unavailable ex) {
			removeQuietly(written);
			log.warn("Storing a file failed because the storage server is not available: {}", ex.getMessage());
			throw unavailable();
		}
		catch (RuntimeException ex) {
			removeQuietly(written);
			throw ex;
		}
		return new StoredFileRef(id);
	}

	@Override
	@NoAuthorization("serves the bytes only; the module that owns the file decides who may see it")
	public Optional<FileContent> open(UUID fileId, String rendition) {
		Optional<StoredFile> file = this.files.find(fileId).filter((found) -> found.renditions().contains(rendition));
		if (file.isEmpty()) {
			return Optional.empty();
		}
		try {
			return this.storage.get(file.get().key(rendition))
				.map((object) -> new FileContent(object.content(), object.length(), object.contentType()));
		}
		catch (ObjectStorage.Unavailable ex) {
			log.warn("Reading a file failed because the storage server is not available: {}", ex.getMessage());
			throw unavailable();
		}
	}

	@Override
	@NoAuthorization("hides a file for the module that owns it, which decided that it may")
	public void discard(UUID fileId) {
		this.files.softDelete(fileId);
	}

	private void ensureBucket() {
		if (this.properties.createBucket() && !this.bucketReady.get()) {
			this.storage.ensureBucket();
			this.bucketReady.set(true);
		}
	}

	/** Reads the upload, refusing it as soon as it is larger than the limit. */
	private static byte[] readAtMost(InputStream content, long limit) {
		try {
			byte[] bytes = content.readNBytes((int) Math.min(limit + 1, Integer.MAX_VALUE - 8));
			if (bytes.length > limit) {
				throw ApiException.payloadTooLarge(TOO_LARGE,
						"The file is larger than the allowed " + (limit / (1024 * 1024)) + " MB.");
			}
			if (bytes.length == 0) {
				throw ApiException.invalid(INVALID_IMAGE, "The file is empty.", List.of());
			}
			return bytes;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** The type found from the first bytes, whatever the sender called the file. */
	private static String detectType(byte[] bytes) {
		try {
			return MimeTypes.getDefaultMimeTypes().detect(new java.io.ByteArrayInputStream(bytes), new Metadata())
				.getBaseType()
				.toString();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private void removeQuietly(List<String> keys) {
		if (keys.isEmpty()) {
			return;
		}
		try {
			this.storage.deleteAll(keys);
		}
		catch (RuntimeException ex) {
			// The reconciliation job removes what is left once it is old enough.
			log.warn("Could not remove {} objects of a failed upload; reconciliation will", keys.size());
		}
	}

	private static ApiException unavailable() {
		return ApiException.serviceUnavailable("Files are not available right now. Try again in a moment.");
	}

}
