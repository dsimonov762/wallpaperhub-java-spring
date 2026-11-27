package dev.wallpaperhub.account.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The payload of an event is the JSON form of its Avro record: the same field names as the schema in contracts/avro, nothing more. */
class UserEventsTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID EVENT = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    private static final UUID USER = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final Instant AT = Instant.parse("2026-01-02T03:04:05.678Z");

    private static List<String> fields(String schemaFile) throws IOException {
        // surefire runs in the module directory; contracts/ is in the repository root
        JsonNode schema = JSON.readTree(Files.readString(Path.of("..", "contracts", "avro", schemaFile)));
        List<String> names = new ArrayList<>();
        schema.get("fields").forEach(f -> names.add(f.get("name").asString()));
        return names;
    }

    @Test
    void profileUpdatedMatchesItsSchema() throws IOException {
        OutboxMessage message = UserEvents.profileUpdated(EVENT, USER, "alice", "Alice", null, AT);

        assertThat(message.payload().keySet()).containsExactlyInAnyOrderElementsOf(fields("account.user.profile-updated.v1.avsc"));
        assertThat(message.payload()).containsEntry("occurredAt", AT.toEpochMilli());
        assertThat(message.topic()).isEqualTo("account.user.profile-updated.v1");
        assertThat(message.aggregateId()).isEqualTo(USER.toString());
    }

    @Test
    void deletedMatchesItsSchemaAndCarriesNoPersonalData() throws IOException {
        OutboxMessage message = UserEvents.deleted(EVENT, USER, AT);

        assertThat(message.payload().keySet()).containsExactlyInAnyOrderElementsOf(fields("account.user.deleted.v1.avsc"));
        assertThat(message.topic()).isEqualTo("account.user.deleted.v1");
    }

    @Test
    void headersCarryTheEventId() {
        OutboxMessage message = UserEvents.deleted(EVENT, USER, AT);

        assertThat(OutboxWriter.headers(message)).containsEntry("event-id", EVENT.toString());
    }
}
