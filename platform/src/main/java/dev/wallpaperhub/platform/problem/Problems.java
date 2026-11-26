package dev.wallpaperhub.platform.problem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.wallpaperhub.platform.i18n.Messages;
import dev.wallpaperhub.platform.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;

/** Builds the body of an error response (README 5.3, SERVICE-SPEC section 5): RFC 9457 fields plus {@code traceId}, {@code code}, {@code requestId}, {@code errors}. */
public final class Problems {

    /** The type URI is the base plus the slug; it does not have to resolve. */
    public static final String TYPE_BASE = "https://wallpaperhub.dev/problems/";

    /** One entry of {@code errors}: the field, the constraint code and the localized message. */
    public record FieldIssue(String field, String code, String message) {
    }

    private final Messages messages;

    public Problems(Messages messages) {
        this.messages = messages;
    }

    public Messages messages() {
        return messages;
    }

    public Map<String, Object> body(HttpServletRequest request, int status, String slug, String code,
                                    Map<String, ?> args, List<FieldIssue> errors, String lang) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", TYPE_BASE + slug);
        body.put("title", messages.text(lang, "problem." + slug + ".title", Map.of()));
        body.put("status", status);
        String detailKey = code != null && messages.has("error." + code) ? "error." + code : "problem." + slug + ".detail";
        body.put("detail", messages.text(lang, detailKey, args));
        body.put("instance", request.getRequestURI());
        body.put("traceId", RequestContext.traceId(request));
        if (code != null) {
            body.put("code", code);
        }
        String requestId = RequestContext.requestId(request);
        if (requestId != null) {
            body.put("requestId", requestId);
        }
        if (errors != null && !errors.isEmpty()) {
            List<Map<String, Object>> list = errors.stream().map(e -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("field", e.field());
                m.put("code", e.code());
                m.put("message", e.message());
                return m;
            }).toList();
            body.put("errors", list);
        }
        return body;
    }
}
