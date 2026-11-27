package dev.wallpaperhub.account;

import dev.wallpaperhub.platform.problem.AppException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one operation of account.yaml that is still a stand-in: registration needs the Keycloak admin API (a later increment of
 * WP-S1). It stays, answering 501, because the conformance suite uses it as the validated-body endpoint (V checks).
 */
@RestController
@RequestMapping("/api/v1")
class StandInController {

    @PostMapping("/registrations")
    void register(@Valid @RequestBody RegistrationRequest request) {
        throw AppException.notImplemented();
    }
}
