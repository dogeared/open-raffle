package org.openraffle.service;

import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.server.VaadinServletService;
import com.vaadin.flow.internal.CurrentInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openraffle.domain.Event;
import org.openraffle.domain.Participant;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class QrCodeServiceTest {

    @AfterEach
    void clearCurrentRequest() {
        CurrentInstance.clearAll();
    }

    @Test
    void configuredPublicUrlWinsAndLosesItsTrailingSlash() {
        QrCodeService service = new QrCodeService("https://raffle.example.com/");
        currentRequest("http", "localhost", 8081);

        assertThat(service.wishlistUrl(participant("abc"))).isEqualTo("https://raffle.example.com/p/abc");
    }

    @Test
    void withoutConfigurationTheUrlComesFromTheCurrentRequest() {
        QrCodeService service = new QrCodeService("");

        currentRequest("http", "192.168.1.20", 8081);
        assertThat(service.wishlistUrl(participant("abc"))).isEqualTo("http://192.168.1.20:8081/p/abc");

        currentRequest("https", "raffle.example.com", 443);
        assertThat(service.wishlistUrl(participant("abc"))).isEqualTo("https://raffle.example.com/p/abc");
    }

    @Test
    void withoutConfigurationOrRequestItFailsLoudly() {
        QrCodeService service = new QrCodeService(null);

        assertThatThrownBy(() -> service.wishlistUrl(participant("abc")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("raffle.public-url");
    }

    @Test
    void theEventsPublicListUrlUsesItsSlug() {
        QrCodeService service = new QrCodeService("https://raffle.example.com");
        Event fair = new Event();
        fair.setName("Carnage & Fun 29");

        assertThat(service.prizeListUrl(fair)).isEqualTo("https://raffle.example.com/e/carnage-fun-29");
        assertThat(service.pngFor(fair, 96)).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
    }

    @Test
    void qrCodeIsAPng() {
        QrCodeService service = new QrCodeService("http://localhost:8081");

        byte[] png = service.pngFor(participant("abc"), 128);

        assertThat(png).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
    }

    private static void currentRequest(String scheme, String host, int port) {
        MockHttpServletRequest http = new MockHttpServletRequest();
        http.setScheme(scheme);
        http.setServerName(host);
        http.setServerPort(port);
        http.setContextPath("");
        CurrentInstance.set(VaadinRequest.class, new VaadinServletRequest(http, mock(VaadinServletService.class)));
    }

    private static Participant participant(String token) {
        Participant p = new Participant();
        p.setName("Ann");
        p.setToken(token);
        return p;
    }
}
