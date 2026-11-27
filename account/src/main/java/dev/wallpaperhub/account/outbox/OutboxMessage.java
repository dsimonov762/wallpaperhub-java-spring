package dev.wallpaperhub.account.outbox;

import java.util.Map;
import java.util.UUID;

/** One event for {@code outbox_event}: the payload is the JSON form of the Avro record of {@code topic} (ADR 0003). */
public record OutboxMessage(
        UUID eventId, String eventType, String topic, String aggregateId, Map<String, Object> payload) {}
