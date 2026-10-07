package org.openraffle.ui;

import com.vaadin.flow.component.html.Image;
import org.openraffle.domain.Prize;

/** A prize's stored picture as a small square, for grid rows and list rows. */
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
        return image;
    }
}
