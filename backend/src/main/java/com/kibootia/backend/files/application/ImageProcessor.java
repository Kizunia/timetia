package com.kibootia.backend.files.application;

import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.twelvemonkeys.imageio.metadata.CompoundDirectory;
import com.twelvemonkeys.imageio.metadata.Entry;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEG;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEGSegment;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEGSegmentUtil;
import com.twelvemonkeys.imageio.metadata.tiff.TIFF;
import com.twelvemonkeys.imageio.metadata.tiff.TIFFReader;

import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.files.FileCategory.Rendition;
import com.kibootia.backend.platform.files.FileCategory.Shape;

/**
 * Turns an uploaded image into the renditions of its category (ADR 0029): decoded from the bytes, turned upright by its
 * EXIF orientation, cut to the centred square or kept whole with its proportions (the rendition's shape), scaled to
 * each size and written as a new PNG. A new image is drawn each time, so nothing of the original carries over: no EXIF,
 * no GPS position, no embedded thumbnail, no comment, and nothing hidden after the end of the image. The original is
 * never stored.
 *
 * <p>The decoder is told the size first and refuses an image with too many pixels, which is the usual way a small file
 * grows into gigabytes of memory. A big image is read with subsampling, so it is never held at full size.
 */
final class ImageProcessor {

	/** Larger images are refused: 40 megapixels is above any phone camera and a bit over 150 MB as pixels. */
	static final long MAX_PIXELS = 40_000_000L;

	/** Above this many pixels the decoder skips pixels while reading instead of loading the whole image. */
	private static final long SUBSAMPLE_ABOVE_PIXELS = 4_000_000L;

	static {
		// Spring Boot's launcher loads the libraries through its own class loader; scanning here, on the starting thread,
		// is what lets ImageIO see the WebP and JPEG readers inside the packaged jar.
		ImageIO.scanForPlugins();
		ImageIO.setUseCache(false);
	}

	private ImageProcessor() {
	}

	/**
	 * @return the PNG bytes of every rendition, in the order given
	 * @throws ApiException 400 {@code files.invalid_image} when the bytes cannot be decoded or the image is too large
	 */
	static Map<String, byte[]> render(byte[] source, List<Rendition> renditions) {
		BufferedImage image = decode(source, renditions.stream().mapToInt(Rendition::pixels).max().orElse(1));
		BufferedImage upright = orient(image, exifOrientation(source));
		BufferedImage square = null;
		Map<String, byte[]> result = new LinkedHashMap<>();
		for (Rendition rendition : renditions) {
			BufferedImage made;
			if (rendition.shape() == Shape.FIT) {
				made = fit(upright, rendition.pixels());
			}
			else {
				if (square == null) {
					square = centreSquare(upright);
				}
				made = scale(square, rendition.pixels(), rendition.pixels());
			}
			result.put(rendition.name(), png(made));
		}
		return result;
	}

