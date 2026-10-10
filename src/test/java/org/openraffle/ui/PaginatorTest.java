package org.openraffle.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class PaginatorTest {

    private final List<List<Integer>> pagesSeen = new ArrayList<>();
    private final Paginator<Integer> paginator = new Paginator<>(pagesSeen::add);

    private static List<Integer> numbers(int n) {
        return IntStream.rangeClosed(1, n).boxed().toList();
    }

    @Test
    void defaultsToTenPerPage() {
        paginator.setItems(numbers(37));

        assertThat(paginator.getPageSize()).isEqualTo(10);
        assertThat(paginator.getPageCount()).isEqualTo(4);
        assertThat(paginator.currentPage()).containsExactlyElementsOf(numbers(10));
        assertThat(pagesSeen).hasSize(1);
    }

    @Test
    void pageSizeChangesRestartFromTheFirstPage() {
        paginator.setItems(numbers(37));

        paginator.setPageSize(100);

        assertThat(paginator.getPageCount()).isEqualTo(1);
        assertThat(paginator.currentPage()).hasSize(37);
        assertThat(Paginator.PAGE_SIZES).containsExactly(10, 25, 50, 100);
    }

    @Test
    void refreshingWithFewerItemsClampsToTheLastPage() {
        paginator.setItems(numbers(37));
        paginator.setPageSize(10);
        // Walk to the last page through the public surface: shrink the list and re-grow it.
        paginator.setItems(numbers(37));
        for (int i = 0; i < 3; i++) {
            nextPage();
        }
        assertThat(paginator.getPage()).isEqualTo(3);
        assertThat(paginator.currentPage()).containsExactly(31, 32, 33, 34, 35, 36, 37);

        paginator.setItems(numbers(12));

        assertThat(paginator.getPage()).isEqualTo(1);
        assertThat(paginator.currentPage()).containsExactly(11, 12);
    }

    @Test
    void emptyListIsASinglePage() {
        paginator.setItems(List.of());

        assertThat(paginator.getPageCount()).isEqualTo(1);
        assertThat(paginator.currentPage()).isEmpty();
    }

    private void nextPage() {
        // The next button is private (and nested in the pager group); simulate it the way a click would.
        descendants(paginator)
                .filter(c -> c instanceof com.vaadin.flow.component.button.Button b && "Next page".equals(b.getAriaLabel().orElse("")))
                .findFirst()
                .map(com.vaadin.flow.component.button.Button.class::cast)
                .orElseThrow()
                .click();
    }

    private static java.util.stream.Stream<com.vaadin.flow.component.Component> descendants(com.vaadin.flow.component.Component c) {
        return c.getChildren().flatMap(child -> java.util.stream.Stream.concat(java.util.stream.Stream.of(child), descendants(child)));
    }
}
