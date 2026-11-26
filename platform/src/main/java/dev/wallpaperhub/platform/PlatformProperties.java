package dev.wallpaperhub.platform;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The typed configuration every service has. The values come from the environment variables of SERVICE-SPEC section 3,
 * wired in {@code wallpaperhub-platform.yml}; {@link Startup} has already checked that they are present.
 */
@ConfigurationProperties("wallpaperhub")
public record PlatformProperties(String serviceName, String profile, Oidc oidc, Management management) {

    /** {@code OIDC_ISSUER_URI}, {@code OIDC_JWKS_URI}, {@code OIDC_CLIENT_ID} (which is also the audience of accepted tokens). */
    public record Oidc(String issuerUri, String jwksUri, String clientId) {
    }

    /** {@code MGMT_PORT}, default 8081. */
    public record Management(int port) {
    }
}
