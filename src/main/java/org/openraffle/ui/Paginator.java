package org.openraffle.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.util.List;
import java.util.function.Consumer;

/**
 * Client-side-free pagination over an in-memory list: previous/next, a page number box to
 * type a page into, a "per page" chooser and a summary. Hands each page to {@code onPage};
 * call {@link #setItems} whenever the underlying list changes and the current page is kept
 * (clamped to the last one).
 */
public class Paginator<T> extends HorizontalLayout {

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final List<Integer> PAGE_SIZES = List.of(10, 25, 50, 100);

    private final Consumer<List<T>> onPage;
    private final Span summary = new Span();
    private final Button previous = new Button(VaadinIcon.ANGLE_LEFT.create());
    private final Button next = new Button(VaadinIcon.ANGLE_RIGHT.create());
    private final Select<Integer> pageSize = new Select<>();
    /** The current page, 1-based, which can be typed into to jump. */
    private final IntegerField pageNumber = new IntegerField();
    private final Span pageCount = new Span();

    private List<T> items = List.of();
    private int page;

    public Paginator(Consumer<List<T>> onPage) {
        this.onPage = onPage;

        previous.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        previous.setAriaLabel("Previous page");
        previous.addClickListener(e -> goTo(page - 1));
        next.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        next.setAriaLabel("Next page");
        next.addClickListener(e -> goTo(page + 1));

        pageNumber.setMin(1);
        pageNumber.setStepButtonsVisible(false);
        pageNumber.setWidth("4em");
        pageNumber.setAriaLabel("Page number");
        pageNumber.setTooltipText("Type a page number and press Enter");
        pageNumber.setValueChangeMode(ValueChangeMode.ON_CHANGE);
        pageNumber.addValueChangeListener(e -> {
            if (!e.isFromClient()) {
                return;
            }
            // Blank or out of range: go as far as possible, and show where we ended up.
            goTo(e.getValue() == null ? page : e.getValue() - 1);
        });
        pageCount.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.Whitespace.NOWRAP);

        pageSize.setItems(PAGE_SIZES);
        pageSize.setValue(DEFAULT_PAGE_SIZE);
        pageSize.setWidth("5.5em");
        pageSize.setAriaLabel("Items per page");
        pageSize.addValueChangeListener(e -> goTo(0));
        Span perPage = new Span("per page");

        summary.addClassNames(LumoUtility.TextColor.SECONDARY);
        addClassNames(LumoUtility.FontSize.SMALL);
        setAlignItems(FlexComponent.Alignment.CENTER);
        setWidthFull();
        add(pageSize, perPage, summary, previous, pageNumber, pageCount, next);
        expand(summary);
        summary.getStyle().set("text-align", "right");
    }

    public void setItems(List<T> items) {
        this.items = items == null ? List.of() : items;
        goTo(page);
    }

    public int getPageSize() {
        return pageSize.getValue();
    }

    public void setPageSize(int size) {
        pageSize.setValue(size);
    }

    public int getPage() {
        return page;
    }

    public int getPageCount() {
        return Math.max(1, (items.size() + getPageSize() - 1) / getPageSize());
    }

    /** The items on the current page. */
    public List<T> currentPage() {
        int from = Math.min(page * getPageSize(), items.size());
        int to = Math.min(from + getPageSize(), items.size());
        return items.subList(from, to);
    }

    private void goTo(int requested) {
        page = Math.max(0, Math.min(requested, getPageCount() - 1));
        List<T> current = currentPage();
        int from = items.isEmpty() ? 0 : page * getPageSize() + 1;
        int to = page * getPageSize() + current.size();
        summary.setText(items.isEmpty() ? "Nothing to show" : from + "–" + to + " of " + items.size());
        previous.setEnabled(page > 0);
        next.setEnabled(page < getPageCount() - 1);
        pageNumber.setMax(getPageCount());
        pageNumber.setValue(page + 1);
        pageNumber.setEnabled(!items.isEmpty());
        pageCount.setText("of " + getPageCount());
        onPage.accept(current);
    }
}
