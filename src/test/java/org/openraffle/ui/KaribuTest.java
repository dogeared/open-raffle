package org.openraffle.ui;

import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.spring.MockSpringSecurity;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.github.mvysny.kaributesting.v10.UtilsKt;
import com.github.mvysny.fakeservlet.FakeRequest;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.openraffle.bgg.FakeBgg;
import org.openraffle.bgg.FakeBggClient;
import org.openraffle.domain.Event;
import org.openraffle.domain.Organizer;
import org.openraffle.domain.Participant;
import org.openraffle.domain.Prize;
import org.openraffle.repository.EventRepository;
import org.openraffle.repository.OrganizerRepository;
import org.openraffle.repository.ParticipantRepository;
import org.openraffle.repository.PrizeRepository;
import org.openraffle.security.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Base for server-side view tests: the real Spring context (H2 instead of Postgres, no
 * Keycloak round-trips) with Karibu's mocked Vaadin servlet, a clean database and a way
 * to "log in" as an admin, an organizer or nobody.
 */
@SpringBootTest(properties = {
        // The OAuth2 client auto-configuration fetches the issuer's discovery document at
        // start-up; a stub registration below stands in for it.
        "spring.autoconfigure.exclude=org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration,org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration",
        "vaadin.launch-browser=false",
        "raffle.images-dir=target/test-images",
})
@AutoConfigureTestDatabase
@Import({KaribuTest.StubOidcClient.class, FakeBgg.class})
public abstract class KaribuTest {

    @TestConfiguration
    static class StubOidcClient {
        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("keycloak")
                    .clientId("open-raffle-app")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "profile", "email")
                    .authorizationUri("http://keycloak.test/realms/open-raffle/protocol/openid-connect/auth")
                    .tokenUri("http://keycloak.test/realms/open-raffle/protocol/openid-connect/token")
                    .jwkSetUri("http://keycloak.test/realms/open-raffle/protocol/openid-connect/certs")
                    .userInfoUri("http://keycloak.test/realms/open-raffle/protocol/openid-connect/userinfo")
                    .userNameAttributeName("preferred_username")
                    .build());
        }
    }

    private static Routes routes;

    @Autowired
    protected ApplicationContext ctx;
    @Autowired
    protected EventRepository events;
    @Autowired
    protected ParticipantRepository participants;
    @Autowired
    protected PrizeRepository prizes;
    @Autowired
    protected OrganizerRepository organizers;

    @BeforeAll
    static void discoverRoutes() {
        routes = new Routes().autoDiscoverViews("org.openraffle");
    }

    private boolean started;

    @Autowired
    FakeBggClient fakeBgg;

    @BeforeEach
    void cleanSlate() {
        fakeBgg.enabled = true;
        fakeBgg.imagesAvailable = true;
        fakeBgg.fullImagesAvailable = true;
        fakeBgg.downloads.clear();
        fakeBgg.searches.clear();
        wipeDatabase();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDownVaadin() {
        if (started) {
            MockVaadin.tearDown();
            started = false;
        }
        SecurityContextHolder.clearContext();
    }

    /**
     * Boots the mocked Vaadin UI at "/" for whoever is logged in (call a login method first,
     * or none for an anonymous visitor). Call again after changing users.
     */
    protected void start() {
        if (started) {
            MockVaadin.tearDown();
        }
        // The login methods run before there is a Vaadin session, so the Authentication sits
        // in the thread-local SecurityContext. Vaadin's Spring integration reads the context
        // from the Vaadin session once one exists, so after setup the same Authentication is
        // stored there too (and on the mocked request), the way the real filter chain would.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        MockSpringSecurity.mock();
        MockVaadin.setup(UI::new, new MockSpringServlet(routes, ctx, UI::new));
        started = true;
        if (authentication != null) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            VaadinSession.getCurrent().getSession()
                    .setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            SecurityContextHolder.setContext(context);
        }
        FakeRequest request = mockRequest();
        request.setUserPrincipalInt(authentication);
        request.setUserInRole((principal, role) -> hasRole(role));
    }

    private void wipeDatabase() {
        List<Prize> all = prizes.findAll();
        all.forEach(p -> p.setClaimedBy(null));
        prizes.saveAll(all);
        participants.deleteAll();
        prizes.deleteAll();
        events.deleteAll();
        organizers.deleteAll();
    }

    /** Someone the app has seen log in as an organizer. */
    protected Organizer knownOrganizer(String email, String name) {
        return organizers.save(new Organizer(email, name));
    }

    // --- users -----------------------------------------------------------------------

    protected void loginAsAdmin() {
        login("admin@example.com", "Raffle Admin", SecurityConfig.ROLE_ADMIN, SecurityConfig.ROLE_ORGANIZER);
    }

    protected void loginAsOrganizer(String email) {
        login(email, "Helpful Organizer", SecurityConfig.ROLE_ORGANIZER);
    }

    protected void loginWithoutRoles(String email) {
        login(email, "Nobody Special");
    }

    protected void logout() {
        SecurityContextHolder.clearContext();
    }

    private static FakeRequest mockRequest() {
        return UtilsKt.getMock(VaadinRequest.getCurrent());
    }

    private static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String wanted = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(wanted));
    }

    private static void login(String email, String name, String... roles) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.add(new SimpleGrantedAuthority("OIDC_USER"));
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        OidcIdToken token = OidcIdToken.withTokenValue("test-token")
                .subject(email)
                .claim("email", email)
                .claim("name", name)
                .claim("preferred_username", email.substring(0, email.indexOf('@')))
                .build();
        DefaultOidcUser user = new DefaultOidcUser(authorities, token, "preferred_username");
        SecurityContextHolder.getContext().setAuthentication(new OAuth2AuthenticationToken(user, authorities, "keycloak"));
    }

    // --- data ------------------------------------------------------------------------

    protected Event event(String name, String... organizerEmails) {
        Event e = new Event();
        e.setName(name);
        e.setOrganizerEmails(new LinkedHashSet<>(List.of(organizerEmails)));
        return events.save(e);
    }

    protected Prize prize(Event event, String name) {
        Prize p = new Prize();
        p.setEvent(event);
        p.setName(name);
        return prizes.save(p);
    }

    protected Participant participant(Event event, String name, long from, long to) {
        Participant p = new Participant();
        p.setEvent(event);
        p.setName(name);
        p.setPhone("555-0100");
        p.addRange(from, to);
        p.setToken("token-" + name.toLowerCase().replace(' ', '-'));
        p.setWishlist(new ArrayList<>());
        return participants.save(p);
    }

    protected static void navigate(String path) {
        UI.getCurrent().navigate(path);
    }

    /**
     * Presses the confirm button of a ConfirmDialog the way the browser does: the dialog
     * closes first, then the confirm listener runs (so a notification it shows is not
     * parented under the still-open modal dialog).
     */
    protected static void confirm(ConfirmDialog dialog) {
        dialog.close();
        ComponentUtil.fireEvent(dialog, new ConfirmDialog.ConfirmEvent(dialog, true));
        MockVaadin.clientRoundtrip();
    }
}
