package dev.wallpaperhub.platform.web;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

/** Per-request identifiers: request id (E6), trace id and span id (E2, L2). Set by {@link PlatformRequestFilter}. */
public final class RequestContext {

    public static final String REQUEST_ID = "dev.wallpaperhub.requestId";
    public static final String TRACE_ID = "dev.wallpaperhub.traceId";
    public static final String SPAN_ID = "dev.wallpaperhub.spanId";
    public static final String USER_ID = "dev.wallpaperhub.userId";

    /** E6: only a plain id is echoed. Anything else would reach a response header unchecked (HTTP response splitting). */
    private static final Pattern PLAIN_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    /** W3C Trace Context, version 00: 00-&lt;32 hex trace id&gt;-&lt;16 hex parent id&gt;-&lt;2 hex flags&gt;. */
    private static final Pattern TRACEPARENT = Pattern.compile("00-([0-9a-f]{32})-([0-9a-f]{16})-[0-9a-f]{2}");
    private static final String ZERO_TRACE = "0".repeat(32);
    private static final String ZERO_SPAN = "0".repeat(16);

    private RequestContext() {
    }

    /** The caller's {@code X-Request-Id} when it is a plain id, otherwise a new one. */
    public static String requestId(String header) {
        if (header != null && PLAIN_ID.matcher(header).matches()) {
            return header;
        }
        return UUID.randomUUID().toString();
    }

    /** The trace id of a {@code traceparent} header, or a new random one when there is none or it is malformed. */
    public static String traceId(String traceparent) {
        if (traceparent != null) {
            Matcher m = TRACEPARENT.matcher(traceparent.strip());
            if (m.matches() && !ZERO_TRACE.equals(m.group(1)) && !ZERO_SPAN.equals(m.group(2))) {
                return m.group(1);
            }
        }
        return randomHex(32);
    }

    /** A span id for this request (this service is not a tracer yet; OpenTelemetry replaces it, see ROADMAP). */
    public static String newSpanId() {
        return randomHex(16);
    }

    /** What the filter stored, or a new random value when the filter did not run (an error before it). */
    public static String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TRACE_ID);
        return value != null ? value.toString() : randomHex(32);
    }

    public static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ID);
        return value != null ? value.toString() : null;
    }

    static String randomHex(int length) {
        StringBuilder sb = new StringBuilder(length);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        while (sb.length() < length) {
            sb.append(String.format("%016x", random.nextLong()));
        }
        String hex = sb.substring(0, length);
        return hex.chars().allMatch(c -> c == '0') ? randomHex(length) : hex;
    }
}
