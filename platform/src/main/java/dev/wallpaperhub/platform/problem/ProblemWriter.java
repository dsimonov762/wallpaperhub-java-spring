package dev.wallpaperhub.platform.problem;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import dev.wallpaperhub.platform.web.Language;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** Writes a problem+json response from code that runs outside Spring MVC (the security filters). */
public final class ProblemWriter {

    private final Problems problems;
    private final JsonMapper mapper;

    public ProblemWriter(Problems problems, JsonMapper mapper) {
        this.problems = problems;
        this.mapper = mapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, int status, String slug, String code)
            throws IOException {
        String lang = Language.of(request.getHeader("Accept-Language"));
        Map<String, Object> body = problems.body(request, status, slug, code, Map.of(), List.of(), lang);
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Language", lang);
        response.getOutputStream().write(mapper.writeValueAsBytes(body));
    }
}
