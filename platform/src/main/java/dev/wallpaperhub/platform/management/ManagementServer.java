package dev.wallpaperhub.platform.management;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * The management port (default 8081): {@code /health/live}, {@code /health/ready}, {@code /health/startup} (SERVICE-SPEC section 2),
 * answering {@code 200 {"status":"UP"}} or {@code 503 {"status":"DOWN"}}, with no authentication.
 *
 * <p>It is the JDK's own HTTP server on purpose: separate from the API port and its security chain, JSON with the content type the
 * checks expect, and it keeps answering while the API port drains during shutdown (S1) because it is stopped last.
 * <ul>
 *   <li>live: the process runs;</li>
 *   <li>startup: the application context has finished starting;</li>
 *   <li>ready: started, not closing, and every {@link ReadinessCheck} is ready; turns DOWN as soon as shutdown begins.</li>
 * </ul>
 */
public final class ManagementServer implements SmartLifecycle, ApplicationListener<ApplicationEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(ManagementServer.class);

    private final int port;
    private final List<ReadinessCheck> checks;
    private HttpServer server;
    private ExecutorService executor;
    private volatile boolean running;
    private volatile boolean started;
    private volatile boolean closing;

    public ManagementServer(int port, List<ReadinessCheck> checks) {
        this.port = port;
        this.checks = List.copyOf(checks);
    }

    /** The port actually bound (useful when 0 was asked for). */
    public int boundPort() {
        return server.getAddress().getPort();
    }

    @Override
    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            throw new IllegalStateException("cannot listen on the management port " + port, e);
        }
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
        running = true;
        LOG.info("management port {} is listening", boundPort());
    }

    @Override
    public void stop() {
        closing = true;
        running = false;
        if (server != null) {
            server.stop(0);
            executor.close();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ContextRefreshedEvent) {
            markStarted();
        } else if (event instanceof ContextClosedEvent) {
            markClosing();
        }
    }

    public void markStarted() {
        started = true;
    }

    public void markClosing() {
        closing = true;
    }

    private boolean ready() {
        if (!started || closing) {
            return false;
        }
        for (ReadinessCheck check : checks) {
            try {
                if (!check.ready()) {
                    return false;
                }
            } catch (RuntimeException e) {
                LOG.warn("readiness check {} failed: {}", check.name(), e.toString());
                return false;
            }
        }
        return true;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod();
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            BooleanSupplier probe = switch (exchange.getRequestURI().getPath()) {
                case "/health/live" -> () -> true;
                case "/health/startup" -> () -> started;
                case "/health/ready" -> this::ready;
                default -> null;
            };
            if (probe == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            boolean up = probe.getAsBoolean();
            byte[] body = (up ? "{\"status\":\"UP\"}" : "{\"status\":\"DOWN\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            boolean head = "HEAD".equals(method);
            exchange.sendResponseHeaders(up ? 200 : 503, head ? -1 : body.length);
            if (!head) {
                exchange.getResponseBody().write(body);
            }
        }
    }
}
