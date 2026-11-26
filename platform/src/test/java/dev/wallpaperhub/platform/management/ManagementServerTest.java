package dev.wallpaperhub.platform.management;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ManagementServerTest {

    private final HttpClient client = HttpClient.newHttpClient();
    private final AtomicBoolean dependency = new AtomicBoolean(true);
    private ManagementServer server;

    private HttpResponse<String> get(String path) throws Exception {
        URI uri = URI.create("http://127.0.0.1:" + server.boundPort() + path);
        return client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private void startServer() {
        server = new ManagementServer(0, List.of(new ReadinessCheck() {
            @Override
            public String name() {
                return "dependency";
            }

            @Override
            public boolean ready() {
                return dependency.get();
            }
        }));
        server.start();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void liveIsUpButStartupAndReadyWaitForTheContext() throws Exception {
        startServer();
        assertThat(get("/health/live").statusCode()).isEqualTo(200);
        assertThat(get("/health/startup").statusCode()).isEqualTo(503);
        assertThat(get("/health/ready").statusCode()).isEqualTo(503);

        server.markStarted();
        HttpResponse<String> ready = get("/health/ready");
        assertThat(ready.statusCode()).isEqualTo(200);
        assertThat(ready.body()).isEqualTo("{\"status\":\"UP\"}");
        assertThat(ready.headers().firstValue("Content-Type")).hasValue("application/json");
        assertThat(get("/health/startup").statusCode()).isEqualTo(200);
    }

    @Test
    void readyFollowsTheDependencyAndShutdown() throws Exception {
        startServer();
        server.markStarted();
        assertThat(get("/health/ready").statusCode()).isEqualTo(200);

        dependency.set(false);
        HttpResponse<String> down = get("/health/ready");
        assertThat(down.statusCode()).isEqualTo(503);
        assertThat(down.body()).isEqualTo("{\"status\":\"DOWN\"}");
        assertThat(get("/health/live").statusCode()).isEqualTo(200);

        dependency.set(true);
        assertThat(get("/health/ready").statusCode()).isEqualTo(200);
        server.markClosing();
        assertThat(get("/health/ready").statusCode()).isEqualTo(503);
        assertThat(get("/health/live").statusCode()).isEqualTo(200);
    }

    @Test
    void otherPathsAreNotFound() throws Exception {
        startServer();
        assertThat(get("/health/other").statusCode()).isEqualTo(404);
    }
}
