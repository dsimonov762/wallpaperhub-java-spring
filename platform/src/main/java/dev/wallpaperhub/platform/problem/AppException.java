package dev.wallpaperhub.platform.problem;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A failure the service wants to report to the caller as problem+json (README 5.3). {@code slug} is the problem type
 * ({@code validation-failed}, {@code not-found}, ...), {@code code} the message key under {@code error.} in the bundles.
 */
public class AppException extends RuntimeException {

    private final int status;
    private final String slug;
    private final String code;
    private final Map<String, Object> args;

    public AppException(int status, String slug, String code, Map<String, Object> args) {
        super(code != null ? code : slug);
        this.status = status;
        this.slug = slug;
        this.code = code;
        this.args = args == null ? Map.of() : Map.copyOf(args);
    }

    public int status() {
        return status;
    }

    public String slug() {
        return slug;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> args() {
        return args;
    }

    public static AppException validation(String code, String argName, Object argValue) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put(argName, argValue);
        return new AppException(400, "validation-failed", code, args);
    }

    public static AppException notFound(String code) {
        return new AppException(404, "not-found", code, Map.of());
    }

    public static AppException forbidden(String code) {
        return new AppException(403, "forbidden", code, Map.of());
    }

    public static AppException conflict(String code) {
        return new AppException(409, "conflict", code, Map.of());
    }

    public static AppException upstreamUnavailable(String code) {
        return new AppException(503, "upstream-unavailable", code, Map.of());
    }

    /** A stand-in until the real implementation exists (WP-S1). */
    public static AppException notImplemented() {
        return new AppException(501, "internal-error", null, Map.of());
    }
}
