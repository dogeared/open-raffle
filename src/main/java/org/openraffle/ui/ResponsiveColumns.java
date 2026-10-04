package org.openraffle.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.page.ExtendedClientDetails;
import com.vaadin.flow.component.page.Page;
import com.vaadin.flow.shared.Registration;

import java.util.List;

/**
 * Hides a grid's lower-priority columns on narrow screens (phones), so the remaining ones
 * fit the width and wrap instead of forcing a sideways scroll. Width is unknown until the
 * browser reports it; until then every column stays visible.
 */
public final class ResponsiveColumns {

    /** Below this window width the optional columns are hidden. */
    public static final int NARROW_PX = 640;

    private ResponsiveColumns() {
    }

    public static void hideOnNarrowScreens(Grid<?> grid, List<Grid.Column<?>> optional) {
        grid.addAttachListener(attach -> {
            UI ui = attach.getUI();
            Page page = ui.getPage();
            ExtendedClientDetails known = ui.getInternals().getExtendedClientDetails();
            if (known != null) {
                apply(optional, known.getWindowInnerWidth());
            } else {
                page.retrieveExtendedClientDetails(details -> apply(optional, details.getWindowInnerWidth()));
            }
            Registration resize = page.addBrowserWindowResizeListener(e -> apply(optional, e.getWidth()));
            grid.addDetachListener(detach -> {
                resize.remove();
                detach.unregisterListener();
            });
        });
    }

    static void apply(List<Grid.Column<?>> optional, int windowWidth) {
        boolean show = windowWidth <= 0 || windowWidth >= NARROW_PX;
        optional.forEach(column -> column.setVisible(show));
    }
}
