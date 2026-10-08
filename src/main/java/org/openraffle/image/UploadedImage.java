package org.openraffle.image;

import javax.imageio.ImageIO;
import javax.imageio.IIOImage;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * Turns an organizer's upload into a picture the app is willing to keep. Following the
 * OWASP file upload guidance: the name's extension and the declared type must be on the
 * allow-list, the bytes must carry the matching image signature, the file must be under
 * the size and pixel limits, and it must decode as an image; it is then re-encoded from
 * the decoded pixels, which drops metadata (EXIF, location) and anything else hidden in
 * the file, and shrunk to a sensible size. The original bytes are never stored.
 * <p>
 * Memory matters on a small server: an upload is read from a temporary file, never held
 * whole in memory, and a big photo is decoded at a reduced resolution (every n-th pixel)
 * rather than at full size before being shrunk.
 * <p>
 * Colours and orientation survive: JPEGs are decoded with TwelveMonkeys' reader, which
 * applies embedded colour profiles (phone photos are Display P3) the way the camera meant,
 * where the JDK's reader leaves them washed out; a PNG's profile is applied by
 * {@link ColourProfiles}, including the tone-mapping iPhone HDR photos need; and the EXIF
 * orientation is read before the metadata is dropped and applied to the pixels, so
 * portrait photos stay upright.
 */
public final class UploadedImage {

    /** Largest upload accepted, before processing. */
    public static final long MAX_BYTES = 10L * 1024 * 1024;
    /** Largest picture decoded (width × height); above this is a decompression bomb, not a photo. */
    public static final long MAX_PIXELS = 40_000_000L;
    /** Pictures are shrunk so their long edge is at most this. */
    public static final int MAX_EDGE = 1600;
    public static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif");
    public static final Set<String> CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/gif");

    /** The stored form: already re-encoded; {@code extension} is jpg or png. */
    public record Processed(byte[] bytes, String extension, String contentType, int width, int height) {
    }

    private UploadedImage() {
    }

