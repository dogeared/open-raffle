package org.openraffle.security;

import com.nimbusds.jwt.JWTParser;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;

import java.text.ParseException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Admin views require an OIDC login against Keycloak. Participant wishlist pages
 * (reached via QR code) are explicitly {@code @AnonymousAllowed}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Manages events and who organizes them; implies {@link #ROLE_ORGANIZER}. */
    public static final String ROLE_ADMIN = "ADMIN";
    /** Runs the events they are listed on: participants, prizes, the draw. */
    public static final String ROLE_ORGANIZER = "ORGANIZER";

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // The hosting platform's health check must not be bounced to the login page.
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(EndpointRequest.to(HealthEndpoint.class, InfoEndpoint.class)).permitAll()
                // Prize pictures appear on the public prize list and participants' wishlists.
                .requestMatchers(HttpMethod.GET, "/images/*").permitAll());
        // Vaadin's defaults (static resources, CSRF, navigation access control), plus:
        // redirect unauthenticated users straight to Keycloak; after logout, send them
        // through Keycloak's end-session endpoint and back to the app root; a fresh login
        // that did not start from a protected page lands on the event list.
        http.with(VaadinSecurityConfigurer.vaadin(), vaadin -> vaadin
                .oauth2LoginPage("/oauth2/authorization/keycloak", "{baseUrl}")
                .defaultSuccessUrl("/events"));
        http.oauth2Login(login -> login
                .userInfoEndpoint(userInfo -> userInfo.oidcUserService(keycloakOidcUserService())));
        return http.build();
    }

    /**
     * Loads the OIDC user as usual, then adds {@code ROLE_*} authorities for the realm roles
     * in the <em>access token</em>. Keycloak's default realm-roles mapper puts roles only
     * there, so without this a stock Keycloak client would never grant admin access.
     */
    @Bean
    OAuth2UserService<OidcUserRequest, OidcUser> keycloakOidcUserService() {
        OidcUserService delegate = new OidcUserService();
        return request -> {
            OidcUser user = delegate.loadUser(request);
            Set<GrantedAuthority> authorities = new HashSet<>(user.getAuthorities());
            for (String role : realmRoles(jwtClaims(request.getAccessToken().getTokenValue()))) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
            String nameAttribute = request.getClientRegistration().getProviderDetails()
                    .getUserInfoEndpoint().getUserNameAttributeName();
            return nameAttribute == null
                    ? new DefaultOidcUser(authorities, user.getIdToken(), user.getUserInfo())
                    : new DefaultOidcUser(authorities, user.getIdToken(), user.getUserInfo(), nameAttribute);
        };
    }

    /**
     * Keycloak puts realm roles under {@code realm_access.roles}. Map them to
     * {@code ROLE_*} authorities so Vaadin's {@code @RolesAllowed} works. The claim is
     * looked for in the ID token and in the userinfo response here, and in the access
     * token by {@link #keycloakOidcUserService()}: which one carries it depends on how
     * the realm-roles mapper is configured in Keycloak.
     */
    @Bean
    GrantedAuthoritiesMapper keycloakAuthoritiesMapper() {
        return authorities -> {
            Set<GrantedAuthority> mapped = new HashSet<>(authorities);
            for (GrantedAuthority authority : authorities) {
                List<Map<String, Object>> claimSources = switch (authority) {
                    case OidcUserAuthority oidc -> oidc.getUserInfo() == null
                            ? List.of(oidc.getIdToken().getClaims())
                            : List.of(oidc.getIdToken().getClaims(), oidc.getUserInfo().getClaims());
                    case OAuth2UserAuthority oauth -> List.of(oauth.getAttributes());
                    default -> List.of();
                };
                for (Map<String, Object> claims : claimSources) {
                    for (String role : realmRoles(claims)) {
                        mapped.add(new SimpleGrantedAuthority("ROLE_" + role));
                    }
                }
            }
            // An admin can do everything an organizer can, without Keycloak needing a composite role.
            if (mapped.contains(new SimpleGrantedAuthority("ROLE_" + ROLE_ADMIN))) {
                mapped.add(new SimpleGrantedAuthority("ROLE_" + ROLE_ORGANIZER));
            }
            return mapped;
        };
    }

    /**
     * Claims of a JWT without signature verification. The access token arrives straight
     * from Keycloak's token endpoint over TLS in the same response as the (verified) ID
     * token, so it is trusted the same way; an opaque or malformed token yields no claims.
     */
    static Map<String, Object> jwtClaims(String token) {
        try {
            return JWTParser.parse(token).getJWTClaimsSet().getClaims();
        } catch (ParseException e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    static Collection<String> realmRoles(Map<String, Object> claims) {
        Object realmAccess = claims.get("realm_access");
        if (realmAccess instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> roles) {
            return (Collection<String>) roles;
        }
        return List.of();
    }
}
