package dev.wallpaperhub.account.user;

import dev.wallpaperhub.platform.problem.AppException;
import dev.wallpaperhub.platform.web.Pagination;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The user operations of contracts/openapi/account.yaml that need only this service's database. Registration, avatar and banner,
 * and the educational token endpoints follow in later increments of WP-S1.
 */
@RestController
@RequestMapping("/api/v1/users")
class UsersController {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final UserService service;

    UsersController(UserService service) {
        this.service = service;
    }

    @GetMapping("/me")
    ResponseEntity<UserResponse> me(Authentication authentication) {
        return ok(service.get(callerId(authentication)));
    }

    @PatchMapping("/me")
    ResponseEntity<UserResponse> updateMe(
            Authentication authentication,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody UpdateProfileRequest request) {
        if (request.isEmptyUpdate()) {
            throw new AppException(400, "validation-failed", null, Map.of());
        }
        Long expected = ETags.expectedVersion(ifMatch);
        return ok(service.updateProfile(callerId(authentication), request, expected));
    }

    @GetMapping("/{id}")
    PublicUserResponse get(@PathVariable UUID id) {
        return PublicUserResponse.of(service.get(id));
    }

    @GetMapping
    UserPageResponse list(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UserStatus status) {
        Pagination.requireValidSize(size);
        if (!isAdmin(authentication)) {
            throw AppException.forbidden(null);
        }
        if (page < 0 || size < 1 || (q != null && q.length() > 100)) {
            throw new AppException(400, "validation-failed", null, Map.of());
        }
        return UserPageResponse.of(service.list(q, status, page, size, UserSort.parse(sort)));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(Authentication authentication, @PathVariable UUID id) {
        service.delete(id, callerId(authentication), isAdmin(authentication));
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<UserResponse> ok(UserEntity user) {
        return ResponseEntity.ok().eTag(Long.toString(user.getVersion())).body(UserResponse.of(user));
    }

    /** The {@code sub} of the token is the user id (a user id is a UUID, SERVICE-SPEC section 4). */
    private static UUID callerId(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            throw AppException.notFound("user.not-found");
        }
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> ROLE_ADMIN.equals(a.getAuthority()));
    }
}