    /** For small inputs already in memory (tests, BGG downloads); uploads use {@link #process(Path, String, String)}. */
    public static Processed process(byte[] bytes, String declaredContentType, String fileName) throws InvalidImageException {
        if (bytes == null || bytes.length == 0) {
            throw new InvalidImageException("The file is empty.");
        }
        try {
            Path temp = Files.createTempFile("open-raffle-upload-", ".bin");
            try {
                Files.write(temp, bytes);
                return process(temp, declaredContentType, fileName);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new InvalidImageException("The picture could not be read.");
        }
    }

    /** Checks and re-encodes the picture in {@code file}, which the caller deletes afterwards. */
    public static Processed process(Path file, String declaredContentType, String fileName) throws InvalidImageException {
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            throw new InvalidImageException("The file could not be read.");
        }
        if (size == 0) {
            throw new InvalidImageException("The file is empty.");
        }
        if (size > MAX_BYTES) {
            throw new InvalidImageException("The file is larger than 10 MB.");
        }
        String extension = extensionOf(fileName);
        if (!EXTENSIONS.contains(extension)) {
            throw new InvalidImageException("Only JPEG, PNG and GIF pictures can be uploaded (the file is \"." + extension + "\").");
        }
        String declared = declaredContentType == null ? "" : declaredContentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
        if (!declared.isEmpty() && !CONTENT_TYPES.contains(declared)) {
            throw new InvalidImageException("Only JPEG, PNG and GIF pictures can be uploaded (the file says it is " + declared + ").");
        }
        String signature = signatureOf(head(file));
        if (signature == null) {
            throw new InvalidImageException("The file is not a JPEG, PNG or GIF picture.");
        }
        if (!signature.equals(extension.equals("jpeg") ? "jpg" : extension)) {
            throw new InvalidImageException("The file's contents are a " + signature.toUpperCase(Locale.ROOT) + " but its name says ." + extension + ".");
        }
        boolean photo = signature.equals("jpg");
        int orientation = ExifOrientation.of(file);
        BufferedImage image = decode(file, signature);
        if (signature.equals("png")) {
            // The JDK's PNG reader hands back raw samples; the profile says what colours they are.
            image = ColourProfiles.toSrgb(image, ColourProfiles.fromPng(file).orElse(null));
        }
        BufferedImage shrunk = ExifOrientation.apply(shrink(image), orientation);
        String outExtension = photo ? "jpg" : "png";
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (photo) {
                writeJpeg(withoutAlpha(shrunk), out);
            } else if (!ImageIO.write(shrunk, "png", out)) {
                throw new InvalidImageException("The picture could not be saved.");
            }
            return new Processed(out.toByteArray(), outExtension, photo ? "image/jpeg" : "image/png", shrunk.getWidth(), shrunk.getHeight());
        } catch (IOException e) {
            throw new InvalidImageException("The picture could not be saved.");
        }
    }

    /** JPEG quality the pictures are saved at; the default (0.75) visibly softens box art. */
    static final float JPEG_QUALITY = 0.9f;

    private static void writeJpeg(BufferedImage image, ByteArrayOutputStream out) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    /** The reader for the format, preferring TwelveMonkeys' JPEG reader over the JDK's. */
    static ImageReader readerFor(String format) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
        ImageReader first = null;
        while (readers.hasNext()) {
            ImageReader reader = readers.next();
            if (reader.getClass().getName().startsWith("com.twelvemonkeys")) {
                return reader;
            }
            if (first == null) {
                first = reader;
            }
        }
        return first;
    }

    /** The lower-cased last extension of a file name; "" when it has none. */
    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        String base = fileName.substring(Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\')) + 1);
        int dot = base.lastIndexOf('.');
        return dot < 0 ? "" : base.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** "jpg", "png" or "gif" by magic bytes, else null. */
    static String signatureOf(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "png";
        }
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8' && (b[4] == '7' || b[4] == '9') && b[5] == 'a') {
            return "gif";
        }
        return null;
    }

    /** The first bytes of the file, for the signature check. */
    private static byte[] head(Path file) throws InvalidImageException {
        try (InputStream in = Files.newInputStream(file)) {
            return in.readNBytes(16);
        } catch (IOException e) {
            throw new InvalidImageException("The file could not be read.");
        }
    }

    /**
     * Decodes from the file. A picture much bigger than {@link #MAX_EDGE} is read with
     * subsampling (every n-th pixel in each direction), so a 24-megapixel photo costs a few
     * megabytes of heap instead of a hundred; it is scaled to its final size afterwards.
     */
    private static BufferedImage decode(Path file, String format) throws InvalidImageException {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            ImageReader reader = readerFor(format);
            if (in == null || reader == null) {
                throw new InvalidImageException("The picture could not be read.");
            }
            try {
                reader.setInput(in, true, true);
                // Size first, from the header, before any pixels are allocated.
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                long pixels = (long) width * height;
                if (pixels > MAX_PIXELS) {
                    throw new InvalidImageException("The picture is too large (over 40 megapixels).");
                }
                if (pixels <= 0) {
                    throw new InvalidImageException("The picture has no size.");
                }
                ImageReadParam param = reader.getDefaultReadParam();
                int step = subsampling(Math.max(width, height));
                if (step > 1) {
                    param.setSourceSubsampling(step, step, 0, 0);
                }
                BufferedImage image = reader.read(0, param);
                if (image == null) {
                    throw new InvalidImageException("The picture could not be read.");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            throw new InvalidImageException("The picture could not be read.");
        }
    }

    /** Every n-th pixel, chosen so the decoded long edge is still at least {@link #MAX_EDGE}. */
    static int subsampling(int longEdge) {
        return Math.max(1, longEdge / MAX_EDGE);
    }

    private static BufferedImage shrink(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int longEdge = Math.max(w, h);
        boolean hasAlpha = image.getColorModel().hasAlpha();
        int type = hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        if (longEdge <= MAX_EDGE && (image.getType() == type)) {
            return image;
        }
        double scale = longEdge <= MAX_EDGE ? 1.0 : (double) MAX_EDGE / longEdge;
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage out = new BufferedImage(nw, nh, type);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, nw, nh, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static BufferedImage withoutAlpha(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }
}
