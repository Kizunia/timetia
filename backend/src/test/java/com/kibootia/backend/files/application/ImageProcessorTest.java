package com.kibootia.backend.files.application;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.files.FileCategory.Rendition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What happens to an uploaded image (ADR 0029): every rendition is a new PNG made from the pixels, either the centred
 * square or the whole image with its proportions, turned upright by the EXIF orientation, with nothing of the original
 * left in it, and an image too large to decode safely is refused before it is decoded.
 */
class ImageProcessorTest {

	private static final List<Rendition> RENDITIONS = List.of(new Rendition("small", 64), new Rendition("large", 256));

	/** A 1x1 lossless WebP, the smallest valid file of that type. */
	private static final byte[] TINY_WEBP = Base64.getDecoder().decode("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==");

	// --- fixtures ---------------------------------------------------------------------------------------------------

	/** Left half red, right half blue. */
	private static BufferedImage redBlue(int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(Color.RED);
		graphics.fillRect(0, 0, width / 2, height);
		graphics.setColor(Color.BLUE);
		graphics.fillRect(width / 2, 0, width - width / 2, height);
		graphics.dispose();
		return image;
	}

	private static byte[] encode(BufferedImage image, String format) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		assertThat(ImageIO.write(image, format, bytes)).isTrue();
		return bytes.toByteArray();
	}

	/** A segment of a JPEG: the marker, the length (which counts its own two bytes) and the data. */
	private static byte[] segment(int marker, byte[] data) {
		ByteBuffer buffer = ByteBuffer.allocate(4 + data.length);
		buffer.put((byte) 0xFF).put((byte) marker).putShort((short) (data.length + 2)).put(data);
		return buffer.array();
	}

	/** An EXIF block with one entry, the orientation, and a text that must not survive. */
	private static byte[] exifWithOrientation(int orientation, String secret) {
		byte[] text = secret.getBytes(StandardCharsets.US_ASCII);
		ByteBuffer tiff = ByteBuffer.allocate(8 + 2 + 12 + 4 + text.length + 1).order(java.nio.ByteOrder.LITTLE_ENDIAN);
		tiff.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
		tiff.putShort((short) 1);
		tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
		tiff.putInt(0);
		tiff.put(text).put((byte) 0);
		ByteBuffer data = ByteBuffer.allocate(6 + tiff.capacity());
		data.put("Exif\0\0".getBytes(StandardCharsets.US_ASCII)).put(tiff.array());
		return data.array();
	}

	/** The JPEG with extra segments right after its start marker, where a camera puts its EXIF and comment. */
	private static byte[] withSegments(byte[] jpeg, byte[]... segments) {
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		result.write(jpeg, 0, 2);
		for (byte[] segment : segments) {
			result.write(segment, 0, segment.length);
		}
		result.write(jpeg, 2, jpeg.length - 2);
		return result.toByteArray();
	}

	private static BufferedImage decode(byte[] png) throws IOException {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
		assertThat(image).as("the rendition is a readable image").isNotNull();
		return image;
	}

	private static boolean near(int rgb, Color expected) {
		Color actual = new Color(rgb, true);
		return Math.abs(actual.getRed() - expected.getRed()) < 60 && Math.abs(actual.getGreen() - expected.getGreen()) < 60
				&& Math.abs(actual.getBlue() - expected.getBlue()) < 60;
	}

	// --- formats and sizes ------------------------------------------------------------------------------------------

	@ParameterizedTest(name = "{0} becomes a 64 and a 256 pixel PNG")
	@ValueSource(strings = { "png", "jpeg" })
	@DisplayName("a JPEG or PNG becomes one square PNG per rendition, at exactly its size")
	void rendersEveryRendition(String format) throws IOException {
		Map<String, byte[]> result = ImageProcessor.render(encode(redBlue(300, 200), format), RENDITIONS);

		assertThat(result.keySet()).containsExactly("small", "large");
		assertThat(decode(result.get("small")).getWidth()).isEqualTo(64);
		assertThat(decode(result.get("small")).getHeight()).isEqualTo(64);
		assertThat(decode(result.get("large")).getWidth()).isEqualTo(256);
		assertThat(decode(result.get("large")).getHeight()).isEqualTo(256);
		assertThat(result.get("large")).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
	}

	@Test
	@DisplayName("a WebP is accepted")
	void acceptsWebp() throws IOException {
		Map<String, byte[]> result = ImageProcessor.render(TINY_WEBP, RENDITIONS);

		assertThat(decode(result.get("large")).getWidth()).isEqualTo(256);
	}

	@Test
	@DisplayName("a wide image is cut to its centred square, and a small one is scaled up to the fixed size")
	void cropsToTheCentre() throws IOException {
		// 40x20: the centred square is x 10..30, so its left half is red and its right half blue.
		BufferedImage large = decode(ImageProcessor.render(encode(redBlue(40, 20), "png"), RENDITIONS).get("large"));

		assertThat(large.getWidth()).isEqualTo(256);
		assertThat(near(large.getRGB(20, 128), Color.RED)).isTrue();
		assertThat(near(large.getRGB(235, 128), Color.BLUE)).isTrue();
	}

	@Test
	@DisplayName("a large photograph is read with subsampling and still gives the right result")
	void largeImages() throws IOException {
		BufferedImage large = decode(ImageProcessor.render(encode(redBlue(3000, 2000), "jpeg"), RENDITIONS).get("large"));

		assertThat(large.getWidth()).isEqualTo(256);
		assertThat(near(large.getRGB(20, 128), Color.RED)).isTrue();
		assertThat(near(large.getRGB(235, 128), Color.BLUE)).isTrue();
	}

	// --- fit renditions ---------------------------------------------------------------------------------------------

	private static final List<Rendition> FIT = List.of(Rendition.fit("preview", 100), Rendition.fit("full", 400));

	@Test
	@DisplayName("a fit rendition keeps a landscape image whole, with its longest side at the rendition's size")
	void fitKeepsALandscapeImage() throws IOException {
		Map<String, byte[]> result = ImageProcessor.render(encode(redBlue(1200, 600), "png"), FIT);

		BufferedImage preview = decode(result.get("preview"));
		BufferedImage full = decode(result.get("full"));
		assertThat(result.keySet()).containsExactly("preview", "full");
		assertThat(preview.getWidth()).isEqualTo(100);
		assertThat(preview.getHeight()).isEqualTo(50);
		assertThat(full.getWidth()).isEqualTo(400);
		assertThat(full.getHeight()).isEqualTo(200);
		// Nothing is cut off: the red left edge and the blue right edge are both still there.
		assertThat(near(full.getRGB(2, 100), Color.RED)).isTrue();
		assertThat(near(full.getRGB(397, 100), Color.BLUE)).isTrue();
	}

	@Test
	@DisplayName("a fit rendition keeps a portrait image whole too")
	void fitKeepsAPortraitImage() throws IOException {
		BufferedImage preview = decode(ImageProcessor.render(encode(redBlue(300, 900), "png"), FIT).get("preview"));

		assertThat(preview.getWidth()).isEqualTo(33);
		assertThat(preview.getHeight()).isEqualTo(100);
	}

	@Test
	@DisplayName("a fit rendition never enlarges an image that is already small")
	void fitDoesNotEnlarge() throws IOException {
		Map<String, byte[]> result = ImageProcessor.render(encode(redBlue(60, 40), "png"), FIT);

		for (String name : List.of("preview", "full")) {
			BufferedImage image = decode(result.get(name));
			assertThat(image.getWidth()).as(name + " width").isEqualTo(60);
			assertThat(image.getHeight()).as(name + " height").isEqualTo(40);
		}
	}

	@Test
	@DisplayName("a fit rendition turns a rotated image upright, so the long side follows the picture")
	void fitAppliesExifOrientation() throws IOException {
		// Stored 400x200 (landscape) with orientation 6: upright it is 200x400 (portrait), red above blue.
		byte[] jpeg = withSegments(encode(redBlue(400, 200), "jpeg"), segment(0xE1, exifWithOrientation(6, "x")));

		BufferedImage full = decode(ImageProcessor.render(jpeg, FIT).get("full"));

		assertThat(full.getWidth()).isEqualTo(200);
		assertThat(full.getHeight()).isEqualTo(400);
		assertThat(near(full.getRGB(100, 5), Color.RED)).as("top is red").isTrue();
		assertThat(near(full.getRGB(100, 395), Color.BLUE)).as("bottom is blue").isTrue();
	}

	@Test
	@DisplayName("a large photograph is read with subsampling for a fit rendition and keeps its proportions")
	void fitLargeImages() throws IOException {
		BufferedImage full = decode(ImageProcessor.render(encode(redBlue(3000, 2000), "jpeg"), FIT).get("full"));

		assertThat(full.getWidth()).isEqualTo(400);
		assertThat(full.getHeight()).isEqualTo(267);
		assertThat(near(full.getRGB(10, 130), Color.RED)).isTrue();
		assertThat(near(full.getRGB(390, 130), Color.BLUE)).isTrue();
	}

	@Test
	@DisplayName("square and fit renditions can be mixed in one category")
	void mixedShapes() throws IOException {
		List<Rendition> mixed = List.of(new Rendition("thumb", 64), Rendition.fit("full", 400));

		Map<String, byte[]> result = ImageProcessor.render(encode(redBlue(800, 400), "png"), mixed);

		assertThat(decode(result.get("thumb")).getWidth()).isEqualTo(64);
		assertThat(decode(result.get("thumb")).getHeight()).isEqualTo(64);
		assertThat(decode(result.get("full")).getWidth()).isEqualTo(400);
		assertThat(decode(result.get("full")).getHeight()).isEqualTo(200);
	}

	@Test
	@DisplayName("EXIF, a comment and trailing data are not in a fit rendition either")
	void fitStripsMetadata() throws IOException {
		byte[] jpeg = withSegments(encode(redBlue(300, 200), "jpeg"),
				segment(0xE1, exifWithOrientation(1, "GPS-LATITUDE-52.5200-SECRET")),
				segment(0xFE, "COMMENT-SECRET-OWNER-NAME".getBytes(StandardCharsets.US_ASCII)));

		for (byte[] rendition : ImageProcessor.render(jpeg, FIT).values()) {
			String text = new String(rendition, StandardCharsets.ISO_8859_1);
			assertThat(text).doesNotContain("SECRET").doesNotContain("GPS").doesNotContain("Exif").doesNotContain("COMMENT");
		}
	}

	// --- orientation ------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("an image with EXIF orientation 6 is turned upright before it is cropped")
	void appliesExifOrientation() throws IOException {
		// Stored 40x20 with the left half red. Orientation 6 means "turn 90 degrees clockwise to view": the red half ends
		// up on top. The centred square of the upright 20x40 image is its middle 20 rows, red above and blue below.
		byte[] jpeg = withSegments(encode(redBlue(40, 20), "jpeg"), segment(0xE1, exifWithOrientation(6, "x")));

		assertThat(ImageProcessor.exifOrientation(jpeg)).isEqualTo(6);
		BufferedImage large = decode(ImageProcessor.render(jpeg, RENDITIONS).get("large"));

		assertThat(near(large.getRGB(128, 20), Color.RED)).as("top is red").isTrue();
		assertThat(near(large.getRGB(128, 235), Color.BLUE)).as("bottom is blue").isTrue();
	}

	@Test
	@DisplayName("an image with no EXIF, or with a damaged EXIF block, is used as it is")
	void noOrientation() throws IOException {
		byte[] plain = encode(redBlue(40, 20), "jpeg");
		byte[] damaged = withSegments(plain, segment(0xE1, "Exif\0\0not a tiff header at all".getBytes(StandardCharsets.US_ASCII)));

		assertThat(ImageProcessor.exifOrientation(plain)).isEqualTo(1);
		assertThat(ImageProcessor.exifOrientation(damaged)).isEqualTo(1);
		assertThat(ImageProcessor.exifOrientation(encode(redBlue(4, 4), "png"))).isEqualTo(1);
		BufferedImage large = decode(ImageProcessor.render(damaged, RENDITIONS).get("large"));
		assertThat(near(large.getRGB(20, 128), Color.RED)).isTrue();
	}

	@ParameterizedTest(name = "orientation {0}")
	@ValueSource(ints = { 1, 2, 3, 4, 5, 6, 7, 8 })
	@DisplayName("each of the eight EXIF orientations moves every pixel to the place the specification says")
	void everyOrientation(int orientation) {
		int width = 3;
		int height = 2;
		BufferedImage source = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				source.setRGB(x, y, 0xFF000000 | (x << 16) | (y << 8) | 0x7F);
			}
		}

		BufferedImage result = ImageProcessor.orient(source, orientation);

		boolean swapped = orientation >= 5;
		assertThat(result.getWidth()).isEqualTo(swapped ? height : width);
		assertThat(result.getHeight()).isEqualTo(swapped ? width : height);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int[] target = switch (orientation) {
					case 2 -> new int[] { width - 1 - x, y };
					case 3 -> new int[] { width - 1 - x, height - 1 - y };
					case 4 -> new int[] { x, height - 1 - y };
					case 5 -> new int[] { y, x };
					case 6 -> new int[] { height - 1 - y, x };
					case 7 -> new int[] { height - 1 - y, width - 1 - x };
					case 8 -> new int[] { y, width - 1 - x };
					default -> new int[] { x, y };
				};
				assertThat(result.getRGB(target[0], target[1])).as("pixel (%d,%d)", x, y).isEqualTo(source.getRGB(x, y));
			}
		}
	}

	// --- nothing of the original survives ---------------------------------------------------------------------------

	@Test
	@DisplayName("EXIF, a comment and trailing data in the original are not in any rendition")
	void stripsMetadata() throws IOException {
		byte[] jpeg = withSegments(encode(redBlue(120, 120), "jpeg"),
				segment(0xE1, exifWithOrientation(1, "GPS-LATITUDE-52.5200-SECRET")),
				segment(0xFE, "COMMENT-SECRET-OWNER-NAME".getBytes(StandardCharsets.US_ASCII)));
		ByteArrayOutputStream withTrailer = new ByteArrayOutputStream();
		withTrailer.write(jpeg);
		withTrailer.write("TRAILING-SECRET-DATA".getBytes(StandardCharsets.US_ASCII));

		for (byte[] rendition : ImageProcessor.render(withTrailer.toByteArray(), RENDITIONS).values()) {
			String text = new String(rendition, StandardCharsets.ISO_8859_1);
			assertThat(text).doesNotContain("SECRET").doesNotContain("GPS").doesNotContain("Exif").doesNotContain("COMMENT");
		}
	}

	// --- refusals ---------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("an image with more than 40 million pixels is refused before it is decoded")
	void refusesPixelBombs() throws IOException {
		// Only the header of a 8000 x 8000 PNG: the decoder reads the size from it and the limit is applied at once.
		byte[] header = new byte[13];
		ByteBuffer.wrap(header).putInt(8000).putInt(8000).put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
		ByteArrayOutputStream png = new ByteArrayOutputStream();
		png.write(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A });
		png.write(ByteBuffer.allocate(4).putInt(13).array());
		byte[] type = "IHDR".getBytes(StandardCharsets.US_ASCII);
		png.write(type);
		png.write(header);
		CRC32 crc = new CRC32();
		crc.update(type);
		crc.update(header);
		png.write(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());

		assertThatThrownBy(() -> ImageProcessor.render(png.toByteArray(), RENDITIONS)).isInstanceOfSatisfying(ApiException.class,
				(ex) -> {
					assertThat(ex.code()).isEqualTo(FileService.INVALID_IMAGE);
					assertThat(ex.status().value()).isEqualTo(400);
					assertThat(ex.getMessage()).contains("pixels");
				});
	}

	@Test
	@DisplayName("bytes that are not an image, or a damaged one, are refused without echoing the decoder's message")
	void refusesWhatCannotBeDecoded() throws IOException {
		byte[] damaged = encode(redBlue(100, 100), "png");
		byte[] truncated = java.util.Arrays.copyOf(damaged, damaged.length / 2);

		for (byte[] bytes : List.of("just some text".getBytes(StandardCharsets.US_ASCII), truncated, new byte[] { 1, 2, 3 })) {
			assertThatThrownBy(() -> ImageProcessor.render(bytes, RENDITIONS)).isInstanceOfSatisfying(ApiException.class,
					(ex) -> {
						assertThat(ex.code()).isEqualTo(FileService.INVALID_IMAGE);
						assertThat(ex.getMessage()).isEqualTo("The file could not be read as an image.");
					});
		}
	}

}
