package org.openraffle.image;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.Raster;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Brings a picture's colours into sRGB, which is what every browser assumes once the
 * app has dropped the file's metadata. A PNG may carry an ICC profile (iCCP chunk): an
 * ordinary one (Display P3, Adobe RGB, ...) is applied with the JDK's colour engine; an
 * HDR one (Apple's "Display P3 Primaries; PQ" from iPhone photos) is tone-mapped by hand,
 * since the colour engine cannot do that and leaves the picture flat or dark.
 */
final class ColourProfiles {

    /**
     * Luminance that becomes white when tone-mapping PQ (HDR) content for an ordinary
     * screen. Matched to what macOS ColorSync produces for an iPhone HDR photo (within
     * two percent); the usual broadcast figure of 203 nits comes out too bright.
     */
    static final double PQ_WHITE_NITS = 350;

    private ColourProfiles() {
    }

    /** The PNG's embedded ICC profile, if it has one it is worth applying. */
    static Optional<ICC_Profile> fromPng(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] head = in.readNBytes(512 * 1024);
            return pngChunk(head, "iCCP").flatMap(ColourProfiles::profileFromIccp);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** The data of the first chunk of that type among the first bytes of a PNG. */
    static Optional<byte[]> pngChunk(byte[] head, String type) {
        if (head.length < 8 || (head[0] & 0xFF) != 0x89 || head[1] != 'P' || head[2] != 'N' || head[3] != 'G') {
            return Optional.empty();
        }
        ByteBuffer b = ByteBuffer.wrap(head);
        int i = 8;
        while (i + 8 <= b.limit()) {
            int length = b.getInt(i);
            String chunk = new String(head, i + 4, 4, StandardCharsets.ISO_8859_1);
            if (length < 0 || i + 8 + length > b.limit()) {
                return Optional.empty(); // truncated or the chunk is further in than we read
            }
            if (chunk.equals(type)) {
                byte[] data = new byte[length];
                System.arraycopy(head, i + 8, data, 0, length);
                return Optional.of(data);
            }
            if (chunk.equals("IDAT")) {
                return Optional.empty(); // profile and EXIF chunks come before the image data
            }
            i += 12 + length;
        }
        return Optional.empty();
    }

    private static Optional<ICC_Profile> profileFromIccp(byte[] iccp) {
        int nul = 0;
        while (nul < iccp.length && iccp[nul] != 0) {
            nul++;
        }
        int start = nul + 2; // name, NUL, compression method
        if (start >= iccp.length) {
            return Optional.empty();
        }
        Inflater inflater = new Inflater();
        inflater.setInput(iccp, start, iccp.length - start);
        try {
            byte[] out = new byte[64 * 1024];
            byte[] profile = new byte[0];
            while (!inflater.finished()) {
                int n = inflater.inflate(out);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                byte[] grown = new byte[profile.length + n];
                System.arraycopy(profile, 0, grown, 0, profile.length);
                System.arraycopy(out, 0, grown, profile.length, n);
                profile = grown;
                if (profile.length > 4 * 1024 * 1024) {
                    return Optional.empty();
                }
            }
            return Optional.of(ICC_Profile.getInstance(profile));
        } catch (DataFormatException | IllegalArgumentException e) {
            return Optional.empty();
        } finally {
            inflater.end();
        }
    }

    /** The profile's description ("Display P3", "Display P3 Primaries; PQ (...)"), or "". */
    static String description(ICC_Profile profile) {
        byte[] desc = profile.getData(ICC_Profile.icSigProfileDescriptionTag);
        if (desc == null || desc.length < 12) {
            return "";
        }
        String type = new String(desc, 0, 4, StandardCharsets.ISO_8859_1);
        try {
            ByteBuffer b = ByteBuffer.wrap(desc);
            if (type.equals("desc")) {
                int length = b.getInt(8);
                return new String(desc, 12, Math.max(0, Math.min(length - 1, desc.length - 12)), StandardCharsets.ISO_8859_1).trim();
            }
            if (type.equals("mluc") && b.getInt(8) > 0) {
                int length = b.getInt(20);
                int offset = b.getInt(24);
                return new String(desc, offset, Math.min(length, desc.length - offset), StandardCharsets.UTF_16BE).trim();
            }
        } catch (RuntimeException e) {
            // odd profile: no description, no special handling
        }
        return "";
    }

    static boolean isPq(ICC_Profile profile) {
        String d = description(profile).toUpperCase(Locale.ROOT);
        return d.contains("PQ") || d.contains("2100") || d.contains("HDR");
    }

    /** Plain sRGB, where conversion would change nothing; "linear sRGB" and the like are not plain. */
    static boolean isSrgb(ICC_Profile profile) {
        String d = description(profile).toUpperCase(Locale.ROOT);
        return d.contains("SRGB") && !d.contains("LINEAR") && !d.contains("PQ") && !d.contains("HLG");
    }

    /** The picture in sRGB, as an RGB image; the same instance when the profile is sRGB already. */
    static BufferedImage toSrgb(BufferedImage image, ICC_Profile profile) {
        if (profile == null || isSrgb(profile)) {
            return image;
        }
        if (isPq(profile)) {
            return fromPq(image, primariesOf(profile));
        }
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        try {
            // Filtering the raster (not the image) makes the engine take the samples as values in
            // the profile's space; filtering the image would first "convert" them from sRGB and
            // change nothing. The profile has three components, so the raster must too.
            BufferedImage threeBands = image.getRaster().getNumBands() == 3 && !image.getColorModel().hasAlpha()
                    ? image : asRgb(image);
            new ColorConvertOp(new ICC_Profile[]{profile, ICC_Profile.getInstance(ColorSpace.CS_sRGB)}, null)
                    .filter(threeBands.getRaster(), out.getRaster());
            return out;
        } catch (RuntimeException e) {
            return image; // a profile the engine cannot apply: better unmanaged than broken
        }
    }

    /** Grey or translucent pictures as plain RGB (flattened on white), so they have three bands. */
    private static BufferedImage asRgb(BufferedImage image) {
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    /** Linear RGB → XYZ (D65) for the profile's primaries: P3 for Apple's HDR, BT.2020 when it says so, else sRGB. */
    static double[] primariesOf(ICC_Profile profile) {
        String d = description(profile).toUpperCase(Locale.ROOT);
        if (d.contains("2020")) {
            return BT2020_TO_XYZ;
        }
        return d.contains("SRGB") ? SRGB_TO_XYZ : P3_TO_XYZ;
    }

    static final double[] P3_TO_XYZ = {0.4865709, 0.2656677, 0.1982173, 0.2289746, 0.6917385, 0.0792869, 0.0000000, 0.0451134, 1.0439444};
    static final double[] BT2020_TO_XYZ = {0.6369580, 0.1446169, 0.1688810, 0.2627002, 0.6779981, 0.0593017, 0.0000000, 0.0280727, 1.0609851};
    static final double[] SRGB_TO_XYZ = {0.4124564, 0.3575761, 0.1804375, 0.2126729, 0.7151522, 0.0721750, 0.0193339, 0.1191920, 0.9503041};
    private static final double[] XYZ_TO_SRGB = {3.2404542, -1.5371385, -0.4985314, -0.9692660, 1.8760108, 0.0415560, 0.0556434, -0.2040259, 1.0572252};

    /**
     * PQ-encoded samples → sRGB: the PQ curve gives absolute luminance, which is scaled so
     * {@link #PQ_WHITE_NITS} becomes white (brighter highlights clip), moved from the
     * source primaries to sRGB's, and gamma-encoded.
     */
    static BufferedImage fromPq(BufferedImage image, double[] toXyz) {
        int w = image.getWidth();
        int h = image.getHeight();
        Raster raster = image.getRaster();
        int bands = raster.getNumBands();
        int bits = image.getSampleModel().getSampleSize(0);
        double max = (1 << bits) - 1;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] px = new int[bands];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                raster.getPixel(x, y, px);
                double r = pqToNits(px[0] / max) / PQ_WHITE_NITS;
                double g = pqToNits(px[bands > 2 ? 1 : 0] / max) / PQ_WHITE_NITS;
                double b = pqToNits(px[bands > 2 ? 2 : 0] / max) / PQ_WHITE_NITS;
                double X = toXyz[0] * r + toXyz[1] * g + toXyz[2] * b;
                double Y = toXyz[3] * r + toXyz[4] * g + toXyz[5] * b;
                double Z = toXyz[6] * r + toXyz[7] * g + toXyz[8] * b;
                int sr = encode(XYZ_TO_SRGB[0] * X + XYZ_TO_SRGB[1] * Y + XYZ_TO_SRGB[2] * Z);
                int sg = encode(XYZ_TO_SRGB[3] * X + XYZ_TO_SRGB[4] * Y + XYZ_TO_SRGB[5] * Z);
                int sb = encode(XYZ_TO_SRGB[6] * X + XYZ_TO_SRGB[7] * Y + XYZ_TO_SRGB[8] * Z);
                out.setRGB(x, y, (sr << 16) | (sg << 8) | sb);
            }
        }
        return out;
    }

    /** SMPTE ST 2084: PQ signal (0–1) → luminance in cd/m² (0–10000). */
    static double pqToNits(double e) {
        final double m1 = 2610.0 / 16384, m2 = 2523.0 / 4096 * 128, c1 = 3424.0 / 4096, c2 = 2413.0 / 4096 * 32, c3 = 2392.0 / 4096 * 32;
        double p = Math.pow(Math.max(0, Math.min(1, e)), 1 / m2);
        return 10000 * Math.pow(Math.max(p - c1, 0) / (c2 - c3 * p), 1 / m1);
    }

    /** Linear sRGB (0–1, clipped) → 8-bit gamma-encoded. */
    static int encode(double linear) {
        double l = Math.max(0, Math.min(1, linear));
        double v = l <= 0.0031308 ? 12.92 * l : 1.055 * Math.pow(l, 1 / 2.4) - 0.055;
        return (int) Math.round(v * 255);
    }
}
