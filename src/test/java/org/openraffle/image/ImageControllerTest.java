package org.openraffle.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ImageControllerTest {

    @TempDir
    Path dir;

    @Test
    void servesStoredImagesWithTheirTypeAndLongCachingAndNothingElse() throws IOException {
        PrizeImageStore store = new PrizeImageStore(dir.toString());
        ImageController controller = new ImageController(store);
        String name = store.store(3, FakeBytes.PNG, "png");

        ResponseEntity<Resource> ok = controller.image(name);
        assertThat(ok.getStatusCode().value()).isEqualTo(200);
        assertThat(ok.getHeaders().getContentType()).hasToString("image/png");
        assertThat(ok.getHeaders().getCacheControl()).contains("max-age=31536000").contains("immutable");
        assertThat(ok.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(ok.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("inline");
        assertThat(ok.getBody().contentLength()).isEqualTo(FakeBytes.PNG.length);

        assertThat(controller.image("../" + name).getStatusCode().value()).isEqualTo(404);
        assertThat(controller.image("prize-3-0123456789abcdef.png").getStatusCode().value()).isEqualTo(404);
        assertThat(controller.image("application.properties").getStatusCode().value()).isEqualTo(404);
    }

    static final class FakeBytes {
        static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    }
}
