package com.docusphere.backend.authentication.service.security;

import com.docusphere.backend.Common.config.AppConfig;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.authentication.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final String ACCESS_TOKEN_COOKIE = "accessToken";
    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    private static final String ACCESS_COOKIE_PATH = "/";
    private static final String REFRESH_COOKIE_PATH = "/api/auth/refresh";
    private static final String COOKIE_SAME_SITE_PROD = "None";
    private static final String COOKIE_SAME_SITE_DEV = "Lax";

    private final UserService userService;
    private final JwtService jwtService;
    private final AppConfig appConfig;
    private final UserDetailsService userDetailsService;

    @Value("${jwt.access.expiration}")
    private long accessTokenCookieMaxAge;

    @Value("${app.session.remember-me-expiry}")
    private long rememberMeRefreshCookieMaxAge;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (!(authentication.getPrincipal() instanceof OAuth2User oauth2User)) {
            throw new BadCredentialsException("OAuth2 authentication principal is invalid.");
        }

        String provider = authentication instanceof OAuth2AuthenticationToken token
                ? token.getAuthorizedClientRegistrationId()
                : "oauth2";

        Map<String, Object> attributes = oauth2User.getAttributes();
        String email = resolveEmail(authentication, oauth2User, attributes);
        if (email == null || email.isBlank()) {
            throw new BadCredentialsException("OAuth2 login did not return an email address.");
        }

        String fullName = resolveFullName(oauth2User, attributes);
        String picture = resolvePicture(oauth2User, attributes);

        User user = userService.processOAuth2User(email, fullName, picture, provider);
        UserDetails loadedUserDetails = userDetailsService.loadUserByUsername(user.getEmail());

        String accessToken = jwtService.generateAccessToken(loadedUserDetails, user.getRole().getName(), user.getId());
        String refreshToken = jwtService.generateRefreshToken(loadedUserDetails, user.getId());

        response.addHeader(HttpHeaders.SET_COOKIE,
                createCookie(ACCESS_TOKEN_COOKIE, accessToken, ACCESS_COOKIE_PATH, Duration.ofMillis(accessTokenCookieMaxAge)).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                createCookie(REFRESH_TOKEN_COOKIE, refreshToken, REFRESH_COOKIE_PATH, Duration.ofMillis(rememberMeRefreshCookieMaxAge)).toString());

        response.sendRedirect(getFrontendRedirectUrl());
    }

    private String getFrontendRedirectUrl() {
        String frontendUrl = appConfig.getFrontendUrl();
        if (frontendUrl == null || frontendUrl.isBlank()) {
            return "http://localhost:5173";
        }
        return frontendUrl;
    }

    private String getAttribute(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        return value == null ? null : value.toString();
    }

    private String resolveEmail(Authentication authentication, OAuth2User oauth2User, Map<String, Object> attributes) {
        if (oauth2User instanceof OidcUser oidcUser && oidcUser.getEmail() != null && !oidcUser.getEmail().isBlank()) {
            return oidcUser.getEmail();
        }

        String[] candidates = new String[] {"email", "emailAddress", "upn", "preferred_username"};
        for (String candidate : candidates) {
            String value = getAttribute(attributes, candidate);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        String principalName = authentication.getName();
        if (principalName != null && principalName.contains("@")) {
            return principalName;
        }

        return null;
    }

    private String resolveFullName(OAuth2User oauth2User, Map<String, Object> attributes) {
        if (oauth2User instanceof OidcUser oidcUser && oidcUser.getFullName() != null && !oidcUser.getFullName().isBlank()) {
            return oidcUser.getFullName();
        }

        String[] candidates = new String[] {"name", "full_name", "displayName", "given_name"};
        for (String candidate : candidates) {
            String value = getAttribute(attributes, candidate);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    private String resolvePicture(OAuth2User oauth2User, Map<String, Object> attributes) {
        if (oauth2User instanceof OidcUser oidcUser && oidcUser.getPicture() != null && !oidcUser.getPicture().isBlank()) {
            return oidcUser.getPicture();
        }

        String[] candidates = new String[] {"picture", "avatar_url", "avatarUrl", "photoUrl"};
        for (String candidate : candidates) {
            String value = getAttribute(attributes, candidate);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    private ResponseCookie createCookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(isCookieSecure())
                .sameSite(getCookieSameSite())
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    private boolean isCookieSecure() {
        String frontendUrl = appConfig.getFrontendUrl();
        return frontendUrl != null && frontendUrl.startsWith("https://");
    }

    private String getCookieSameSite() {
        return isCookieSecure() ? COOKIE_SAME_SITE_PROD : COOKIE_SAME_SITE_DEV;
    }
}



