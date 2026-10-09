package org.openraffle.image;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PictureUploadHandlerTest {

    private final PictureUploadHandler handler = new PictureUploadHandler((meta, file) -> { });

    @Test
    void theServerEnforcesTheSizeAndCountLimitsItselfNotJustTheBrowser() {
        assertThat(handler.getFileSizeMax()).isEqualTo(10L * 1024 * 1024);
        assertThat(handler.getRequestSizeMax()).isBetween(handler.getFileSizeMax(), handler.getFileSizeMax() + 1024 * 1024);
        assertThat(handler.getFileCountMax()).isEqualTo(1);
        assertThat(PictureUploadHandler.MAX_IN_FLIGHT).isLessThanOrEqualTo(8);
    }

    @Test
    void anUploadIsRefusedBeforeAnyByteIsReadWhenItCannotBeAPicture() {
        assertThatCode(() -> PictureUploadHandler.check("IMG_1.JPG", "image/jpeg", 5_000_000)).doesNotThrowAnyException();
        assertThatCode(() -> PictureUploadHandler.check("box.png", "image/png; charset=binary", 10)).doesNotThrowAnyException();
        assertThatCode(() -> PictureUploadHandler.check("anim.gif", null, -1)).doesNotThrowAnyException(); // unknown type/size: checked after upload

        assertThatThrownBy(() -> PictureUploadHandler.check("big.jpg", "image/jpeg", 11L * 1024 * 1024))
                .isInstanceOf(IOException.class).hasMessageContaining("10 MB");
        assertThatThrownBy(() -> PictureUploadHandler.check("shell.php", "image/jpeg", 10))
                .isInstanceOf(IOException.class).hasMessageContaining("Only JPEG");
        assertThatThrownBy(() -> PictureUploadHandler.check("movie.mp4", "video/mp4", 10))
                .isInstanceOf(IOException.class).hasMessageContaining("Only JPEG");
        assertThatThrownBy(() -> PictureUploadHandler.check("photo.jpg", "application/octet-stream", 10))
                .isInstanceOf(IOException.class).hasMessageContaining("Only JPEG");
        assertThat(PictureUploadHandler.inFlight()).isZero();
    }
}
