package org.openraffle.image;

import com.vaadin.flow.server.streams.FileUploadCallback;
import com.vaadin.flow.server.streams.TemporaryFileUploadHandler;
import com.vaadin.flow.server.streams.UploadEvent;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The upload handler for prize pictures, with the limits enforced on the server (the
 * Upload component's own limits only run in the browser): one file per request, at most
 * {@link UploadedImage#MAX_BYTES} each, a declared image type, and no more than a few
 * uploads being received at once across the whole server. Vaadin's defaults are no size
 * limit at all, which is how a flood of uploads could overwhelm a small instance.
 */
public class PictureUploadHandler extends TemporaryFileUploadHandler {

    /** Uploads being received at the same time, server-wide; beyond this they are refused, not queued. */
    public static final int MAX_IN_FLIGHT = 8;
    private static final AtomicInteger inFlight = new AtomicInteger();

    public PictureUploadHandler(FileUploadCallback callback) {
        super(callback);
    }

    @Override
    public long getFileSizeMax() {
        return UploadedImage.MAX_BYTES;
    }

    @Override
    public long getRequestSizeMax() {
        return UploadedImage.MAX_BYTES + 64 * 1024; // the file plus multipart framing
    }

    @Override
    public long getFileCountMax() {
        return 1;
    }

    @Override
    public void handleUploadRequest(UploadEvent event) throws IOException {
        check(event.getFileName(), event.getContentType(), event.getFileSize());
        if (inFlight.incrementAndGet() > MAX_IN_FLIGHT) {
            inFlight.decrementAndGet();
            throw new IOException("The server is receiving too many pictures at once; please try again in a moment.");
        }
        try {
            super.handleUploadRequest(event);
        } finally {
            inFlight.decrementAndGet();
        }
    }

    /** Refuses, before a byte is read, what {@link UploadedImage} would refuse anyway. */
    static void check(String fileName, String contentType, long declaredSize) throws IOException {
        if (declaredSize > UploadedImage.MAX_BYTES) {
            throw new IOException("The file is larger than 10 MB.");
        }
        String extension = UploadedImage.extensionOf(fileName);
        if (!UploadedImage.EXTENSIONS.contains(extension)) {
            throw new IOException("Only JPEG, PNG and GIF pictures can be uploaded.");
        }
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
        if (!type.isEmpty() && !UploadedImage.CONTENT_TYPES.contains(type)) {
            throw new IOException("Only JPEG, PNG and GIF pictures can be uploaded.");
        }
    }

    static int inFlight() {
        return inFlight.get();
    }
}
