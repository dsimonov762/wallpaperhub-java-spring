package dev.wallpaperhub.platform.problem;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import dev.wallpaperhub.platform.i18n.Messages;
import dev.wallpaperhub.platform.web.RequestContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.json.JsonMapper;

class ProblemsTest {

    private final Problems problems = new Problems(Messages.load(JsonMapper.builder().build()));

    @Test
    void notFoundHasTheFieldsOfTheContract() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/nothing");
        request.setAttribute(RequestContext.TRACE_ID, "4bf92f3577b34da6a3ce929d0e0e4736");
        request.setAttribute(RequestContext.REQUEST_ID, "req-1");

        Map<String, Object> body = problems.body(request, 404, "not-found", null, Map.of(), List.of(), "de");

        assertThat(body).containsEntry("type", "https://wallpaperhub.dev/problems/not-found")
                .containsEntry("status", 404)
                .containsEntry("instance", "/api/v1/nothing")
                .containsEntry("traceId", "4bf92f3577b34da6a3ce929d0e0e4736")
                .containsEntry("requestId", "req-1");
        assertThat(body.get("title")).isEqualTo(problems.messages().text("de", "problem.not-found.title", Map.of()));
        assertThat(body).doesNotContainKey("errors");
    }

    @Test
    void aCodeWithAMessageSetsTheDetailAndTheCode() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        Map<String, Object> body = problems.body(request, 400, "validation-failed", "pagination.size-too-large",
                Map.of("max", 100), List.of(), "en");
        assertThat(body).containsEntry("code", "pagination.size-too-large");
        assertThat(body.get("detail").toString()).contains("100");
    }
}
