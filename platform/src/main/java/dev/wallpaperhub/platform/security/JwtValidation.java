package dev.wallpaperhub.platform.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/** SERVICE-SPEC section 4 (A1 to A4): signature against the JWKS (done by the decoder), then issuer, audience, expiry, not-before and token type. */
public final class JwtValidation {

    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private JwtValidation() {}

    public static OAuth2TokenValidator<Jwt> validator(String issuer, String audience) {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(CLOCK_SKEW), // exp and nbf
                new JwtIssuerValidator(issuer),
                new JwtClaimValidator<Collection<String>>(
                        JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience)),
                new JwtClaimValidator<String>("typ", "Bearer"::equals)); // not an ID or refresh token
    }

    /** Keycloak puts the realm roles in {@code realm_access.roles}; they become {@code ROLE_<name>}. */
    public static JwtAuthenticationConverter converter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        Converter<Jwt, Collection<GrantedAuthority>> authorities = jwt -> {
            List<GrantedAuthority> result = new ArrayList<>();
            Object realmAccess = jwt.getClaim("realm_access");
            if (realmAccess instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> roles) {
                for (Object role : roles) {
                    result.add(new SimpleGrantedAuthority("ROLE_" + role));
                }
            }
            return result;
        };
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
