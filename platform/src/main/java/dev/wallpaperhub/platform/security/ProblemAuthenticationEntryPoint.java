package dev.wallpaperhub.platform.security;

import java.io.IOException;

import dev.wallpaperhub.platform.problem.ProblemWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/** A1 to A4: no token, or a token that does not validate: 401 problem+json with {@code WWW-Authenticate: Bearer}, never a redirect or a page. */
public final class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemWriter writer;

    public ProblemAuthenticationEntryPoint(ProblemWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        writer.write(request, response, 401, "unauthorized", null);
    }
}
