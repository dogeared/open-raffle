package org.openraffle.bgg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * BoardGameGeek's XML API 2 (https://boardgamegeek.com/wiki/page/BGG_XML_API2). Every
 * request carries the registered application's bearer token; images come from BGG's
 * CDN without it. Search results are cached briefly, because a type-ahead repeats itself.
 */
@Service
public class XmlBggClient implements BggClient {

    private static final Logger log = LoggerFactory.getLogger(XmlBggClient.class);
    static final String BASE_URL = "https://boardgamegeek.com";
    static final String USER_AGENT = "open-raffle (+https://github.com/dogeared/open-raffle)";
    /** BGG serves large box art; anything beyond this is not a box image. */
    static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final int SEARCH_CACHE_SIZE = 200;

    private final RestClient api;
    private final RestClient cdn;
    private final boolean enabled;
    private final Map<String, List<BggItem>> searchCache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<BggItem>> eldest) {
                    return size() > SEARCH_CACHE_SIZE;
                }
            });

    public XmlBggClient(RestClient.Builder builder, @Value("${raffle.bgg.api-key:}") String apiKey) {
        String token = apiKey == null ? "" : apiKey.trim();
        this.enabled = !token.isEmpty();
        this.cdn = builder.clone().defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT).build();
        this.api = builder.clone()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public List<BggItem> search(String query) {
        String q = query == null ? "" : query.trim();
        if (!enabled || q.length() < 2) {
            return List.of();
        }
        return searchCache.computeIfAbsent(q.toLowerCase(), key -> fetchSearch(q));
    }

    private List<BggItem> fetchSearch(String query) {
        Optional<Document> doc = get(uri -> uri.path("/xmlapi2/search")
                .queryParam("query", query)
                .queryParam("type", "boardgame,boardgameexpansion")
                .build());
        if (doc.isEmpty()) {
            return List.of();
        }
        List<BggItem> items = new ArrayList<>();
        NodeList nodes = doc.get().getElementsByTagName("item");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element item = (Element) nodes.item(i);
            Long id = parseLong(item.getAttribute("id"));
            String name = primaryName(item);
            if (id != null && name != null) {
                items.add(new BggItem(id, name, year(item)));
            }
        }
        // BGG returns hits in id order; exact and prefix matches first reads like its own site.
        String lower = query.toLowerCase();
        items.sort((a, b) -> Integer.compare(rank(a, lower), rank(b, lower)));
        return List.copyOf(items);
    }

    private static int rank(BggItem item, String query) {
        String name = item.name().toLowerCase();
        return name.equals(query) ? 0 : name.startsWith(query) ? 1 : 2;
    }

    @Override
    public Optional<BggThing> thing(long id) {
        if (!enabled) {
            return Optional.empty();
        }
        return get(uri -> uri.path("/xmlapi2/thing").queryParam("id", id).queryParam("stats", 1).build()).flatMap(doc -> {
            NodeList nodes = doc.getElementsByTagName("item");
            if (nodes.getLength() == 0) {
                return Optional.empty();
            }
            Element item = (Element) nodes.item(0);
            String name = primaryName(item);
            return Optional.of(new BggThing(id, name == null ? "BGG #" + id : name, year(item),
                    text(item, "image"), text(item, "thumbnail"), rating(item), ratingCount(item)));
        });
    }

    @Override
    public Optional<BggImage> download(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        try {
            URI target = URI.create(url.trim());
            if (!"https".equals(target.getScheme())) {
                log.warn("Refusing to fetch a prize image over {}: {}", target.getScheme(), url);
                return Optional.empty();
            }
            ResponseEntity<byte[]> response = cdn.get().uri(target).retrieve().toEntity(byte[].class);
            byte[] bytes = response.getBody();
            if (bytes == null || bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
                log.warn("Prize image at {} is {} bytes; skipping", url, bytes == null ? 0 : bytes.length);
                return Optional.empty();
            }
            return Optional.ofNullable(imageExtension(bytes)).map(ext -> new BggImage(bytes, ext));
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("Could not fetch prize image {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    /** The format by its magic bytes, or null when the bytes are not an image we serve. */
    static String imageExtension(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "png";
        }
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return "gif";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "webp";
        }
        return null;
    }

    private Optional<Document> get(java.util.function.Function<org.springframework.web.util.UriBuilder, URI> uri) {
        try {
            ResponseEntity<byte[]> response = api.get().uri(uri).retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> { })
                    .toEntity(byte[].class);
            if (response.getStatusCode().value() == 202) {
                log.info("BGG queued the request; try again shortly");
                return Optional.empty();
            }
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("BGG answered {} for {}", response.getStatusCode().value(), uri);
                return Optional.empty();
            }
            byte[] body = response.getBody();
            return body == null ? Optional.empty() : Optional.of(parse(new String(body, StandardCharsets.UTF_8)));
        } catch (RestClientException e) {
            log.warn("BGG request failed: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("BGG answer could not be read: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Parses with external entities and DOCTYPEs off: the answer is data, not instructions. */
    static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static String primaryName(Element item) {
        NodeList names = item.getElementsByTagName("name");
        String fallback = null;
        for (int i = 0; i < names.getLength(); i++) {
            Element name = (Element) names.item(i);
            if (name.getParentNode() != item) {
                continue; // names nested in links/polls are not this item's
            }
            String value = name.getAttribute("value");
            if (value.isBlank()) {
                continue;
            }
            if ("primary".equals(name.getAttribute("type"))) {
                return value;
            }
            if (fallback == null) {
                fallback = value;
            }
        }
        return fallback;
    }

    private static Integer year(Element item) {
        NodeList years = item.getElementsByTagName("yearpublished");
        if (years.getLength() == 0) {
            return null;
        }
        Long year = parseLong(((Element) years.item(0)).getAttribute("value"));
        return year == null || year == 0 ? null : year.intValue();
    }

    /** The community average from {@code <statistics><ratings><average value=…/>}, 1–10, else null. */
    private static Double rating(Element item) {
        NodeList averages = item.getElementsByTagName("average");
        for (int i = 0; i < averages.getLength(); i++) {
            Element average = (Element) averages.item(i);
            if (average.getParentNode() != null && "ratings".equals(average.getParentNode().getNodeName())) {
                try {
                    double value = Double.parseDouble(average.getAttribute("value").trim());
                    return value > 0 && value <= 10 ? value : null;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** How many people rated it: {@code <statistics><ratings><usersrated value=…/>}. */
    private static Integer ratingCount(Element item) {
        NodeList counts = item.getElementsByTagName("usersrated");
        for (int i = 0; i < counts.getLength(); i++) {
            Element count = (Element) counts.item(i);
            if (count.getParentNode() != null && "ratings".equals(count.getParentNode().getNodeName())) {
                Long value = parseLong(count.getAttribute("value"));
                return value == null ? null : value.intValue();
            }
        }
        return null;
    }

    private static String text(Element item, String tag) {
        NodeList nodes = item.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return null;
        }
        String value = nodes.item(0).getTextContent();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** For tests and diagnostics. */
    List<String> cachedQueries() {
        synchronized (searchCache) {
            return new ArrayList<>(searchCache.keySet());
        }
    }

    static boolean isImageExtension(String ext) {
        return Arrays.asList("jpg", "png", "gif", "webp").contains(ext);
    }
}
