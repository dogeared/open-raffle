package org.openraffle.image;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExifOrientationTest {

    /** A JPEG with an APP1 Exif segment whose only tag is Orientation. */
    static byte[] withOrientation(byte[] jpeg, int orientation, ByteOrder order) {
        ByteBuffer tiff = ByteBuffer.allocate(8 + 2 + 12 + 4).order(order);
        tiff.put(order == ByteOrder.BIG_ENDIAN ? "MM".getBytes() : "II".getBytes());
        tiff.putShort((short) 42);
        tiff.putInt(8);            // first IFD right after the header
        tiff.putShort((short) 1);  // one entry
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        tiff.putInt(0);            // no next IFD
        byte[] payload = tiff.array();
        int length = 2 + 6 + payload.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF); out.write(0xD8);
        out.write(0xFF); out.write(0xE1);
        out.write(length >> 8); out.write(length & 0xFF);
        out.writeBytes("Exif\0\0".getBytes());
        out.writeBytes(payload);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    @Test
    void readsTheTagInEitherByteOrderAndDefaultsToUpright() {
        byte[] plain = TestImages.jpeg(40, 20);

        assertThat(ExifOrientation.of(plain)).isEqualTo(1);
        assertThat(ExifOrientation.of(withOrientation(plain, 6, ByteOrder.BIG_ENDIAN))).isEqualTo(6);
        assertThat(ExifOrientation.of(withOrientation(plain, 8, ByteOrder.LITTLE_ENDIAN))).isEqualTo(8);
        assertThat(ExifOrientation.of(withOrientation(plain, 9, ByteOrder.BIG_ENDIAN))).isEqualTo(1); // out of range
        assertThat(ExifOrientation.of(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0, 4})).isEqualTo(1); // truncated
        assertThat(ExifOrientation.of(TestImages.png(5, 5))).isEqualTo(1);
    }

    @Test
    void appliesRotationsAndFlipsToThePixels() {
        // 4×2: top row red, bottom row blue; left column also marked green at (0,0).
        BufferedImage img = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 4; x++) { img.setRGB(x, 0, Color.RED.getRGB()); img.setRGB(x, 1, Color.BLUE.getRGB()); }
        img.setRGB(0, 0, Color.GREEN.getRGB());

        assertThat(ExifOrientation.apply(img, 1)).isSameAs(img);

        BufferedImage r6 = ExifOrientation.apply(img, 6); // 90° clockwise: 2×4, green ends up top-right
        assertThat(r6.getWidth()).isEqualTo(2);
        assertThat(r6.getHeight()).isEqualTo(4);
        assertThat(new Color(r6.getRGB(1, 0))).isEqualTo(Color.GREEN);
        assertThat(new Color(r6.getRGB(0, 3))).isEqualTo(Color.BLUE);

        BufferedImage r3 = ExifOrientation.apply(img, 3); // 180°: green bottom-right
        assertThat(new Color(r3.getRGB(3, 1))).isEqualTo(Color.GREEN);

        BufferedImage r8 = ExifOrientation.apply(img, 8); // 90° counter-clockwise: green bottom-left
        assertThat(r8.getWidth()).isEqualTo(2);
        assertThat(new Color(r8.getRGB(0, 3))).isEqualTo(Color.GREEN);

        BufferedImage r2 = ExifOrientation.apply(img, 2); // mirrored: green top-right
        assertThat(new Color(r2.getRGB(3, 0))).isEqualTo(Color.GREEN);
    }

    @Test
    void aSidewaysPhoneSelfieComesOutUprightFromProcessing() throws Exception {
        byte[] sideways = withOrientation(TestImages.jpeg(300, 200), 6, ByteOrder.BIG_ENDIAN);
        Path file = Files.createTempFile("selfie", ".jpg");
        try {
            Files.write(file, sideways);
            UploadedImage.Processed out = UploadedImage.process(file, "image/jpeg", "IMG_1.jpg");
            assertThat(out.width()).isEqualTo(200);
            assertThat(out.height()).isEqualTo(300);
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(out.bytes()));
            assertThat(decoded.getWidth()).isEqualTo(200);
            assertThat(ExifOrientation.of(out.bytes())).isEqualTo(1); // the tag is gone with the rest of the metadata
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
