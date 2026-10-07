package org.openraffle.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.vaadin.flow.server.VaadinServletRequest;
import org.openraffle.domain.Event;
import org.openraffle.domain.Participant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

@Service
public class QrCodeService {

    private final String configuredPublicUrl;

    public QrCodeService(@Value("${raffle.public-url:}") String publicUrl) {
        this.configuredPublicUrl = stripTrailingSlash(publicUrl == null ? "" : publicUrl.trim());
    }

    /** The URL a participant lands on after scanning their QR code. */
    public String wishlistUrl(Participant participant) {
        return publicBaseUrl() + "/p/" + participant.getToken();
    }

    /** The event's login-free prize list, named after the event rather than its id. */
    public String prizeListUrl(Event event) {
        return publicBaseUrl() + "/e/" + event.getSlug();
    }

    /**
     * {@code raffle.public-url} (env {@code RAFFLE_PUBLIC_URL}) when set; otherwise the
     * scheme, host and port of the current request. Behind a proxy the request-derived
     * value honours {@code X-Forwarded-*} because {@code server.forward-headers-strategy}
     * is {@code framework}, but setting the property explicitly is the safer choice.
     */
    public String publicBaseUrl() {
        if (!configuredPublicUrl.isEmpty()) {
            return configuredPublicUrl;
        }
        VaadinServletRequest request = VaadinServletRequest.getCurrent();
        if (request == null) {
            throw new IllegalStateException("raffle.public-url is not set and there is no current request to derive it from");
        }
        return stripTrailingSlash(ServletUriComponentsBuilder
                .fromContextPath(request.getHttpServletRequest())
                .build()
                .toUriString());
    }

    public byte[] pngFor(Participant participant, int sizePx) {
        return pngFor(wishlistUrl(participant), sizePx);
    }

    public byte[] pngFor(Event event, int sizePx) {
        return pngFor(prizeListUrl(event), sizePx);
    }

    /** A square PNG QR code that opens {@code url}. */
    public byte[] pngFor(String url, int sizePx) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(
                    url, BarcodeFormat.QR_CODE, sizePx, sizePx,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 1));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException e) {
            throw new IllegalStateException("Could not encode QR code", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
