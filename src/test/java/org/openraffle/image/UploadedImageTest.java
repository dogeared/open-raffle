package org.openraffle.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadedImageTest {

    @Test
    void realPicturesAreReEncodedFromTheirPixelsAndKeepTheirSize() throws Exception {
        UploadedImage.Processed jpeg = UploadedImage.process(TestImages.jpeg(640, 480), "image/jpeg", "IMG_0001.JPG");
        assertThat(jpeg.extension()).isEqualTo("jpg");
        assertThat(jpeg.contentType()).isEqualTo("image/jpeg");
        assertThat(jpeg.width()).isEqualTo(640);
        assertThat(jpeg.height()).isEqualTo(480);
        assertThat(UploadedImage.signatureOf(jpeg.bytes())).isEqualTo("jpg");

        UploadedImage.Processed png = UploadedImage.process(TestImages.png(300, 200), "image/png", "box.png");
        assertThat(png.extension()).isEqualTo("png");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(png.bytes()));
        assertThat(decoded.getWidth()).isEqualTo(300);

        // GIFs come out as PNG (first frame), so nothing animated or scripted survives.
        UploadedImage.Processed gif = UploadedImage.process(TestImages.gif(120, 90), "image/gif", "anim.gif");
        assertThat(gif.extension()).isEqualTo("png");
        assertThat(UploadedImage.signatureOf(gif.bytes())).isEqualTo("png");
    }

    @Test
    void bigPicturesAreShrunkToTheLongEdgeLimit() throws Exception {
        UploadedImage.Processed big = UploadedImage.process(TestImages.jpeg(4000, 3000), "image/jpeg", "huge.jpeg");

        assertThat(big.width()).isEqualTo(1600);
        assertThat(big.height()).isEqualTo(1200);
    }

    @Test
    void hugePhotosAreDecodedSubsampledSoTheyNeverFillTheHeapYetComeOutTheRightSize() throws Exception {
        // A 24-megapixel phone photo: decoded at every 3rd pixel (2000×1333) and then scaled.
        assertThat(UploadedImage.subsampling(6000)).isEqualTo(3);
        assertThat(UploadedImage.subsampling(3199)).isEqualTo(1);
        assertThat(UploadedImage.subsampling(3200)).isEqualTo(2);
        assertThat(UploadedImage.subsampling(1600)).isEqualTo(1);

        Path photo = Files.createTempFile("photo", ".jpg");
        try {
            Files.write(photo, TestImages.jpeg(6000, 4000));
            UploadedImage.Processed out = UploadedImage.process(photo, "image/jpeg", "IMG_6000.jpg");
            assertThat(out.width()).isEqualTo(1600);
            assertThat(out.height()).isEqualTo(1067);
            assertThat(UploadedImage.signatureOf(out.bytes())).isEqualTo("jpg");
        } finally {
            Files.deleteIfExists(photo);
        }
    }

    @Test
    void anUploadIsReadFromItsTemporaryFileAndTheFileIsLeftForTheCallerToRemove() throws Exception {
        Path file = Files.createTempFile("upload", ".png");
        try {
            Files.write(file, TestImages.png(50, 40));
            UploadedImage.Processed out = UploadedImage.process(file, "image/png", "box.png");
            assertThat(out.width()).isEqualTo(50);
            assertThat(file).exists();

            Files.write(file, new byte[0]);
            assertThatThrownBy(() -> UploadedImage.process(file, "image/png", "box.png"))
                    .isInstanceOf(InvalidImageException.class).hasMessageContaining("empty");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void metadataDoesNotSurviveReEncoding() throws Exception {
        // A JPEG with an APP1 "Exif" segment smuggled in right after SOI.
        byte[] plain = TestImages.jpeg(64, 64);
        byte[] exif = "Exif\0\0SECRET-LOCATION".getBytes(StandardCharsets.US_ASCII);
        byte[] withExif = new byte[plain.length + exif.length + 4];
        withExif[0] = (byte) 0xFF; withExif[1] = (byte) 0xD8;
        withExif[2] = (byte) 0xFF; withExif[3] = (byte) 0xE1;
        int len = exif.length + 2;
        withExif[4] = (byte) (len >> 8); withExif[5] = (byte) len;
        System.arraycopy(exif, 0, withExif, 6, exif.length);
        System.arraycopy(plain, 2, withExif, 6 + exif.length, plain.length - 2);

        UploadedImage.Processed out = UploadedImage.process(withExif, "image/jpeg", "photo.jpg");

        assertThat(new String(out.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("SECRET-LOCATION");
    }

    @ParameterizedTest
    @CsvSource({
            "shell.php,         image/jpeg, Only JPEG",
            "photo.jpg.php,     image/jpeg, Only JPEG",
            "vector.svg,        image/svg+xml, Only JPEG",
            "pic.webp,          image/webp, Only JPEG",
            "pic.heic,          image/heic, Only JPEG",
            "noextension,       image/jpeg, Only JPEG",
            "photo.jpg,         text/html, Only JPEG",
            "photo.jpg,         application/octet-stream, Only JPEG",
    })
    void namesAndTypesOffTheAllowListAreRefusedBeforeTheBytesAreLookedAt(String name, String type, String message) {
        assertThatThrownBy(() -> UploadedImage.process(TestImages.jpeg(10, 10), type, name))
                .isInstanceOf(InvalidImageException.class).hasMessageContaining(message);
    }

    @Test
    void bytesMustCarryTheSignatureTheNameAndTypeClaim() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> UploadedImage.process(html, "image/png", "page.png"))
                .isInstanceOf(InvalidImageException.class).hasMessageContaining("not a JPEG, PNG or GIF");

        // A real PNG renamed .jpg: contents and name disagree.
        assertThatThrownBy(() -> UploadedImage.process(TestImages.png(10, 10), "image/jpeg", "renamed.jpg"))
                .isInstanceOf(InvalidImageException.class).hasMessageContaining("contents are a PNG");

        // A valid signature followed by garbage is not decodable: a polyglot does not get through.
        byte[] fakeGif = Arrays.copyOf("GIF89a<?php system($_GET['c']); ?>".getBytes(StandardCharsets.US_ASCII), 60);
        assertThatThrownBy(() -> UploadedImage.process(fakeGif, "image/gif", "poly.gif"))
                .isInstanceOf(InvalidImageException.class).hasMessageContaining("could not be read");
    }

    @Test
    void sizeAndPixelLimitsApply() {
        byte[] tooBig = new byte[(int) UploadedImage.MAX_BYTES + 1];
        tooBig[0] = (byte) 0xFF; tooBig[1] = (byte) 0xD8; tooBig[2] = (byte) 0xFF;
        assertThatThrownBy(() -> UploadedImage.process(tooBig, "image/jpeg", "big.jpg"))
                .isInstanceOf(InvalidImageException.class).hasMessageContaining("larger than 10 MB");

        assertThatThrownBy(() -> UploadedImage.process(new byte[0], "image/jpeg", "empty.jpg"))
                .isInstanceOf(InvalidImageException.class).hasMessageContaining("empty");

        // A PNG header claiming 50,000 × 50,000 pixels is refused from the header alone.
        byte[] bomb = pngHeader(50_000, 50_000);
        assertThatThrownBy(() -> UploadedImage.process(bomb, "image/png", "bomb.png"))
                .isInstanceOf(InvalidImageException.class).hasMessageContaining("40 megapixels");
    }

    @Test
    void extensionsComeFromTheLastDotOfTheBaseName() {
        assertThat(UploadedImage.extensionOf("a/b/c.PNG")).isEqualTo("png");
        assertThat(UploadedImage.extensionOf("C:\\\\photos\\\\x.jpeg")).isEqualTo("jpeg");
        assertThat(UploadedImage.extensionOf("archive.tar.gz")).isEqualTo("gz");
        assertThat(UploadedImage.extensionOf("noext")).isEmpty();
        assertThat(UploadedImage.extensionOf(null)).isEmpty();
    }

    private static byte[] pngHeader(int width, int height) {
        byte[] b = new byte[64];
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
        System.arraycopy(sig, 0, b, 0, sig.length);
        b[16] = (byte) (width >> 24); b[17] = (byte) (width >> 16); b[18] = (byte) (width >> 8); b[19] = (byte) width;
        b[20] = (byte) (height >> 24); b[21] = (byte) (height >> 16); b[22] = (byte) (height >> 8); b[23] = (byte) height;
        b[24] = 8; b[25] = 2; // 8-bit RGB
        return b;
    }
}
