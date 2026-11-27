package dev.wallpaperhub.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The body of {@code POST /api/v1/registrations} (RegistrationRequest in contracts/openapi/account.yaml). WP-S1 generates this from the contract. */
public record RegistrationRequest(
        @NotBlank @Size(min = 3, max = 32) String username,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(min = 1, max = 64) String displayName,
        String locale) {}
