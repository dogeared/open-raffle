package org.openraffle.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.Test;
import org.openraffle.drive.DriveCallbackController;
import org.openraffle.ui.admin.SettingsView;

import static com.github.mvysny.kaributesting.v10.LocatorJ._assertNone;
import static com.github.mvysny.kaributesting.v10.LocatorJ._assertOne;
import static com.github.mvysny.kaributesting.v10.LocatorJ._click;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static com.github.mvysny.kaributesting.v10.NotificationsKt.getNotifications;
import static org.assertj.core.api.Assertions.assertThat;

class SettingsViewTest extends KaribuTest {

    private void openSettings() {
        loginAsOrganizer("pat@example.com");
        start();
        navigate("settings");
        _assertOne(SettingsView.class);
    }

    private String state() {
        return _get(Span.class, spec -> spec.withClasses("drive-state")).getText();
    }

    @Test
    void explainsWhatToSetUpWhenGoogleIsNotConfigured() {
        fakeDrive.configured = false;
        openSettings();

        assertThat(state()).isEqualTo("Not set up on this server.");
        assertThat(_get(SettingsView.class).getElement().getTextRecursively())
                .contains("GOOGLE_CLIENT_ID").contains("/drive/callback");
        _assertNone(Button.class, spec -> spec.withText("Connect Google Drive"));
    }

    @Test
    void connectStartsTheConsentFlowWithAStateKeptInTheSession() {
        openSettings();
        assertThat(state()).isEqualTo("Not connected.");

        _click(_get(Button.class, spec -> spec.withText("Connect Google Drive")));

        Object state = VaadinSession.getCurrent().getSession().getAttribute(DriveCallbackController.STATE_ATTRIBUTE);
        assertThat(state).isInstanceOf(String.class);
        assertThat((String) state).hasSize(32);
    }

    @Test
    void showsTheConnectionChecksItAndDisconnectsAfterConfirming() {
        loginAsOrganizer("pat@example.com");
        connectDrive();
        openSettings();

        assertThat(state()).isEqualTo("Connected");
        assertThat(_get(Anchor.class, spec -> spec.withText(org.openraffle.drive.DriveService.FOLDER_NAME)).getHref())
                .isEqualTo("https://drive.google.com/drive/folders/folder-1");
        assertThat(_get(SettingsView.class).getElement().getTextRecursively()).contains("owner@gmail.test").contains("pat@example.com");

        fakeDrive.folderGone = true;
        _click(_get(Button.class, spec -> spec.withText("Check now")));
        assertThat(state()).isEqualTo("Connected, but not working");
        assertThat(_get(SettingsView.class).getElement().getTextRecursively()).contains("folder is gone");
        _assertOne(Button.class, spec -> spec.withText("Reconnect"));

        _click(_get(Button.class, spec -> spec.withText("Disconnect")));
        ConfirmDialog confirm = _get(ConfirmDialog.class);
        confirm(confirm);
        assertThat(state()).isEqualTo("Not connected.");
        assertThat(driveConnections.count()).isZero();
    }

    @Test
    void theOutcomeOfTheGoogleRoundTripIsShownOnce() {
        loginAsOrganizer("pat@example.com");
        start();
        VaadinSession.getCurrent().getSession().setAttribute(DriveCallbackController.RESULT_ATTRIBUTE, DriveCallbackController.Outcome.DECLINED);

        navigate("settings");

        assertThat(getNotifications()).extracting(n -> n.getElement().getProperty("text"))
                .anySatisfy(text -> assertThat(text).contains("declined on Google's consent screen"));
        assertThat(VaadinSession.getCurrent().getSession().getAttribute(DriveCallbackController.RESULT_ATTRIBUTE)).isNull();
    }
}
