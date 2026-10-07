package org.openraffle.image;

import org.openraffle.service.PrizeService;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Serves stored prize images at {@code /images/<name>} to everyone (the public prize list
 * and participants' wishlists show them). Names are validated by the store; a name that
 * is not one of ours is a 404, whatever else it contains. A picture whose file is gone is
 * fetched from BoardGameGeek again on the spot, so hosts without persistent disks work.
 */
@RestController
public class ImageController {

    private final PrizeImageStore store;
    private final PrizeService prizes;

    public ImageController(PrizeImageStore store, PrizeService prizes) {
        this.store = store;
        this.prizes = prizes;
    }

    @GetMapping("/images/{name}")
    public ResponseEntity<Resource> image(@PathVariable String name) {
        // Missing on disk (fresh deploy, no persistent storage)? Fetch it from BGG again.
        Optional<Path> file = store.resolve(name).or(() -> prizes.restoreImage(name));
        if (file.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                // Every upload gets a new name, so a name's bytes never change.
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .header(HttpHeaders.CONTENT_TYPE, PrizeImageStore.contentType(name))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + name + "\"")
                .body(new PathResource(file.get()));
    }

    static MediaType mediaType(String name) {
        return MediaType.parseMediaType(PrizeImageStore.contentType(name));
    }
}
