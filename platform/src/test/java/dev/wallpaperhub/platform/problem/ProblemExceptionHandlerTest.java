package dev.wallpaperhub.platform.problem;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wallpaperhub.platform.i18n.Messages;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.json.JsonMapper;

class ProblemExceptionHandlerTest {

    private final Problems problems =
            new Problems(Messages.load(JsonMapper.builder().build()));
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");

    /** A service that knows that its database errors are not bugs. */
    private static final ExceptionClassifier DATABASE =
            failure -> failure instanceof IllegalStateException ? AppException.dependencyDown("database") : null;

    @Test
    void anUnknownExceptionIsA500WithoutDetails() {
        var response = new ProblemExceptionHandler(problems).unexpected(new IllegalStateException("secret"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().toString()).doesNotContain("secret");
    }

    @Test
    void anExceptionThatAClassifierKnowsIsItsAnswer() {
        var response = new ProblemExceptionHandler(problems, List.of(DATABASE))
                .unexpected(new IllegalStateException("no connection"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        Map<String, Object> body = response.getBody();
        assertThat(body)
                .containsEntry("type", "https://wallpaperhub.dev/problems/upstream-unavailable")
                .containsEntry("status", 503)
                .containsEntry("code", "upstream.unavailable");
        assertThat(body.get("detail").toString()).contains("database").doesNotContain("no connection");
    }

    @Test
    void aClassifierThatDoesNotKnowTheExceptionLeavesItA500() {
        var response = new ProblemExceptionHandler(problems, List.of(DATABASE))
                .unexpected(new IllegalArgumentException("a bug"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
    }
}
