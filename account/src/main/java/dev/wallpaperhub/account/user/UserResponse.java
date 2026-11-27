package dev.wallpaperhub.account.user;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code User} of account.yaml: the caller's own profile, with the e-mail address. {@code avatarUrl} and {@code bannerUrl} are
 * {@code null} for now: the public URL of an image comes from media-service (the avatar increment of WP-S1).
 */
public record UserResponse(
        UUID id,
        String username,
        String email,
        String displayName,
        String bio,
        String locale,
        String avatarAssetId,
        String avatarUrl,
        String bannerAssetId,
        String bannerUrl,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    static UserResponse of(UserEntity user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getBio(),
                user.getLocale(),
                user.getAvatarAssetId(),
                null,
                user.getBannerAssetId(),
                null,
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
