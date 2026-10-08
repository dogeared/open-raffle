package org.openraffle.ui.admin;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedSession;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.RolesAllowed;
import org.openraffle.domain.DriveConnection;
import org.openraffle.domain.Prize;
import org.openraffle.drive.DriveCallbackController;
import org.openraffle.drive.DriveService;
import org.openraffle.security.SecurityConfig;
import org.openraffle.service.QrCodeService;
import org.openraffle.ui.MainLayout;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * App-wide settings for organizers and admins. For now: the Google Drive folder that
 * holds organizers' prize pictures, with connect, check, reconnect and disconnect.
 */
@Route(value = "settings", layout = MainLayout.class)
@PageTitle("Settings | Open Raffle")
@RolesAllowed({SecurityConfig.ROLE_ORGANIZER, SecurityConfig.ROLE_ADMIN})
public class SettingsView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault());

    private final DriveService driveService;
    private final QrCodeService publicUrl;
    private final Div driveCard = new Div();

    public SettingsView(DriveService driveService, QrCodeService publicUrl) {
        this.driveService = driveService;
        this.publicUrl = publicUrl;
        setMaxWidth("720px");
        driveCard.addClassNames(LumoUtility.Padding.MEDIUM, LumoUtility.BorderRadius.MEDIUM, LumoUtility.Background.CONTRAST_5);
        driveCard.setWidthFull();
        add(new H2("Settings"), driveCard);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent enter) {
        // Back from Google? The callback left the outcome in the HTTP session.
        WrappedSession session = VaadinSession.getCurrent().getSession();
        Object result = session.getAttribute(DriveCallbackController.RESULT_ATTRIBUTE);
        if (result instanceof DriveCallbackController.Outcome outcome) {
            Object detail = session.getAttribute(DriveCallbackController.RESULT_DETAIL_ATTRIBUTE);
            session.removeAttribute(DriveCallbackController.RESULT_ATTRIBUTE);
            session.removeAttribute(DriveCallbackController.RESULT_DETAIL_ATTRIBUTE);
            String text = outcome.message() + (detail instanceof String d && !d.isBlank() ? ": " + d : "");
            notify(text, outcome.isSuccess() ? NotificationVariant.LUMO_SUCCESS : NotificationVariant.LUMO_ERROR);
        }
        render(driveService.status());
    }

    private void render(DriveService.Status status) {
        driveCard.removeAll();
        H3 title = new H3("Google Drive for prize pictures");
        title.addClassNames(LumoUtility.Margin.Top.NONE);
        Paragraph what = new Paragraph("Organizers can upload up to " + Prize.MAX_PICTURES
                + " pictures per prize once a Google Drive folder is connected. The app creates its own folder in the connected account,"
                + " with a subfolder per event, and can only see files it put there. Until then, prizes show their BoardGameGeek box image.");
        what.addClassNames(LumoUtility.TextColor.SECONDARY);
        driveCard.add(title, what);

        Span state = new Span();
        state.addClassName("drive-state");
        HorizontalLayout actions = new HorizontalLayout();
        actions.addClassName("drive-actions");
        actions.setWrap(true);
        switch (status.state()) {
            case NOT_CONFIGURED -> {
                state.setText("Not set up on this server.");
                state.getElement().getThemeList().addAll(java.util.List.of("badge", "contrast"));
                Paragraph how = new Paragraph("Set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET (a Google OAuth web client with "
                        + DriveCallbackController.redirectUri(publicUrl.publicBaseUrl()) + " as an authorised redirect URI) and restart. See the README.");
                how.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
                driveCard.add(state, how);
            }
            case NOT_CONNECTED -> {
                state.setText("Not connected.");
                state.getElement().getThemeList().addAll(java.util.List.of("badge", "contrast"));
                actions.add(connectButton("Connect Google Drive"));
                driveCard.add(state, actions);
            }
            case CONNECTED, UNHEALTHY -> {
                DriveConnection c = status.connection();
                boolean healthy = status.state() == DriveService.State.CONNECTED;
                state.setText(healthy ? "Connected" : "Connected, but not working");
                state.getElement().getThemeList().addAll(java.util.List.of("badge", healthy ? "success" : "error"));
                Div details = new Div();
                details.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY, LumoUtility.Margin.Vertical.SMALL);
                Anchor folder = new Anchor(c.getFolderUrl(), c.getFolderName() == null ? "the folder" : c.getFolderName());
                folder.setTarget("_blank");
                details.add(new Div(new Span("Folder: "), folder));
                details.add(new Div(new Span("Account: " + (c.getAccountEmail() == null ? "unknown" : c.getAccountEmail()))));
                details.add(new Div(new Span("Connected " + (c.getConnectedAt() == null ? "" : WHEN.format(c.getConnectedAt()))
                        + (c.getConnectedBy() == null ? "" : " by " + c.getConnectedBy())
                        + (c.getLastCheckedAt() == null ? "" : " · last checked " + WHEN.format(c.getLastCheckedAt())))));
                if (!healthy) {
                    Span error = new Span(c.getLastError());
                    error.addClassNames(LumoUtility.TextColor.ERROR, LumoUtility.FontWeight.SEMIBOLD);
                    details.add(new Div(error));
                }
                Button check = new Button("Check now", VaadinIcon.REFRESH.create(), e -> {
                    DriveService.Status after = driveService.checkHealth();
                    notify(after.canUpload() ? "Google Drive is working." : "Google Drive is not working: " + after.connection().getLastError(),
                            after.canUpload() ? NotificationVariant.LUMO_SUCCESS : NotificationVariant.LUMO_ERROR);
                    render(after);
                });
                Button disconnect = new Button("Disconnect", VaadinIcon.UNLINK.create(), e -> confirmDisconnect());
                disconnect.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
                actions.add(connectButton("Reconnect"), check, disconnect);
                driveCard.add(state, details, actions);
            }
        }
    }

    /** Sends the browser to Google's consent screen; the callback brings it back to this page. */
    private Button connectButton(String label) {
        Button connect = new Button(label, VaadinIcon.CLOUD_UPLOAD.create(), e -> {
            String state = DriveService.newState();
            VaadinSession.getCurrent().getSession().setAttribute(DriveCallbackController.STATE_ATTRIBUTE, state);
            String redirectUri = DriveCallbackController.redirectUri(publicUrl.publicBaseUrl());
            UI.getCurrent().getPage().setLocation(driveService.authorizationUrl(redirectUri, state));
        });
        connect.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        return connect;
    }

    private void confirmDisconnect() {
        ConfirmDialog confirm = new ConfirmDialog("Disconnect Google Drive?",
                "Organizers will not be able to upload pictures until it is connected again. Pictures already uploaded stay in "
                        + "Google Drive and keep showing from the app's copies.",
                "Disconnect", e -> {
                    driveService.disconnect();
                    notify("Google Drive disconnected.", NotificationVariant.LUMO_CONTRAST);
                    render(driveService.status());
                }, "Cancel", e -> {
                });
        confirm.setConfirmButtonTheme("error primary");
        confirm.open();
    }

    private static void notify(String text, NotificationVariant variant) {
        Notification.show(text, 6000, Notification.Position.BOTTOM_CENTER).addThemeVariants(variant);
    }
}
