package dev.wallpaperhub.platform.security;

import dev.wallpaperhub.platform.problem.ProblemWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/** An authenticated caller who may not do this: 403 problem+json. */
public final class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemWriter writer;

    public ProblemAccessDeniedHandler(ProblemWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        writer.write(request, response, 403, "forbidden", null);
    }
}
