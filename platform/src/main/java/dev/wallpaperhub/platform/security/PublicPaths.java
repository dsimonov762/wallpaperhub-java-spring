package dev.wallpaperhub.platform.security;

import java.util.List;

/**
 * Paths a service serves without a token (the operations of its OpenAPI file that have {@code security: []}). Declare one as a
 * bean; everything else under the API port requires a valid access token. A request that <em>sends</em> an invalid token is still
 * refused on these paths.
 */
public record PublicPaths(List<String> patterns) {

    public PublicPaths(String... patterns) {
        this(List.of(patterns));
    }
}
