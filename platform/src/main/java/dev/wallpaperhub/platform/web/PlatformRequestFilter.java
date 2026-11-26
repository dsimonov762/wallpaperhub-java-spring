package dev.wallpaperhub.platform.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The first filter of the API port. Per request: the request id (E6, echoed in the response), the trace id of the incoming
 * {@code traceparent} or a new one (E2), the MDC fields of the JSON log (L1, L2), {@code X-Content-Type-Options}, and one
 * access-log line at {@code info} (method, path without the query string, status, duration; SERVICE-SPEC section 6).
 */
public final class PlatformRequestFilter extends OncePerRequestFilter implements Ordered {

    private static final Logger ACCESS = LoggerFactory.getLogger("access");

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        String requestId = RequestContext.requestId(request.getHeader("X-Request-Id"));
        String traceId = RequestContext.traceId(request.getHeader("traceparent"));
        String spanId = RequestContext.newSpanId();
        request.setAttribute(RequestContext.REQUEST_ID, requestId);
        request.setAttribute(RequestContext.TRACE_ID, traceId);
        request.setAttribute(RequestContext.SPAN_ID, spanId);
        response.setHeader("X-Request-Id", requestId);
        response.setHeader("X-Content-Type-Options", "nosniff");
        MDC.put("request_id", requestId);
        MDC.put("trace_id", traceId);
        MDC.put("span_id", spanId);
        try {
            chain.doFilter(request, response);
        } finally {
            Object user = request.getAttribute(RequestContext.USER_ID);
            if (user != null) {
                MDC.put("user_id", user.toString());
            }
            long millis = (System.nanoTime() - start) / 1_000_000;
            ACCESS.info("{} {} {} {} ms", request.getMethod(), request.getRequestURI(), response.getStatus(), millis);
            MDC.remove("request_id");
            MDC.remove("trace_id");
            MDC.remove("span_id");
            MDC.remove("user_id");
        }
    }
}
