package dev.wallpaperhub.account.user;

/** {@code UserStatus} of account.yaml. A deleted user is not a status: it has {@code deleted_at} and disappears from every view. */
public enum UserStatus {
    ACTIVE,
    SUSPENDED
}
