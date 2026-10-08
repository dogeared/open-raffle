package org.openraffle;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Viewport;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Theme("open-raffle")
// Vaadin 25 no longer loads the Lumo utility classes (LumoUtility.*) with the theme.
@StyleSheet(Lumo.UTILITY_STYLESHEET)
@Viewport("width=device-width, initial-scale=1")
public class OpenRaffleApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(OpenRaffleApplication.class, args);
    }
}
