package org.openraffle.ui;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Footer;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.theme.lumo.LumoUtility;

/** Global footer, shown on every page. */
public class AppFooter extends Footer {

    public AppFooter(AppVersion version) {
        Anchor author = new Anchor("https://github.com/dogeared", "dogeared");
        author.setTarget("_blank");
        Span heart = new Span("❤️");
        heart.addClassName("footer-heart");
        add(new Span("made with"), heart, new Span("by"), author, new Span(" · version " + version.get()));
        addClassName("app-footer");
        addClassNames(LumoUtility.Display.FLEX, LumoUtility.JustifyContent.CENTER, LumoUtility.Gap.XSMALL,
                LumoUtility.FontSize.SMALL, LumoUtility.TextColor.TERTIARY, LumoUtility.Padding.MEDIUM,
                LumoUtility.Margin.Top.AUTO, LumoUtility.BoxSizing.BORDER);
        setWidthFull();
    }
}
