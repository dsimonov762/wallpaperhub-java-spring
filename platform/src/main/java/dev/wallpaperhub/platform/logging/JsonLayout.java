package dev.wallpaperhub.platform.logging;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.LayoutBase;

/**
 * One JSON object per line with the fields of SERVICE-SPEC section 6: {@code timestamp} (RFC 3339, UTC, milliseconds),
 * {@code level} (lower case), {@code message}, {@code service}, and from the MDC {@code trace_id}, {@code span_id},
 * {@code request_id}, {@code user_id} when they are set. Configured in {@code logback-spring.xml}.
 */
public final class JsonLayout extends LayoutBase<ILoggingEvent> {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final String[] MDC_FIELDS = {"trace_id", "span_id", "request_id", "user_id"};

    private final String service;

    public JsonLayout() {
        String name = System.getenv("OTEL_SERVICE_NAME");
        this.service = name == null || name.isBlank() ? "unknown-service" : name;
    }

    @Override
    public String doLayout(ILoggingEvent event) {
        StringBuilder sb = new StringBuilder(256).append('{');
        field(sb, "timestamp", TIMESTAMP.format(event.getInstant())).append(',');
        field(sb, "level", event.getLevel().toString().toLowerCase(Locale.ROOT)).append(',');
        field(sb, "message", event.getFormattedMessage()).append(',');
        field(sb, "service", service).append(',');
        field(sb, "logger", event.getLoggerName()).append(',');
        field(sb, "thread", event.getThreadName());
        Map<String, String> mdc = event.getMDCPropertyMap();
        if (mdc != null) {
            for (String key : MDC_FIELDS) {
                String value = mdc.get(key);
                if (value != null && !value.isEmpty()) {
                    sb.append(',');
                    field(sb, key, value);
                }
            }
        }
        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable != null) {
            sb.append(',');
            field(sb, "stack_trace", ThrowableProxyUtil.asString(throwable));
        }
        return sb.append("}\n").toString();
    }

    private static StringBuilder field(StringBuilder sb, String name, String value) {
        sb.append('"').append(name).append("\":");
        if (value == null) {
            return sb.append("null");
        }
        sb.append('"');
        escape(sb, value);
        return sb.append('"');
    }

    static void escape(StringBuilder sb, String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
    }
}
