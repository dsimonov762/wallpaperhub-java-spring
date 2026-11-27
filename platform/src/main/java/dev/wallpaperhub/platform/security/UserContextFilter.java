package dev.wallpaperhub.platform.security;

import dev.wallpaperhub.platform.web.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** After authentication: the subject of the token goes into the log (MDC {@code user_id}) and into the request for the access-log line. */
public final class UserContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean known = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        if (known) {
            request.setAttribute(RequestContext.USER_ID, authentication.getName());
            MDC.put("user_id", authentication.getName());
        }
        try {
            chain.doFilter(request, response);
        } finally {
            if (known) {
                MDC.remove("user_id");
            }
        }
    }
}
