package dev.wallpaperhub.account;

import dev.wallpaperhub.platform.problem.AppException;
import dev.wallpaperhub.platform.web.Pagination;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stand-ins for three operations of account.yaml, only so that the conformance suite has something to talk to (a protected
 * path, a paginated list, a validated body). <b>WP-S1 replaces this class</b> with the real controllers; nothing here is the design.
 */
@RestController
@RequestMapping("/api/v1")
class StandInController {

    @GetMapping("/users/me")
    Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("id", jwt.getSubject());
    }

    @GetMapping("/users")
    Map<String, Object> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Pagination.requireValidSize(size);
        return Map.of("items", List.of(), "page", page, "size", size, "totalItems", 0, "totalPages", 0);
    }

    @PostMapping("/registrations")
    void register(@Valid @RequestBody RegistrationRequest request) {
        throw AppException.notImplemented();
    }
}
