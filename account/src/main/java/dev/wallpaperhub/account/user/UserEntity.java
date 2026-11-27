package dev.wallpaperhub.account.user;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A row of {@code users} (contracts/db-schema/postgres/account/V1__init.sql; the schema is never created from this class,
 * Hibernate only validates it). The id is the Keycloak {@code sub} and is never generated here. {@code version} is the
 * optimistic lock and the ETag; {@code updatedAt} is maintained by the application, not by a trigger.
 */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private UUID id;

    private String username;
    private String email;
    private String displayName;
    private String bio;
    private String locale;
    private String avatarAssetId;
    private String bannerAssetId;
    private String status;
    private Instant deletedAt;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private long version;

    /** For JPA. */
    protected UserEntity() {}

    /** A new, active user (registration, WP-S1 increment 4). The e-mail address is stored in lower case. */
    public static UserEntity create(
            UUID id, String username, String email, String displayName, String locale, Instant now) {
        UserEntity user = new UserEntity();
        user.id = id;
        user.username = username;
        user.email = email.toLowerCase(java.util.Locale.ROOT);
        user.displayName = displayName;
        user.locale = locale;
        user.status = UserStatus.ACTIVE.name();
        user.createdAt = now;
        user.updatedAt = now;
        return user;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBio() {
        return bio;
    }

    public String getLocale() {
        return locale;
    }

    public String getAvatarAssetId() {
        return avatarAssetId;
    }

    public String getBannerAssetId() {
        return bannerAssetId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void changeDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void changeBio(String bio) {
        this.bio = bio;
    }

    public void changeLocale(String locale) {
        this.locale = locale;
    }

    public void markDeleted(Instant now) {
        this.deletedAt = now;
    }

    /** Sets {@code updated_at}; call it with every change. */
    public void touch(Instant now) {
        this.updatedAt = now;
    }
}
