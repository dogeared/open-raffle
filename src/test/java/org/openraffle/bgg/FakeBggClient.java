package org.openraffle.bgg;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A BoardGameGeek that answers from a list set up by the test, with a tiny PNG for every game. */
public class FakeBggClient implements BggClient {

    /** Valid PNG signature followed by filler: enough to pass the magic-byte check. */
    public static final byte[] PNG = concat(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A},
            "fake image bytes".getBytes(StandardCharsets.US_ASCII));

    public boolean enabled = true;
    public boolean imagesAvailable = true;
    /** When false, only the thumbnail URL downloads, like an oversize original. */
    public boolean fullImagesAvailable = true;
    public final List<BggItem> items = new ArrayList<>(List.of(
            new BggItem(13, "CATAN", 1995),
            new BggItem(2655, "Catan: Seafarers", 1997),
            new BggItem(822, "Carcassonne", 2000)));
    public final List<String> searches = new ArrayList<>();
    public final java.util.Map<Long, Integer> ratingCounts = new java.util.HashMap<>();
    public final java.util.Map<Long, Double> ratings = new java.util.HashMap<>(java.util.Map.of(13L, 7.09005, 2655L, 7.2, 822L, 7.4));
    public final List<String> downloads = new ArrayList<>();

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public List<BggItem> search(String query) {
        searches.add(query);
        String q = query.toLowerCase();
        return items.stream().filter(i -> i.name().toLowerCase().contains(q)).toList();
    }

    @Override
    public Optional<BggThing> thing(long id) {
        return items.stream().filter(i -> i.id() == id).findFirst()
                .map(i -> new BggThing(i.id(), i.name(), i.year(), imageUrl(i.id()), imageUrl(i.id()) + "?thumb", ratings.get(i.id()), ratingCounts.getOrDefault(i.id(), 1000)));
    }

    public static String imageUrl(long id) {
        return "https://cf.geekdo-images.com/fake/" + id + ".png";
    }

    @Override
    public Optional<BggImage> download(String url) {
        downloads.add(url);
        if (!imagesAvailable || url == null || (!fullImagesAvailable && !url.endsWith("?thumb"))) {
            return Optional.empty();
        }
        return Optional.of(new BggImage(PNG, "png"));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
