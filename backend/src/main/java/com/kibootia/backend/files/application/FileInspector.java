package com.kibootia.backend.files.application;

/**
 * The hook where a deployment adds its own check of an upload, before anything is stored: a malware scanner such as
 * ClamAV, or a rule about content (ADR 0029). The shipped implementation accepts everything; malware scanning is a
 * recipe, not part of the foundation. Replace it by declaring another bean of this type.
 */
@FunctionalInterface
public interface FileInspector {

	/**
	 * Throws an {@link com.kibootia.backend.platform.errors.ApiException} to refuse the upload, for example 422 with a
	 * code of its own. Returns normally to accept it.
	 *
	 * @param content the upload exactly as received
	 * @param contentType the type found from the bytes
	 */
	void inspect(byte[] content, String contentType);

}
