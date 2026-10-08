package org.openraffle.image;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ColourProfilesTest {

    /** The PNG with an extra chunk spliced in right after IHDR. */
    static byte[] withChunk(byte[] png, String type, byte[] data) {
        int ihdrEnd = 8 + 4 + 4 + 13 + 4;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(png, 0, ihdrEnd);
        ByteBuffer len = ByteBuffer.allocate(4).putInt(data.length);
        out.write(len.array(), 0, 4);
        byte[] typeBytes = type.getBytes(StandardCharsets.ISO_8859_1);
        out.write(typeBytes, 0, 4);
        out.write(data, 0, data.length);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.write(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array(), 0, 4);
        out.write(png, ihdrEnd, png.length - ihdrEnd);
        return out.toByteArray();
    }

    static byte[] iccp(ICC_Profile profile) {
        byte[] raw = profile.getData();
        Deflater deflater = new Deflater();
        deflater.setInput(raw);
        deflater.finish();
        byte[] buf = new byte[raw.length + 1024];
        int n = deflater.deflate(buf);
        deflater.end();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("Linear\0\0".getBytes(StandardCharsets.ISO_8859_1));
        out.write(buf, 0, n);
        return out.toByteArray();
    }

    @Test
    void pqCurveAndSrgbEncodingMatchTheStandards() {
        assertThat(ColourProfiles.pqToNits(0)).isEqualTo(0);
        assertThat(ColourProfiles.pqToNits(1)).isCloseTo(10000, within(1.0));
        assertThat(ColourProfiles.pqToNits(0.5081)).isCloseTo(100, within(1.0));   // the well-known 100-nit code value
        assertThat(ColourProfiles.pqToNits(0.5806)).isCloseTo(203, within(2.0));   // broadcast reference white
        assertThat(ColourProfiles.encode(0)).isEqualTo(0);
        assertThat(ColourProfiles.encode(1)).isEqualTo(255);
        assertThat(ColourProfiles.encode(5)).isEqualTo(255);                        // highlights clip
        assertThat(ColourProfiles.encode(0.2140)).isBetween(127, 128);              // sRGB mid grey
    }

    @Test
    void pqWhiteBecomesWhiteAndP3RedBecomesAVividSrgbRed() {
        BufferedImage pq = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
        // 350 nits in PQ is ~0.63; a P3 red at that level, and a white at that level.
        int code = (int) Math.round(0.6309 * 255);
        pq.setRGB(0, 0, (code << 16) | (code << 8) | code);
        pq.setRGB(1, 0, code << 16);

        BufferedImage srgb = ColourProfiles.fromPq(pq, ColourProfiles.P3_TO_XYZ);

        int white = srgb.getRGB(0, 0) & 0xFFFFFF;
        assertThat((white >> 16) & 255).isGreaterThan(245);
        assertThat(white & 255).isGreaterThan(245);
        int red = srgb.getRGB(1, 0) & 0xFFFFFF;
        assertThat((red >> 16) & 255).isEqualTo(255);  // P3 red is outside sRGB: it clips to full red
        assertThat((red >> 8) & 255).isLessThan(40);
        assertThat(red & 255).isLessThan(40);
    }

    @Test
    void anOrdinaryProfileInAPngIsAppliedAndSrgbIsLeftAlone() throws Exception {
        // Mid grey (128) in a *linear* profile is a light grey (~188) once shown as sRGB.
        BufferedImage grey = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) grey.setRGB(x, y, 0x808080);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(grey, "png", png);
        ICC_Profile linear = ICC_Profile.getInstance(ColorSpace.CS_LINEAR_RGB);
        byte[] tagged = withChunk(png.toByteArray(), "iCCP", iccp(linear));
        Path file = Files.createTempFile("linear", ".png");
        try {
            Files.write(file, tagged);
            assertThat(ColourProfiles.fromPng(file)).isPresent();
            UploadedImage.Processed out = UploadedImage.process(file, "image/png", "grey.png");
            int v = ImageIO.read(new ByteArrayInputStream(out.bytes())).getRGB(1, 1) & 255;
            assertThat(v).isBetween(180, 196);

            Files.write(file, png.toByteArray()); // no profile: values are taken as sRGB and kept
            assertThat(ColourProfiles.fromPng(file)).isEmpty();
            out = UploadedImage.process(file, "image/png", "grey.png");
            assertThat(ImageIO.read(new ByteArrayInputStream(out.bytes())).getRGB(1, 1) & 255).isEqualTo(128);
        } finally {
            Files.deleteIfExists(file);
        }
        assertThat(ColourProfiles.toSrgb(grey, ICC_Profile.getInstance(ColorSpace.CS_sRGB))).isSameAs(grey);
    }

    @Test
    void profilesAreRecognisedByTheirDescription() {
        assertThat(ColourProfiles.description(ICC_Profile.getInstance(ColorSpace.CS_sRGB)).toLowerCase()).contains("srgb");
        assertThat(ColourProfiles.isSrgb(ICC_Profile.getInstance(ColorSpace.CS_sRGB))).isTrue();
        assertThat(ColourProfiles.isPq(ICC_Profile.getInstance(ColorSpace.CS_sRGB))).isFalse();
        assertThat(ColourProfiles.isPq(ICC_Profile.getInstance(ColorSpace.CS_LINEAR_RGB))).isFalse();
        assertThat(ColourProfiles.primariesOf(ICC_Profile.getInstance(ColorSpace.CS_sRGB))).isEqualTo(ColourProfiles.SRGB_TO_XYZ);
    }

    @Test
    void chunkParsingStopsAtTheImageDataAndSurvivesTruncation() throws Exception {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(img, "png", png);
        byte[] bytes = png.toByteArray();

        assertThat(ColourProfiles.pngChunk(bytes, "IHDR")).isPresent().get().satisfies(d -> assertThat(d).hasSize(13));
        assertThat(ColourProfiles.pngChunk(bytes, "iCCP")).isEmpty();
        assertThat(ColourProfiles.pngChunk(java.util.Arrays.copyOf(bytes, 20), "IHDR")).isEmpty();
        assertThat(ColourProfiles.pngChunk("not a png".getBytes(), "IHDR")).isEmpty();
        // An eXIf chunk with Orientation 6 is honoured for PNGs too.
        byte[] tiff = java.util.Arrays.copyOfRange(ExifOrientationTest.withOrientation(TestImages.jpeg(8, 8), 6, java.nio.ByteOrder.BIG_ENDIAN), 12, 12 + 26);
        assertThat(ExifOrientation.of(withChunk(bytes, "eXIf", tiff))).isEqualTo(6);
    }
}
