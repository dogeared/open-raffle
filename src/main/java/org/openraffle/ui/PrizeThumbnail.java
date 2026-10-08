package org.openraffle.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import org.openraffle.domain.Prize;

/**
 * A prize's stored picture as a small square, for grid rows and list rows. Hovering shows
 * the full-size picture, which stays while the pointer is on the thumbnail or on the picture
 * itself; clicking opens it to stay until closed.
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
        // The picture opens centred and often right over the thumbnail, which then sees a
        // mouseleave at once; so leaving the thumbnail only closes the peek if the pointer
        // has not landed on the picture in the meantime.
        image.getElement().addEventListener("mouseleave", e -> preview.leftThumbnail())
                .setFilter(POINTER_NOT_ON_PICTURE)
                .debounce(LEAVE_GRACE_MS);
        return image;
    }

    /**
     * Client-side check run when the pointer leaves a thumbnail: true unless the pointer is
     * inside a full-size picture's box. A picture that opens over the thumbnail makes the
     * browser report a leave without any movement, and that leave must not close it.
     */
    static final String POINTER_NOT_ON_PICTURE = "![...document.querySelectorAll('.prize-picture-frame')].some(f => {"
            + " const r = f.getBoundingClientRect();"
            + " return event.clientX >= r.left && event.clientX <= r.right && event.clientY >= r.top && event.clientY <= r.bottom; })";

    /** Leaving the thumbnail waits this long for the pointer to arrive on the picture. */
    static final int LEAVE_GRACE_MS = 300;

    /**
     * One thumbnail's large picture: shown lightly on hover and kept while the pointer is on
     * the thumbnail or the picture itself; kept open on click until closed.
     */
    static final class Preview {
        private final Prize prize;
        private Dialog dialog;
        private boolean pinned;
        private boolean overPicture;

        Preview(Prize prize) {
            this.prize = prize;
        }

        /** Hover: a non-modal glimpse. Stays while the pointer rests on the thumbnail or the picture. */
        void peek() {
            if (dialog != null && dialog.isOpened()) {
                return;
            }
            pinned = false;
            open(false);
        }

        void leftThumbnail() {
            if (!pinned && !overPicture && dialog != null) {
                dialog.close();
            }
        }

        void enteredPicture() {
            overPicture = true;
        }

        void leftPicture() {
            overPicture = false;
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
            overPicture = false;
            // The peek is just the picture: no header or padding, so the picture's box is
            // the whole dialog and "on the picture" means "on the dialog".
            dialog = modal ? new Dialog(prize.getName()) : new Dialog();
            dialog.addClassName("prize-picture-dialog");
            dialog.addClassName(modal ? "prize-picture-pinned" : "prize-picture-peek");
            dialog.setModal(modal);
            dialog.setCloseOnOutsideClick(true);
            dialog.setCloseOnEsc(true);
            Div frame = picture(prize);
            frame.getElement().addEventListener("mouseenter", e -> enteredPicture());
            frame.getElement().addEventListener("mouseleave", e -> leftPicture());
            dialog.add(frame);
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
