package org.openraffle.image;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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

    public static Processed process(byte[] bytes, String declaredContentType, String fileName) throws InvalidImageException {
        if (bytes == null || bytes.length == 0) {
            throw new InvalidImageException("The file is empty.");
        }
        if (bytes.length > MAX_BYTES) {
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
        String signature = signatureOf(bytes);
        if (signature == null) {
            throw new InvalidImageException("The file is not a JPEG, PNG or GIF picture.");
        }
        if (!signature.equals(extension.equals("jpeg") ? "jpg" : extension)) {
            throw new InvalidImageException("The file's contents are a " + signature.toUpperCase(Locale.ROOT) + " but its name says ." + extension + ".");
        }
        BufferedImage image = decode(bytes, signature);
        BufferedImage shrunk = shrink(image);
        boolean photo = signature.equals("jpg");
        String outExtension = photo ? "jpg" : "png";
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BufferedImage toWrite = photo ? withoutAlpha(shrunk) : shrunk;
            if (!ImageIO.write(toWrite, photo ? "jpg" : "png", out)) {
                throw new InvalidImageException("The picture could not be saved.");
            }
            return new Processed(out.toByteArray(), outExtension, photo ? "image/jpeg" : "image/png", shrunk.getWidth(), shrunk.getHeight());
        } catch (IOException e) {
            throw new InvalidImageException("The picture could not be saved.");
        }
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

    private static BufferedImage decode(byte[] bytes, String format) throws InvalidImageException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
            if (in == null || !readers.hasNext()) {
                throw new InvalidImageException("The picture could not be read.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                // Size first, from the header, before any pixels are allocated.
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw new InvalidImageException("The picture is too large (over 40 megapixels).");
                }
                if (pixels <= 0) {
                    throw new InvalidImageException("The picture has no size.");
                }
                BufferedImage image = reader.read(0);
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
