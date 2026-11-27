package dev.wallpaperhub.platform.management;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** The signing keys have been reachable at least once (SERVICE-SPEC section 2). After that the decoder caches them and the service does not depend on the identity provider for every request. */
public final class JwksReadiness implements ReadinessCheck {

    private final URI uri;
    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private volatile boolean reached;

    public JwksReadiness(String jwksUri) {
        this.uri = URI.create(jwksUri);
    }

    @Override
    public String name() {
        return "jwks";
    }

    @Override
    public boolean ready() {
        if (reached) {
            return true;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            reached =
                    client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (IOException e) {
            // not reachable yet
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return reached;
    }
}
