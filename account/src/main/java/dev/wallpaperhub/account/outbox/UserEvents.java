package dev.wallpaperhub.account.outbox;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The events of account-service about a user, as outbox messages. The field names are those of {@code contracts/avro/account.user.*.avsc}
 * (a test compares them); {@code occurredAt} is the epoch milliseconds of the Avro {@code timestamp-millis}.
 */
public final class UserEvents {

    public static final String PROFILE_UPDATED_TOPIC = "account.user.profile-updated.v1";
    public static final String DELETED_TOPIC = "account.user.deleted.v1";

    private UserEvents() {}

    /** A full snapshot of the author-facing fields, not a delta. {@code avatarUrl} stays null until the avatar increment. */
    public static OutboxMessage profileUpdated(
            UUID eventId, UUID userId, String username, String displayName, String avatarUrl, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId.toString());
        payload.put("userId", userId.toString());
        payload.put("username", username);
        payload.put("displayName", displayName);
        payload.put("avatarUrl", avatarUrl);
        payload.put("occurredAt", occurredAt.toEpochMilli());
        return new OutboxMessage(eventId, "UserProfileUpdated", PROFILE_UPDATED_TOPIC, userId.toString(), payload);
    }

    /** Without personal data, on purpose: consumers only need the id to clean up. */
    public static OutboxMessage deleted(UUID eventId, UUID userId, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId.toString());
        payload.put("userId", userId.toString());
        payload.put("occurredAt", occurredAt.toEpochMilli());
        return new OutboxMessage(eventId, "UserDeleted", DELETED_TOPIC, userId.toString(), payload);
    }
}
