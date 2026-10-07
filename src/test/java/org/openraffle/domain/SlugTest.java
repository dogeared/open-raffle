package org.openraffle.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SlugTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Carnage & Fun 29        | carnage-fun-29",
            "Spring fair             | spring-fair",
            "  Padded   name  !!     | padded-name",
            "Café Noël 2026          | cafe-noel-2026",
            "already-a-slug          | already-a-slug",
            "UPPER_snake_case        | upper-snake-case",
            "Don't Stop (Believin')  | don-t-stop-believin",
            "日本語                   | ''",
            "                        | ''",
    })
    void namesBecomeLowercaseDashedUrlPieces(String name, String slug) {
        assertThat(Slug.of(name)).isEqualTo(slug);
    }

    @ParameterizedTest
    @CsvSource({"Spring fair, spring-fair"})
    void anEventKnowsItsSlug(String name, String slug) {
        Event event = new Event();
        event.setName(name);
        assertThat(event.getSlug()).isEqualTo(slug);
        assertThat(Slug.of(null)).isEmpty();
    }
}
