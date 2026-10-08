package org.openraffle.drive;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;
import org.openraffle.service.QrCodeService;

/**
 * Where Google sends the browser back after the consent screen. The state must match the
 * one the Settings page put in this session, so a stray or forged callback does nothing.
 */
@RestController
public class DriveCallbackController {

    private static final Logger log = LoggerFactory.getLogger(DriveCallbackController.class);
    public static final String PATH = "/drive/callback";
    public static final String STATE_ATTRIBUTE = "drive.oauth.state";
    public static final String RESULT_ATTRIBUTE = "drive.oauth.result";
    public static final String RESULT_DETAIL_ATTRIBUTE = "drive.oauth.result.detail";

    private final DriveService driveService;
    private final QrCodeService publicUrl;

    public DriveCallbackController(DriveService driveService, QrCodeService publicUrl) {
        this.driveService = driveService;
        this.publicUrl = publicUrl;
    }

    /** The absolute callback URL, which Google must know as an authorised redirect URI. */
    public static String redirectUri(String publicBaseUrl) {
        return publicBaseUrl + PATH;
    }

    /** How the round trip ended; what Settings shows. Nothing from the query string is kept. */
    public enum Outcome {
        CONNECTED("Google Drive connected."),
        NOT_FROM_HERE("That Google sign-in did not start here. Please try connecting again."),
        DECLINED("Google Drive was not connected: access was declined on Google's consent screen."),
        ADMIN_POLICY("Google Drive was not connected: the account's administrator does not allow this app."),
        ORG_INTERNAL("Google Drive was not connected: this app is restricted to another organisation's accounts."),
        GOOGLE_ERROR("Google Drive was not connected (Google reported an error)."),
        FAILED("Google Drive was not connected");

        private final String message;

        Outcome(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }

        public boolean isSuccess() {
            return this == CONNECTED;
        }
    }

    @GetMapping(PATH)
    public RedirectView callback(@RequestParam(required = false) String code,
                                 @RequestParam(required = false) String state,
                                 @RequestParam(required = false) String error,
                                 HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String expected = session == null ? null : (String) session.getAttribute(STATE_ATTRIBUTE);
        if (session != null) {
            session.removeAttribute(STATE_ATTRIBUTE);
        }
        Outcome outcome;
        String detail = null;
        if (expected == null || state == null || !expected.equals(state)) {
            outcome = Outcome.NOT_FROM_HERE;
        } else if (error != null || code == null) {
            if ("access_denied".equals(error)) {
                outcome = Outcome.DECLINED;
            } else if ("admin_policy_enforced".equals(error)) {
                outcome = Outcome.ADMIN_POLICY;
            } else if ("org_internal".equals(error)) {
                outcome = Outcome.ORG_INTERNAL;
            } else {
                outcome = Outcome.GOOGLE_ERROR;
            }
        } else {
            try {
                driveService.complete(code, redirectUri(publicUrl.publicBaseUrl(request)));
                outcome = Outcome.CONNECTED;
            } catch (DriveException e) {
                log.warn("Google Drive connection failed: {}", e.getMessage());
                outcome = Outcome.FAILED;
                detail = e.getMessage(); // our own wording about Google's answer, not the request's
            }
        }
        if (session != null) {
            session.setAttribute(RESULT_ATTRIBUTE, outcome);
            session.setAttribute(RESULT_DETAIL_ATTRIBUTE, detail);
        }
        return new RedirectView(request.getContextPath() + "/settings", true);
    }
}
