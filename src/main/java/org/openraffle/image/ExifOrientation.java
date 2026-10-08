package org.openraffle.image;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The EXIF Orientation tag (1 = upright … 8 = rotated), read straight from a JPEG's APP1
 * segment or a PNG's eXIf chunk, and the pixel transform that makes the picture upright. Phones
 * store portrait photos sideways with this tag set; since the app drops the metadata, the
 * rotation has to be baked into the pixels instead.
 */
final class ExifOrientation {

    private static final int HEAD_BYTES = 128 * 1024;

    private ExifOrientation() {
    }

    /** 1 when there is no usable tag (also for anything malformed: the picture is then left as decoded). */
    static int of(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return of(in.readNBytes(HEAD_BYTES));
        } catch (IOException | RuntimeException e) {
            return 1;
        }
    }

    static int of(byte[] head) {
        try {
            if (head.length >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
                // A PNG keeps its EXIF in an eXIf chunk: the TIFF structure, without the "Exif\0\0" prefix.
                return ColourProfiles.pngChunk(head, "eXIf")
                        .map(exif -> fromTiff(ByteBuffer.wrap(exif), 0, exif.length))
                        .orElse(1);
            }
            ByteBuffer b = ByteBuffer.wrap(head);
            if (b.remaining() < 4 || (b.get(0) & 0xFF) != 0xFF || (b.get(1) & 0xFF) != 0xD8) {
                return 1;
            }
            int i = 2;
            while (i + 4 <= b.limit() && (b.get(i) & 0xFF) == 0xFF) {
                int marker = b.get(i + 1) & 0xFF;
                if (marker == 0xDA || marker == 0xD9) {
                    return 1; // image data starts: no APP1 came first
                }
                int length = b.getShort(i + 2) & 0xFFFF;
                if (marker == 0xE1 && i + 4 + 6 <= b.limit() && b.get(i + 4) == 'E' && b.get(i + 5) == 'x'
                        && b.get(i + 6) == 'i' && b.get(i + 7) == 'f') {
                    return fromTiff(b, i + 10, Math.min(b.limit(), i + 2 + length));
                }
                i += 2 + length;
            }
        } catch (RuntimeException e) {
            // malformed: treat as upright
        }
        return 1;
    }

    private static int fromTiff(ByteBuffer b, int tiff, int end) {
        if (tiff + 8 > end) {
            return 1;
        }
        ByteOrder order = b.get(tiff) == 'M' ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        ByteBuffer t = b.duplicate().order(order);
        int ifd = tiff + t.getInt(tiff + 4);
        if (ifd + 2 > end) {
            return 1;
        }
        int entries = t.getShort(ifd) & 0xFFFF;
        for (int n = 0; n < entries; n++) {
            int entry = ifd + 2 + n * 12;
            if (entry + 12 > end) {
                return 1;
            }
            int tag = t.getShort(entry) & 0xFFFF;
            if (tag == 0x0112) {
                int value = t.getShort(entry + 8) & 0xFFFF;
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    /** The picture turned and/or flipped so it is upright; the same instance when nothing is needed. */
    static BufferedImage apply(BufferedImage image, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return image;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        boolean sideways = orientation >= 5;
        AffineTransform at = new AffineTransform();
        switch (orientation) {
            case 2 -> { at.translate(w, 0); at.scale(-1, 1); }
            case 3 -> { at.translate(w, h); at.rotate(Math.PI); }
            case 4 -> { at.translate(0, h); at.scale(1, -1); }
            case 5 -> { at.rotate(Math.PI / 2); at.scale(1, -1); }
            case 6 -> { at.translate(h, 0); at.rotate(Math.PI / 2); }
            case 7 -> { at.translate(h, 0); at.rotate(Math.PI / 2); at.translate(w, 0); at.scale(-1, 1); }
            case 8 -> { at.translate(0, w); at.rotate(-Math.PI / 2); }
            default -> { }
        }
        int type = image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage out = new BufferedImage(sideways ? h : w, sideways ? w : h, type);
        new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR).filter(image, out);
        return out;
    }
}
