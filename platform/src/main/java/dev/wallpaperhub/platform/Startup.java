package dev.wallpaperhub.platform;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The one entry point of every service: checks the required environment variables, then starts Spring.
 *
 * <p>SERVICE-SPEC section 3 (check C1): a missing or empty required variable stops the process with a non-zero exit within
 * seconds and a log line that names the variable. Spring's own binding errors name the property, not the variable, and
 * treat an empty value as present, so the variables are checked here, before the application context exists.
 */
public final class Startup {

    /** Required by every service; a service adds its own (database, Kafka, ...) to this list. */
    public static final List<String> COMMON_REQUIRED = List.of(
            "OIDC_ISSUER_URI", "OIDC_JWKS_URI", "OIDC_CLIENT_ID", "OTEL_SERVICE_NAME", "APP_PROFILE");

    private Startup() {
    }

    public static ConfigurableApplicationContext run(Class<?> application, String[] args, String... serviceRequired) {
        Set<String> required = new LinkedHashSet<>(COMMON_REQUIRED);
        required.addAll(List.of(serviceRequired));
        List<String> missing = missing(System::getenv, required);
        if (!missing.isEmpty()) {
            String service = System.getenv("OTEL_SERVICE_NAME");
            System.err.println(errorLine(service == null || service.isBlank() ? application.getSimpleName() : service,
                    "missing or empty required environment variable(s): " + String.join(", ", missing)));
            System.exit(1);
        }
        return SpringApplication.run(application, args);
    }

    /** The names from {@code required} whose value is absent or blank. */
    public static List<String> missing(Function<String, String> env, Iterable<String> required) {
        List<String> missing = new ArrayList<>();
        for (String name : required) {
            String value = env.apply(name);
            if (value == null || value.isBlank()) {
                missing.add(name);
            }
        }
        return missing;
    }

    /** A log line in the format of {@code logging.JsonLayout}, for the moment before logging exists. */
    static String errorLine(String service, String message) {
        return "{\"timestamp\":\"" + Instant.now() + "\",\"level\":\"error\",\"message\":\"" + json(message)
                + "\",\"service\":\"" + json(service) + "\"}";
    }

    private static String json(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
