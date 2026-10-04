package org.openraffle.ui;

import com.vaadin.flow.component.grid.Grid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResponsiveColumnsTest {

    @Test
    void optionalColumnsHideOnPhonesAndReturnOnWiderScreens() {
        Grid<String> grid = new Grid<>();
        Grid.Column<String> keep = grid.addColumn(s -> s).setHeader("Name");
        Grid.Column<String> optional = grid.addColumn(s -> s).setHeader("Count");
        List<Grid.Column<?>> optionals = List.of(optional);

        ResponsiveColumns.apply(optionals, 390);
        assertThat(optional.isVisible()).isFalse();
        assertThat(keep.isVisible()).isTrue();

        ResponsiveColumns.apply(optionals, 1280);
        assertThat(optional.isVisible()).isTrue();

        // Width not known yet: show everything rather than guess.
        ResponsiveColumns.apply(optionals, 0);
        assertThat(optional.isVisible()).isTrue();
    }
}
