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
 * @param renditions the images made from each upload, for example a small and a large square avatar, or a preview and
 *        a full-size picture that keep their proportions
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
	 * @param pixels for a {@link Shape#SQUARE} the width and the height; for a {@link Shape#FIT} the longest side
	 * @param shape how the image is cut to that size
	 */
	public record Rendition(String name, int pixels, Shape shape) {

		public Rendition {
			if (name == null || !name.matches("[a-z0-9]+")) {
				throw new IllegalArgumentException("A rendition name is lower-case letters and digits: " + name);
			}
			if (pixels < 8 || pixels > 2048) {
				throw new IllegalArgumentException("A rendition is between 8 and 2048 pixels wide");
			}
			if (shape == null) {
				throw new IllegalArgumentException("A rendition needs a shape");
			}
		}

		/** A square rendition, cut from the centre of the image (an avatar). */
		public Rendition(String name, int pixels) {
			this(name, pixels, Shape.SQUARE);
		}

		/** A rendition that keeps the whole image and its proportions, its longest side at most {@code pixels}. */
		public static Rendition fit(String name, int pixels) {
			return new Rendition(name, pixels, Shape.FIT);
		}

	}

	/** How a rendition is cut from the upright image. */
	public enum Shape {

		/** The centred square of the image, scaled to the rendition's size; a smaller image is enlarged. */
		SQUARE,

		/** The whole image with its proportions; the longest side is the rendition's size, and it is never enlarged. */
		FIT

	}

}
