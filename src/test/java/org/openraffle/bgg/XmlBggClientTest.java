package org.openraffle.bgg;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class XmlBggClientTest {

    private static final String SEARCH_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <items total="3" termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
              <item type="boardgame" id="2655"><name type="primary" value="Catan: Seafarers"/><yearpublished value="1997"/></item>
              <item type="boardgame" id="13"><name type="primary" value="CATAN"/><yearpublished value="1995"/></item>
              <item type="boardgameexpansion" id="999"><name type="alternate" value="Catan Dice"/></item>
            </items>
            """;

    private static final String THING_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <items termsofuse="https://boardgamegeek.com/xmlapi/termsofuse">
              <item type="boardgame" id="13">
                <thumbnail>https://cf.geekdo-images.com/thumb.jpg</thumbnail>
                <image>https://cf.geekdo-images.com/image.jpg</image>
                <name type="alternate" sortindex="1" value="Die Siedler von Catan"/>
                <name type="primary" sortindex="1" value="CATAN"/>
                <yearpublished value="1995"/>
                <link type="boardgamedesigner" id="11" value="Klaus Teuber"/>
                <statistics page="1"><ratings><usersrated value="144735"/><average value="7.09005"/><bayesaverage value="6.90163"/></ratings></statistics>
              </item>
            </items>
            """;

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    void searchSendsTheBearerTokenAndRanksExactThenPrefixMatchesFirst() {
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith("https://boardgamegeek.com/xmlapi2/search")))
                .andExpect(header("Authorization", "Bearer secret-token"))
                .andExpect(header("User-Agent", XmlBggClient.USER_AGENT))
                .andExpect(queryParam("query", "catan"))
                .andExpect(queryParam("type", "boardgame,boardgameexpansion"))
                .andRespond(withSuccess(SEARCH_XML, MediaType.APPLICATION_XML));
        XmlBggClient client = new XmlBggClient(builder, "secret-token");

        List<BggItem> hits = client.search("catan");

        assertThat(client.isEnabled()).isTrue();
        assertThat(hits).containsExactly(
                new BggItem(13, "CATAN", 1995),
                new BggItem(2655, "Catan: Seafarers", 1997),
                new BggItem(999, "Catan Dice", null));
        assertThat(hits.get(0).label()).isEqualTo("CATAN (1995)");
        assertThat(hits.get(2).label()).isEqualTo("Catan Dice");
        // The same query again is answered from the cache: the mock expects exactly one call.
        assertThat(client.search("Catan ")).hasSize(3);
        server.verify();
    }

    @Test
    void thingReadsThePrimaryNameYearAndImages() {
        server.expect(requestTo("https://boardgamegeek.com/xmlapi2/thing?id=13&stats=1"))
                .andRespond(withSuccess(THING_XML, MediaType.APPLICATION_XML));
        XmlBggClient client = new XmlBggClient(builder, "t");

        Optional<BggThing> thing = client.thing(13);

        assertThat(thing).contains(new BggThing(13, "CATAN", 1995,
                "https://cf.geekdo-images.com/image.jpg", "https://cf.geekdo-images.com/thumb.jpg", 7.09005, 144735));
    }

    @Test
    void withoutATokenNothingIsAskedOfBgg() {
        XmlBggClient client = new XmlBggClient(builder, " ");

        assertThat(client.isEnabled()).isFalse();
        assertThat(client.search("catan")).isEmpty();
        assertThat(client.thing(13)).isEmpty();
        server.verify(); // no requests expected, none made
    }

    @Test
    void shortQueriesErrorsAndQueuedAnswersComeBackEmptyInsteadOfFailing() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("query=xx")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("query=yy")))
                .andRespond(withStatus(HttpStatus.ACCEPTED));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("query=zz")))
                .andRespond(withSuccess("<items><item id=\"1\"><name value=\"Z\"/></item", MediaType.APPLICATION_XML));
        XmlBggClient client = new XmlBggClient(builder, "t");

        assertThat(client.search("c")).isEmpty();
        assertThat(client.search("xx")).isEmpty();
        assertThat(client.search("yy")).isEmpty();
        assertThat(client.search("zz")).isEmpty();
    }

    @Test
    void downloadAcceptsRealImagesOverHttpsOnly() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
        server.expect(requestTo("https://cf.geekdo-images.com/a.jpg"))
                .andExpect(header("User-Agent", XmlBggClient.USER_AGENT))
                .andRespond(withSuccess(jpeg, MediaType.IMAGE_JPEG));
        server.expect(requestTo("https://cf.geekdo-images.com/not-an-image"))
                .andRespond(withSuccess("<html>nope</html>", MediaType.TEXT_HTML));
        server.expect(requestTo("https://cf.geekdo-images.com/missing.png"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        XmlBggClient client = new XmlBggClient(builder, "t");

        Optional<BggImage> image = client.download("https://cf.geekdo-images.com/a.jpg");
        assertThat(image).isPresent();
        assertThat(image.get().extension()).isEqualTo("jpg");
        assertThat(image.get().bytes()).isEqualTo(jpeg);

        assertThat(client.download("https://cf.geekdo-images.com/not-an-image")).isEmpty();
        assertThat(client.download("https://cf.geekdo-images.com/missing.png")).isEmpty();
        assertThat(client.download("http://cf.geekdo-images.com/plain.jpg")).isEmpty();
        assertThat(client.download(null)).isEmpty();
        assertThat(client.download("")).isEmpty();
    }

    @Test
    void imageFormatsAreRecognisedByTheirMagicBytes() {
        assertThat(XmlBggClient.imageExtension(FakeBggClient.PNG)).isEqualTo("png");
        assertThat(XmlBggClient.imageExtension("GIF89a......".getBytes())).isEqualTo("gif");
        assertThat(XmlBggClient.imageExtension("RIFF....WEBPVP8 ".getBytes())).isEqualTo("webp");
        assertThat(XmlBggClient.imageExtension("<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes())).isNull();
        assertThat(XmlBggClient.imageExtension(new byte[0])).isNull();
    }

    @Test
    void xmlParsingRefusesDoctypesAndExternalEntities() {
        String evil = "<!DOCTYPE x [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><items><item id=\"1\"><name value=\"&xxe;\"/></item></items>";
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> XmlBggClient.parse(evil)).isInstanceOf(Exception.class);
        assertThat(URI.create(new BggItem(13, "x", null).url())).hasToString("https://boardgamegeek.com/boardgame/13");
    }
}
