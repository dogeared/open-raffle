package org.openraffle.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Image;
import org.openraffle.domain.Prize;

/**
 * A prize's stored picture as a small square, for grid rows and list rows. Clicking it
 * opens the full-size picture in a dialog, since a thumbnail cannot show much box art.
 */
public final class PrizeThumbnail {

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
        image.addClickListener(e -> showLarge(prize));
        return image;
    }

    /** The picture as large as the window allows, with the prize's name as the title. */
    static void showLarge(Prize prize) {
        Dialog dialog = new Dialog(prize.getName());
        Image large = new Image(prize.getImageUrl(), prize.getName());
        large.addClassName("prize-picture-large");
        dialog.add(large);
        dialog.addClassName("prize-picture-dialog");
        dialog.setCloseOnOutsideClick(true);
        dialog.setCloseOnEsc(true);
        dialog.getFooter().add(new Button("Close", e -> dialog.close()));
        dialog.open();
    }
}
