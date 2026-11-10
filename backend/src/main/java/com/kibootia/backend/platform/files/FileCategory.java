package com.kibootia.backend.platform.files;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A kind of file a module keeps, with the rules that go with it (ADR 0029). The module that owns the files declares its
 * category and passes it in; the storage module applies the rules and knows nothing else about the kind of file.
 *
 * <p>Only image categories exist so far. Every upload is checked from its bytes, re-encoded into each rendition and
 * stripped of metadata, and the original is never stored.
 *
 * @param name lower-case letters, digits and hyphens; part of every object key
 * @param maxBytes the largest upload accepted for this category; the module's own limit applies as well
 * @param renditions the square images made from each upload, for example a small and a large avatar
 */
public record FileCategory(String name, long maxBytes, List<Rendition> renditions) {

	private static final Pattern NAME = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

	public FileCategory {
		if (name == null || !NAME.matcher(name).matches()) {
			throw new IllegalArgumentException("A category name is lower-case letters, digits and hyphens: " + name);
		}
		if (maxBytes < 1) {
			throw new IllegalArgumentException("A category needs a positive size limit");
		}
		if (renditions == null || renditions.isEmpty()) {
			throw new IllegalArgumentException("An image category needs at least one rendition");
		}
		renditions = List.copyOf(renditions);
	}

	/**
	 * One size an image is kept in.
	 *
	 * @param name the name used to ask for it, lower-case letters and digits, for example {@code small}
	 * @param pixels the width and the height of the square image
	 */
	public record Rendition(String name, int pixels) {

		public Rendition {
			if (name == null || !name.matches("[a-z0-9]+")) {
				throw new IllegalArgumentException("A rendition name is lower-case letters and digits: " + name);
			}
			if (pixels < 8 || pixels > 2048) {
				throw new IllegalArgumentException("A rendition is between 8 and 2048 pixels wide");
			}
		}

	}

}
