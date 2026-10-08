package org.openraffle.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import org.openraffle.domain.Prize;

/**
 * A prize's stored picture as a small square, for grid rows and list rows. Hovering shows
 * the full-size picture while the pointer stays; clicking opens it to stay until closed.
 * Both carry BGG's rating in the lower right corner, when the prize is linked to a game.
 */
public final class PrizeThumbnail {

    /** Hover waits this long so sweeping the pointer across a list does not flash pictures. */
    static final int HOVER_DELAY_MS = 350;

    private PrizeThumbnail() {
    }

    /** Null when the prize has no picture, so callers can skip the slot. */
    public static Image of(Prize prize, String size) {
        if (!prize.hasImage()) {
            return null;
        }
        Image image = new Image(prize.getImageUrl(), prize.getName());
        image.addClassName("prize-thumbnail");
        image.setWidth(size);
        image.setHeight(size);
        image.getElement().setAttribute("loading", "lazy");
        image.getElement().setAttribute("title", "Show a larger picture");
        image.getElement().setAttribute("role", "button");
        image.getElement().setAttribute("tabindex", "0");

        Preview preview = new Preview(prize);
        image.addClickListener(e -> preview.pin());
        image.getElement().addEventListener("mouseenter", e -> preview.peek()).debounce(HOVER_DELAY_MS);
        image.getElement().addEventListener("mouseleave", e -> preview.unpeek());
        return image;
    }

    /** One thumbnail's large picture: shown lightly on hover, kept open on click. */
    static final class Preview {
        private final Prize prize;
        private Dialog dialog;
        private boolean pinned;

        Preview(Prize prize) {
            this.prize = prize;
        }

        /** Hover: a non-modal glimpse that goes away when the pointer leaves the thumbnail. */
        void peek() {
            if (dialog != null && dialog.isOpened()) {
                return;
            }
            pinned = false;
            open(false);
        }

        void unpeek() {
            if (!pinned && dialog != null) {
                dialog.close();
            }
        }

        /** Click: the same picture, modal, with a Close button; stays until dismissed. */
        void pin() {
            if (dialog != null) {
                dialog.close();
            }
            pinned = true;
            open(true);
        }

        private void open(boolean modal) {
            dialog = new Dialog(prize.getName());
            dialog.addClassName("prize-picture-dialog");
            dialog.setModal(modal);
            dialog.setCloseOnOutsideClick(true);
            dialog.setCloseOnEsc(true);
            dialog.add(picture(prize));
            if (modal) {
                dialog.getFooter().add(new Button("Close", e -> dialog.close()));
            }
            dialog.open();
        }
    }

    /** The full-size picture with BGG's rating badge over its lower right corner. */
    public static Div picture(Prize prize) {
        Image large = new Image(prize.getImageUrl(), prize.getName());
        large.addClassName("prize-picture-large");
        Div frame = new Div(large);
        frame.addClassName("prize-picture-frame");
        Anchor rating = BggRatingBadge.of(prize);
        if (rating != null) {
            frame.add(rating);
        }
        return frame;
    }
}