	private static BufferedImage decode(byte[] source, int largestTarget) {
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
			Iterator<ImageReader> readers = (input != null) ? ImageIO.getImageReaders(input) : null;
			if (readers == null || !readers.hasNext()) {
				throw invalid("The file could not be read as an image.");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS) {
					throw invalid("The image has too many pixels. Use one below 40 megapixels.");
				}
				ImageReadParam parameters = reader.getDefaultReadParam();
				if ((long) width * height > SUBSAMPLE_ABOVE_PIXELS) {
					// Keep at least twice the largest target on the short side, so the scaling still has detail to work with.
					int step = Math.max(1, Math.min(width, height) / (largestTarget * 2));
					if (step > 1) {
						parameters.setSourceSubsampling(step, step, 0, 0);
					}
				}
				BufferedImage image = reader.read(0, parameters);
				if (image == null) {
					throw invalid("The file could not be read as an image.");
				}
				return image;
			}
			finally {
				reader.dispose();
			}
		}
		catch (IOException | RuntimeException ex) {
			if (ex instanceof ApiException api) {
				throw api;
			}
			// The decoder's message can quote the file's bytes or internal class names, so it is not passed on.
			throw invalid("The file could not be read as an image.");
		}
	}

	/** The EXIF orientation, 1 to 8, of a JPEG; 1 (as it is) for everything else and for a damaged EXIF block. */
	static int exifOrientation(byte[] source) {
		if (source.length < 2 || (source[0] & 0xFF) != 0xFF || (source[1] & 0xFF) != 0xD8) {
			return 1;
		}
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
			List<JPEGSegment> segments = JPEGSegmentUtil.readSegments(input, JPEG.APP1, "Exif");
			if (segments.isEmpty()) {
				return 1;
			}
			InputStream data = segments.get(0).data();
			data.read(); // the pad byte that follows "Exif\0" in a JPEG
			try (ImageInputStream exif = ImageIO.createImageInputStream(data)) {
				CompoundDirectory directory = (CompoundDirectory) new TIFFReader().read(exif);
				Entry entry = directory.getEntryById(TIFF.TAG_ORIENTATION);
				if (entry != null && entry.getValue() instanceof Number number) {
					int value = number.intValue();
					return (value >= 1 && value <= 8) ? value : 1;
				}
			}
		}
		catch (IOException | RuntimeException ex) {
			// A damaged EXIF block only costs the rotation; the image itself is still decoded.
		}
		return 1;
	}

	/** Turns an image upright from its EXIF orientation value (1 is already upright). */
	static BufferedImage orient(BufferedImage image, int orientation) {
		if (orientation <= 1 || orientation > 8) {
			return image;
		}
		int width = image.getWidth();
		int height = image.getHeight();
		AffineTransform transform = new AffineTransform();
		switch (orientation) {
			case 2 -> {
				transform.scale(-1.0, 1.0);
				transform.translate(-width, 0);
			}
			case 3 -> {
				transform.translate(width, height);
				transform.rotate(Math.PI);
			}
			case 4 -> {
				transform.scale(1.0, -1.0);
				transform.translate(0, -height);
			}
			case 5 -> {
				transform.rotate(-Math.PI / 2);
				transform.scale(-1.0, 1.0);
			}
			case 6 -> {
				transform.translate(height, 0);
				transform.rotate(Math.PI / 2);
			}
			case 7 -> {
				transform.scale(-1.0, 1.0);
				transform.translate(-height, 0);
				transform.translate(0, width);
				transform.rotate(3 * Math.PI / 2);
			}
			default -> {
				transform.translate(0, width);
				transform.rotate(3 * Math.PI / 2);
			}
		}
		boolean swapped = orientation >= 5;
		BufferedImage result = new BufferedImage(swapped ? height : width, swapped ? width : height,
				BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D graphics = result.createGraphics();
		try {
			graphics.drawImage(image, transform, null);
		}
		finally {
			graphics.dispose();
		}
		return result;
	}

	private static BufferedImage centreSquare(BufferedImage image) {
		int side = Math.min(image.getWidth(), image.getHeight());
		int x = (image.getWidth() - side) / 2;
		int y = (image.getHeight() - side) / 2;
		BufferedImage square = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D graphics = square.createGraphics();
		try {
			graphics.drawImage(image.getSubimage(x, y, side, side), 0, 0, null);
		}
		finally {
			graphics.dispose();
		}
		return square;
	}

	/**
	 * The whole image with its proportions, its longest side at most {@code longest}. An image that is already small
	 * enough keeps its size; it is still drawn anew, so nothing of the decoded original carries over.
	 */
	private static BufferedImage fit(BufferedImage image, int longest) {
		int width = image.getWidth();
		int height = image.getHeight();
		int side = Math.max(width, height);
		int targetWidth = width;
		int targetHeight = height;
		if (side > longest) {
			double ratio = (double) longest / side;
			targetWidth = Math.max(1, (int) Math.round(width * ratio));
			targetHeight = Math.max(1, (int) Math.round(height * ratio));
		}
		return scale(image, targetWidth, targetHeight);
	}

	/**
	 * Halves while the image is more than twice the target in both directions, then makes the last step, which keeps
	 * shrinking sharp. The result is always an image drawn here, never the one passed in.
	 */
	private static BufferedImage scale(BufferedImage image, int width, int height) {
		BufferedImage current = image;
		while (current.getWidth() / 2 >= width && current.getHeight() / 2 >= height) {
			current = resize(current, current.getWidth() / 2, current.getHeight() / 2);
		}
		boolean drawn = current != image;
		return (drawn && current.getWidth() == width && current.getHeight() == height) ? current
				: resize(current, width, height);
	}

	private static BufferedImage resize(BufferedImage image, int width, int height) {
		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D graphics = result.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.drawImage(image, 0, 0, width, height, null);
		}
		finally {
			graphics.dispose();
		}
		return result;
	}

	private static byte[] png(BufferedImage image) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			if (!ImageIO.write(image, "png", bytes)) {
				throw new IllegalStateException("There is no PNG writer");
			}
			return bytes.toByteArray();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Writing a PNG to memory failed", ex);
		}
	}

	private static ApiException invalid(String message) {
		return ApiException.invalid(FileService.INVALID_IMAGE, message, List.of());
	}

}
