package dev.wallpaperhub.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PlatformRequestFilterTest {

    private final PlatformRequestFilter filter = new PlatformRequestFilter();

    @Test
    void echoesAPlainRequestIdAndStoresTheTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("X-Request-Id", "req-42");
        request.addHeader("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("req-42");
        assertThat(request.getAttribute(RequestContext.TRACE_ID)).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void takesTheTraceAndSpanIdOfTheActiveSpan() throws Exception {
        SpanContext active = SpanContext.create(
                "0af7651916cd43dd8448eb211c80319c",
                "b7ad6b7169203331",
                TraceFlags.getSampled(),
                TraceState.getDefault());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        try (Scope ignored = Span.wrap(active).makeCurrent()) {
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }
        assertThat(request.getAttribute(RequestContext.TRACE_ID)).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        assertThat(request.getAttribute(RequestContext.SPAN_ID)).isEqualTo("b7ad6b7169203331");
    }

    @Test
    void doesNotEchoAnUnsafeRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.addHeader("X-Request-Id", "evil\r\nSet-Cookie: a=b");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getHeader("X-Request-Id")).doesNotContain("\n", "\r", "Set-Cookie");
    }

    @Test
    void clearsTheMdcAfterTheRequest() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(MDC.get("request_id")).isNull();
        assertThat(MDC.get("trace_id")).isNull();
    }
}
