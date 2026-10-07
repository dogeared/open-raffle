package org.openraffle.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrizeImageStoreTest {

    @TempDir
    Path dir;

    @Test
    void storesUnderAMintedNameAndServesOnlyNamesOfThatShape() throws IOException {
        PrizeImageStore store = new PrizeImageStore(dir.toString());

        String name = store.store(42, new byte[]{1, 2, 3}, "jpg");

        assertThat(name).matches("prize-42-[a-f0-9]{16}\\.jpg");
        assertThat(store.resolve(name)).isPresent();
        assertThat(Files.readAllBytes(store.resolve(name).get())).containsExactly(1, 2, 3);
        assertThat(PrizeImageStore.contentType(name)).isEqualTo("image/jpeg");
        assertThat(PrizeImageStore.contentType("x.webp")).isEqualTo("image/webp");

        // Two stores for the same prize never collide, so cached URLs stay valid.
        assertThat(store.store(42, new byte[]{4}, "jpg")).isNotEqualTo(name);
    }

    @Test
    void namesThatAreNotOursAreNotFoundHoweverTheyAreSpelled() throws IOException {
        PrizeImageStore store = new PrizeImageStore(dir.toString());
        Files.writeString(dir.resolve("secret.txt"), "shh");
        Files.writeString(dir.resolve("prize-1-0123456789abcdef.jpg.bak"), "x");

        for (String bad : new String[]{null, "", "secret.txt", "../secret.txt", "..%2Fsecret.txt",
                "prize-1-0123456789abcdef.jpg.bak", "prize-1-0123456789abcdef.svg", "prize-1-0123456789abcdef.JPG",
                "prize-1-0123456789abcde.jpg", "prize-x-0123456789abcdef.jpg", "/etc/passwd",
                "prize-1-0123456789abcdef.jpg/../../secret.txt", "prize-1-0123456789abcdef.php"}) {
            assertThat(store.resolve(bad)).as("%s", bad).isEmpty();
        }
        // The right shape but no such file: also not found rather than an error.
        assertThat(store.resolve("prize-1-0123456789abcdef.jpg")).isEmpty();
    }

    @Test
    void deleteRemovesOurFilesAndIgnoresAnythingElse() throws IOException {
        PrizeImageStore store = new PrizeImageStore(dir.toString());
        Files.writeString(dir.resolve("keep.txt"), "x");
        String name = store.store(7, new byte[]{1}, "png");

        store.delete(name);
        store.delete("keep.txt");
        store.delete(null);

        assertThat(dir.resolve(name)).doesNotExist();
        assertThat(dir.resolve("keep.txt")).exists();
    }

    @Test
    void onlyImageExtensionsCanBeStoredAndTheDirectoryIsCreated() {
        Path nested = dir.resolve("a/b/images");
        PrizeImageStore store = new PrizeImageStore(nested.toString());

        assertThat(nested).isDirectory();
        assertThat(store.directory()).isEqualTo(nested.toAbsolutePath().normalize());
        assertThatThrownBy(() -> store.store(1, new byte[]{1}, "svg")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.store(1, new byte[]{1}, "jpg/../x")).isInstanceOf(IllegalArgumentException.class);
    }
}
