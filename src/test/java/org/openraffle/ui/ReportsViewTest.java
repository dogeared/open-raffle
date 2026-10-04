package org.openraffle.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;
import org.openraffle.domain.Event;
import org.openraffle.domain.Participant;
import org.openraffle.domain.Prize;
import org.openraffle.ui.admin.ReportsView;
import org.openraffle.ui.events.EventsView;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static com.github.mvysny.kaributesting.v10.GridKt._getFormattedRow;
import static com.github.mvysny.kaributesting.v10.GridKt._size;
import static com.github.mvysny.kaributesting.v10.LocatorJ._assertNone;
import static com.github.mvysny.kaributesting.v10.LocatorJ._assertOne;
import static com.github.mvysny.kaributesting.v10.LocatorJ._click;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static org.assertj.core.api.Assertions.assertThat;

class ReportsViewTest extends KaribuTest {

    @SuppressWarnings("unchecked")
    private static Grid<Prize> grid() {
        return _get(Grid.class);
    }

    private Event fair;

    private void openReports() {
        if (fair == null) {
            fair = event("Spring fair", "pat@example.com");
        }
        loginAsOrganizer("pat@example.com");
        start();
        navigate("events/" + fair.getId() + "/reports");
        _assertOne(ReportsView.class);
    }

    private Prize claimed(String prizeName, Participant by, Instant when) {
        Prize p = prize(fair, prizeName);
        p.setClaimedBy(by);
        p.setClaimedAt(when);
        return prizes.save(p);
    }

    @Test
    void listsClaimedPrizesByWhoTookThemWithTheirDetails() {
        fair = event("Spring fair", "pat@example.com");
        Participant ann = participant(fair, "Ann", 1, 10);
        ann.setPhone("555-0101");
        ann = participants.save(ann);
        Participant bob = participant(fair, "Bob", 11, 20);
        claimed("Bike", ann, Instant.parse("2026-10-04T18:00:00Z"));
        claimed("Mug", bob, Instant.parse("2026-10-04T19:30:00Z"));
        prize(fair, "Book"); // unclaimed: not reported
        openReports();

        assertThat(_size(grid())).isEqualTo(2);
        assertThat(_getFormattedRow(grid(), 0)).contains("Bike", "Ann", "1 – 10", "555-0101");
        assertThat(_getFormattedRow(grid(), 1)).contains("Mug", "Bob", "11 – 20", "555-0100");
        assertThat(_get(Span.class, spec -> spec.withText("2 of 3 prizes claimed."))).isNotNull();
    }

    @Test
    void opensSortedByClaimantAscendingAndTheHeadersSortTheWholeReport() {
        fair = event("Spring fair", "pat@example.com");
        Participant ann = participant(fair, "Ann", 1, 10);
        Participant bob = participant(fair, "Bob", 11, 20);
        Participant cy = participant(fair, "cy", 21, 30);
        claimed("Mug", cy, Instant.parse("2026-10-04T18:00:00Z"));
        claimed("Zebra plush", ann, Instant.parse("2026-10-04T18:01:00Z"));
        claimed("bike", bob, Instant.parse("2026-10-04T18:02:00Z"));
        claimed("Apron", ann, Instant.parse("2026-10-04T18:03:00Z"));
        openReports();

        Grid.Column<Prize> byPrize = grid().getColumnByKey("prize");
        Grid.Column<Prize> byClaimant = grid().getColumnByKey("claimedBy");
        assertThat(byPrize.isSortable()).isTrue();
        assertThat(byClaimant.isSortable()).isTrue();
        assertThat(grid().getSortOrder()).singleElement().satisfies(sort -> {
            assertThat(sort.getSorted()).isSameAs(byClaimant);
            assertThat(sort.getDirection()).isEqualTo(SortDirection.ASCENDING);
        });
        // Claimant A–Z, case-insensitive; Ann's two prizes in prize order.
        assertThat(prizeColumn()).containsExactly("Apron", "Zebra plush", "bike", "Mug");

        grid().sort(List.of(new GridSortOrder<>(byClaimant, SortDirection.DESCENDING)));
        assertThat(prizeColumn()).containsExactly("Mug", "bike", "Apron", "Zebra plush");

        grid().sort(List.of(new GridSortOrder<>(byPrize, SortDirection.ASCENDING)));
        assertThat(prizeColumn()).containsExactly("Apron", "bike", "Mug", "Zebra plush");

        grid().sort(List.of(new GridSortOrder<>(byPrize, SortDirection.DESCENDING)));
        assertThat(prizeColumn()).containsExactly("Zebra plush", "Mug", "bike", "Apron");
    }

    @Test
    void sortingReordersAcrossPagesNotJustTheVisibleOne() {
        fair = event("Spring fair", "pat@example.com");
        Participant ann = participant(fair, "Ann", 1, 10);
        IntStream.rangeClosed(1, 12).forEach(i -> claimed(String.format("Prize %02d", i), ann, Instant.parse("2026-10-04T10:00:00Z")));
        openReports();
        assertThat(prizeColumn()).first().isEqualTo("Prize 01");

        grid().sort(List.of(new GridSortOrder<>(grid().getColumnByKey("prize"), SortDirection.DESCENDING)));

        assertThat(_size(grid())).isEqualTo(10);
        assertThat(prizeColumn()).containsExactly("Prize 12", "Prize 11", "Prize 10", "Prize 09", "Prize 08",
                "Prize 07", "Prize 06", "Prize 05", "Prize 04", "Prize 03");
    }

    private static List<String> prizeColumn() {
        return IntStream.range(0, _size(grid())).mapToObj(i -> _getFormattedRow(grid(), i).get(0)).toList();
    }

    @Test
    void saysSoWhenNothingHasBeenClaimed() {
        fair = event("Spring fair", "pat@example.com");
        prize(fair, "Bike");
        openReports();

        assertThat(_size(grid())).isZero();
        assertThat(_get(Span.class, spec -> spec.withText("No prizes have been claimed yet."))).isNotNull();
    }

    @Test
    void isPaginatedLikeTheOtherGrids() {
        fair = event("Spring fair", "pat@example.com");
        Participant ann = participant(fair, "Ann", 1, 10);
        IntStream.rangeClosed(1, 12).forEach(i -> claimed(String.format("Prize %02d", i), ann, Instant.parse("2026-10-04T10:00:00Z").plusSeconds(i)));
        openReports();

        assertThat(_size(grid())).isEqualTo(10);
        assertThat(_getFormattedRow(grid(), 0)).contains("Prize 01");
        assertThat(_get(Span.class, spec -> spec.withText("1–10 of 12"))).isNotNull();
        _click(_get(Button.class, spec -> spec.withPredicate(b -> "Next page".equals(b.getAriaLabel().orElse("")))));
        assertThat(_size(grid())).isEqualTo(2);
        assertThat(_getFormattedRow(grid(), 1)).contains("Prize 12");
    }

    @Test
    void organizersNotOnTheEventAreSentBack() {
        fair = event("Spring fair", "other@example.com");
        loginAsOrganizer("pat@example.com");
        start();

        navigate("events/" + fair.getId() + "/reports");

        _assertNone(ReportsView.class);
        _assertOne(EventsView.class);
    }
}
