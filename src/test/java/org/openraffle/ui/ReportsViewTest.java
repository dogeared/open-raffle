package org.openraffle.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.provider.SortDirection;
import org.junit.jupiter.api.Test;
import org.openraffle.domain.Event;
import org.openraffle.domain.Participant;
import org.openraffle.domain.Prize;
import org.openraffle.ui.admin.ReportsView;
import org.openraffle.ui.admin.ReportsView.Claimant;
import org.openraffle.ui.events.EventsView;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static com.github.mvysny.kaributesting.v10.GridKt._getCellComponent;
import static com.github.mvysny.kaributesting.v10.GridKt._getFormattedRow;
import static com.github.mvysny.kaributesting.v10.GridKt._size;
import static com.github.mvysny.kaributesting.v10.LocatorJ._assertNoDialogs;
import static com.github.mvysny.kaributesting.v10.LocatorJ._assertNone;
import static com.github.mvysny.kaributesting.v10.LocatorJ._assertOne;
import static com.github.mvysny.kaributesting.v10.LocatorJ._click;
import static com.github.mvysny.kaributesting.v10.LocatorJ._find;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static org.assertj.core.api.Assertions.assertThat;

class ReportsViewTest extends KaribuTest {

    @SuppressWarnings("unchecked")
    private static Grid<Claimant> grid() {
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

    private static String claimant(int row) {
        return _getFormattedRow(grid(), row).get(0);
    }

    private static String prizeList(int row) {
        return ((Button) _getCellComponent(grid(), row, "prizes")).getText();
    }

    private static List<String> claimantColumn() {
        return IntStream.range(0, _size(grid())).mapToObj(ReportsViewTest::claimant).toList();
    }

    @Test
    void oneRowPerClaimantWithTheirPrizesAToZAndTheirDetails() {
        fair = event("Spring fair", "pat@example.com");
        Participant ann = participant(fair, "Ann", 1, 10);
        ann.setPhone("555-0101");
        ann = participants.save(ann);
        Participant bob = participant(fair, "Bob", 11, 20);
        claimed("Mug", ann, Instant.parse("2026-10-04T18:00:00Z"));
        claimed("bike", ann, Instant.parse("2026-10-04T19:00:00Z"));
        claimed("Apron", ann, Instant.parse("2026-10-04T18:30:00Z"));
        claimed("Zebra plush", bob, Instant.parse("2026-10-04T19:30:00Z"));
        prize(fair, "Book"); // unclaimed: not reported
        openReports();

        assertThat(_size(grid())).isEqualTo(2);
        assertThat(claimant(0)).isEqualTo("Ann");
        assertThat(prizeList(0)).isEqualTo("Apron, bike, Mug");
        assertThat(_getFormattedRow(grid(), 0)).contains("1 – 10", "555-0101");
        assertThat(claimant(1)).isEqualTo("Bob");
        assertThat(prizeList(1)).isEqualTo("Zebra plush");
        assertThat(_getFormattedRow(grid(), 1)).contains("11 – 20", "555-0100");
        assertThat(_get(Span.class, spec -> spec.withText("4 of 5 prizes claimed by 2 participants."))).isNotNull();
    }

    @Test
    void whenIsTheMostRecentClaimAndTheDialogListsEachPrizeWithItsTime() {
        fair = event("Spring fair", "pat@example.com");
        Participant ann = participant(fair, "Ann", 1, 10);
        claimed("Mug", ann, Instant.parse("2026-10-04T18:00:00Z"));
        claimed("Apron", ann, Instant.parse("2026-10-04T18:30:00Z"));
        openReports();

        String when = _getFormattedRow(grid(), 0).get(4);
        assertThat(when).isEqualTo(ReportsViewTime.format(Instant.parse("2026-10-04T18:30:00Z")));

        _click((Button) _getCellComponent(grid(), 0, "prizes"));

        Dialog dialog = _get(Dialog.class);
        assertThat(dialog.getHeaderTitle()).isEqualTo("Ann's prizes");
        assertThat(_find(ListItem.class)).extracting(li -> li.getElement().getTextRecursively()).containsExactly(
                "Apron — " + ReportsViewTime.format(Instant.parse("2026-10-04T18:30:00Z")),
                "Mug — " + ReportsViewTime.format(Instant.parse("2026-10-04T18:00:00Z")));
        _click(_get(Button.class, spec -> spec.withText("Close")));
        _assertNoDialogs();
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

        Grid.Column<Claimant> byClaimant = grid().getColumnByKey("claimedBy");
        Grid.Column<Claimant> byPrizes = grid().getColumnByKey("prizes");
        Grid.Column<Claimant> byWhen = grid().getColumnByKey("when");
        assertThat(byClaimant.isSortable()).isTrue();
        assertThat(byPrizes.isSortable()).isTrue();
        assertThat(byWhen.isSortable()).isTrue();
        assertThat(grid().getColumnByKey("tickets").isSortable()).isFalse();
        assertThat(grid().getSortOrder()).singleElement().satisfies(sort -> {
            assertThat(sort.getSorted()).isSameAs(byClaimant);
            assertThat(sort.getDirection()).isEqualTo(SortDirection.ASCENDING);
        });
        // Claimant A–Z, case-insensitive.
        assertThat(claimantColumn()).containsExactly("Ann", "Bob", "cy");
        assertThat(prizeList(0)).isEqualTo("Apron, Zebra plush");

        grid().sort(List.of(new GridSortOrder<>(byClaimant, SortDirection.DESCENDING)));
        assertThat(claimantColumn()).containsExactly("cy", "Bob", "Ann");

        // By the prize list: Ann (Apron…), Bob (bike), cy (Mug).
        grid().sort(List.of(new GridSortOrder<>(byPrizes, SortDirection.DESCENDING)));
        assertThat(claimantColumn()).containsExactly("cy", "Bob", "Ann");

        // By the most recent claim: Ann's latest is 18:03, after Bob's 18:02 and cy's 18:00.
        grid().sort(List.of(new GridSortOrder<>(byWhen, SortDirection.ASCENDING)));
        assertThat(claimantColumn()).containsExactly("cy", "Bob", "Ann");
        grid().sort(List.of(new GridSortOrder<>(byWhen, SortDirection.DESCENDING)));
        assertThat(claimantColumn()).containsExactly("Ann", "Bob", "cy");
    }

    @Test
    void sortingReordersAcrossPagesNotJustTheVisibleOne() {
        fair = event("Spring fair", "pat@example.com");
        IntStream.rangeClosed(1, 12).forEach(i -> claimed("Prize " + i,
                participant(fair, String.format("Person %02d", i), i * 10, i * 10 + 5), Instant.parse("2026-10-04T10:00:00Z")));
        openReports();
        assertThat(claimantColumn()).first().isEqualTo("Person 01");

        grid().sort(List.of(new GridSortOrder<>(grid().getColumnByKey("claimedBy"), SortDirection.DESCENDING)));

        assertThat(_size(grid())).isEqualTo(10);
        assertThat(claimantColumn()).containsExactly("Person 12", "Person 11", "Person 10", "Person 09", "Person 08",
                "Person 07", "Person 06", "Person 05", "Person 04", "Person 03");
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
        IntStream.rangeClosed(1, 12).forEach(i -> claimed("Prize " + i,
                participant(fair, String.format("Person %02d", i), i * 10, i * 10 + 5), Instant.parse("2026-10-04T10:00:00Z")));
        openReports();

        assertThat(_size(grid())).isEqualTo(10);
        assertThat(claimant(0)).isEqualTo("Person 01");
        assertThat(_get(Span.class, spec -> spec.withText("1–10 of 12"))).isNotNull();
        assertThat(_get(Span.class, spec -> spec.withText("12 of 12 prizes claimed by 12 participants."))).isNotNull();
        _click(_get(Button.class, spec -> spec.withPredicate(b -> "Next page".equals(b.getAriaLabel().orElse("")))));
        assertThat(_size(grid())).isEqualTo(2);
        assertThat(claimant(1)).isEqualTo("Person 12");
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

    /** The When column's format, in the test JVM's zone, as the view prints it. */
    private static final class ReportsViewTime {
        static String format(Instant at) {
            return java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(java.time.ZoneId.systemDefault()).format(at);
        }
    }
}
