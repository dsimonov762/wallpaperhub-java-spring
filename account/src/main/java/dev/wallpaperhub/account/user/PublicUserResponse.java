package dev.wallpaperhub.account.user;

import java.time.Instant;
import java.util.UUID;

/** {@code PublicUser} of account.yaml: what anybody may see. Never the e-mail address, the locale or the status. */
public record PublicUserResponse(
        UUID id, String username, String displayName, String bio, String avatarUrl, String bannerUrl, Instant createdAt) {

    static PublicUserResponse of(UserEntity user) {
        return new PublicUserResponse(
                user.getId(), user.getUsername(), user.getDisplayName(), user.getBio(), null, null, user.getCreatedAt());
    }
}
