package dev.wallpaperhub.account.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.wallpaperhub.account.audit.AuditWriter;
import dev.wallpaperhub.account.outbox.OutboxMessage;
import dev.wallpaperhub.account.outbox.OutboxWriter;
import dev.wallpaperhub.account.outbox.UserEvents;
import dev.wallpaperhub.platform.problem.AppException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;

/**
 * The write paths on a real PostgreSQL with the schema of contracts/db-schema: the entity is validated against it
 * ({@code ddl-auto=validate}), and the audit and outbox rows are read back as the relay will see them. No web layer, no security:
 * only the persistence beans of the service are in the context (the test configuration sits in this package, so that Spring Boot
 * finds the entities and repositories here).
 */
@SpringBootTest(
        classes = UserPersistenceTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            // application.yml takes this from the environment (OTEL_SERVICE_NAME), which a unit test does not have
            "spring.application.name=account-test"
        })
@EnabledIf("dev.wallpaperhub.account.user.PostgresSupport#dockerAvailable")
class UserPersistenceTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID CAROL = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final UUID DAVE = UUID.fromString("00000000-0000-4000-8000-000000000004");

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({UserService.class, OutboxWriter.class, AuditWriter.class})
    static class TestConfig {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresSupport.register(registry);
    }

    @Autowired
    private UserService service;

    @Autowired
    private OutboxWriter outbox;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void seed() {
        clean();
        insert(ALICE, "alice", "ACTIVE", false);
        insert(BOB, "bob", "ACTIVE", false);
        insert(CAROL, "carol", "ACTIVE", true);
        insert(DAVE, "dave_x", "SUSPENDED", false);
    }

    @AfterEach
    void clean() {
        jdbc.sql("drop trigger if exists fail_outbox on outbox_event").update();
        jdbc.sql("delete from outbox_event").update();
        jdbc.sql("delete from audit_log").update();
        jdbc.sql("delete from users").update();
    }

    private void insert(UUID id, String username, String status, boolean deleted) {
        jdbc.sql("""
                        insert into users (id, username, email, display_name, status, deleted_at)
                        values (:id, :username, :email, :displayName, :status, case when :deleted then now() end)
                        """)
                .param("id", id)
                .param("username", username)
                .param("email", username + "@example.com")
                .param("displayName", username.substring(0, 1).toUpperCase() + username.substring(1))
                .param("status", status)
                .param("deleted", deleted)
                .update();
    }

    private static UpdateProfileRequest displayName(String value) {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setDisplayName(value);
        return request;
    }

    private long count(String table) {
        return jdbc.sql("select count(*) from " + table).query(Long.class).single();
    }

    @Test
    void theEntityMatchesTheContractSchema() {
        // the context only starts when Hibernate's validation of the schema passes; reading a row proves the column
        // types
        UserEntity alice = users.findById(ALICE).orElseThrow();
        assertThat(alice.getUsername()).isEqualTo("alice");
        assertThat(alice.getVersion()).isZero();
        assertThat(alice.getLocale()).isEqualTo("en");
    }

    @Test
    void updateChangesTheRowTheAuditLogAndTheOutboxTogether() {
        UserEntity updated = service.updateProfile(ALICE, displayName("Alice W."), 0L);

        assertThat(updated.getVersion()).isEqualTo(1);
        Map<String, Object> row = jdbc.sql("select version, display_name from users where id = :id")
                .param("id", ALICE)
                .query()
                .singleRow();
        assertThat(row).containsEntry("version", 1L).containsEntry("display_name", "Alice W.");

        Map<String, Object> audit = jdbc.sql("""
                        select action, cast(actor_id as text) as actor, entity_id,
                               before ->> 'displayName' as old_name, after ->> 'displayName' as new_name
                        from audit_log
                        """).query().singleRow();
        assertThat(audit)
                .containsEntry("action", "PROFILE_UPDATED")
                .containsEntry("actor", ALICE.toString())
                .containsEntry("entity_id", ALICE.toString())
                .containsEntry("old_name", "Alice")
                .containsEntry("new_name", "Alice W.");

        Map<String, Object> event = jdbc.sql("""
                        select topic, event_type, aggregate_id, cast(event_id as text) as event_id,
                               payload ->> 'eventId' as payload_event_id, payload ->> 'displayName' as display_name,
                               payload ->> 'username' as username, headers ->> 'event-id' as header_event_id,
                               published_at is null as unpublished
                        from outbox_event
                        """).query().singleRow();
        assertThat(event)
                .containsEntry("topic", "account.user.profile-updated.v1")
                .containsEntry("event_type", "UserProfileUpdated")
                .containsEntry("aggregate_id", ALICE.toString())
                .containsEntry("display_name", "Alice W.")
                .containsEntry("username", "alice")
                .containsEntry("unpublished", true);
        assertThat(event.get("payload_event_id")).isEqualTo(event.get("event_id"));
        assertThat(event.get("header_event_id")).isEqualTo(event.get("event_id"));
    }

    @Test
    void aStaleVersionChangesNothing() {
        assertThatThrownBy(() -> service.updateProfile(ALICE, displayName("Other"), 5L))
                .isInstanceOfSatisfying(
                        AppException.class, e -> assertThat(e.status()).isEqualTo(409));

        assertThat(users.findById(ALICE).orElseThrow().getDisplayName()).isEqualTo("Alice");
        assertThat(count("audit_log")).isZero();
        assertThat(count("outbox_event")).isZero();
    }

    @Test
    void aChangeThatChangesNothingKeepsTheVersionAndWritesNothing() {
        UserEntity same = service.updateProfile(ALICE, displayName("Alice"), null);

        assertThat(same.getVersion()).isZero();
        assertThat(count("audit_log")).isZero();
        assertThat(count("outbox_event")).isZero();
    }

    @Test
    void ifTheOutboxInsertFailsTheProfileChangeIsRolledBack() {
        // fault injection, in the throw-away database of the test only: an insert into outbox_event fails
        jdbc.sql("""
                        create or replace function fail_outbox() returns trigger language plpgsql
                        as $$ begin raise exception 'outbox is down'; end $$
                        """).update();
        jdbc.sql("create trigger fail_outbox before insert on outbox_event for each row execute function fail_outbox()")
                .update();

        assertThatThrownBy(() -> service.updateProfile(ALICE, displayName("Alice W."), null))
                .isInstanceOf(DataAccessException.class);

        Map<String, Object> row = jdbc.sql("select version, display_name from users where id = :id")
                .param("id", ALICE)
                .query()
                .singleRow();
        assertThat(row).containsEntry("version", 0L).containsEntry("display_name", "Alice");
        assertThat(count("audit_log")).isZero();
    }

    @Test
    void deleteIsSoftWritesAuditWithoutBeforeAndAfterAndAnEventWithoutPersonalData() {
        service.delete(ALICE, ALICE, false);

        assertThat(jdbc.sql("select deleted_at is not null from users where id = :id")
                        .param("id", ALICE)
                        .query(Boolean.class)
                        .single())
                .isTrue();
        Map<String, Object> audit = jdbc.sql(
                        "select action, before is null as no_before, after is null as no_after from audit_log")
                .query()
                .singleRow();
        assertThat(audit)
                .containsEntry("action", "USER_DELETED")
                .containsEntry("no_before", true)
                .containsEntry("no_after", true);
        Map<String, Object> event = jdbc.sql("select topic, cast(payload as text) as payload from outbox_event")
                .query()
                .singleRow();
        assertThat(event).containsEntry("topic", "account.user.deleted.v1");
        assertThat((String) event.get("payload")).doesNotContain("alice").doesNotContain("example.com");

        assertThatThrownBy(() -> service.get(ALICE))
                .isInstanceOfSatisfying(
                        AppException.class, e -> assertThat(e.status()).isEqualTo(404));
    }

    @Test
    void someoneElseCannotDeleteYouButAnAdminCan() {
        assertThatThrownBy(() -> service.delete(ALICE, BOB, false))
                .isInstanceOfSatisfying(
                        AppException.class, e -> assertThat(e.status()).isEqualTo(403));
        assertThat(users.findById(ALICE).orElseThrow().isDeleted()).isFalse();

        service.delete(ALICE, BOB, true);
        assertThat(users.findById(ALICE).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    void listHidesDeletedUsersFiltersAndPages() {
        Page<UserEntity> all = service.list(null, null, 0, 10, UserSort.parse("username,asc"));
        assertThat(all.map(UserEntity::getUsername).toList()).containsExactly("alice", "bob", "dave_x");

        assertThat(service.list("BO", null, 0, 10, UserSort.parse(null))
                        .map(UserEntity::getUsername)
                        .toList())
                .containsExactly("bob");
        assertThat(service.list("example.com", null, 0, 10, UserSort.parse("username,desc"))
                        .map(UserEntity::getUsername)
                        .toList())
                .containsExactly("dave_x", "bob", "alice");
        assertThat(service.list(null, UserStatus.SUSPENDED, 0, 10, UserSort.parse(null))
                        .map(UserEntity::getUsername)
                        .toList())
                .containsExactly("dave_x");

        Page<UserEntity> firstPage = service.list(null, null, 0, 2, UserSort.parse("username,asc"));
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
    }

    @Test
    void likeWildcardsInTheSearchTextAreLiteral() {
        assertThat(service.list("%", null, 0, 10, UserSort.parse(null)).getTotalElements())
                .isZero();
        assertThat(service.list("_", null, 0, 10, UserSort.parse(null))
                        .map(UserEntity::getUsername)
                        .toList())
                .containsExactly("dave_x");
    }

    @Test
    void theOutboxRefusesToWriteOutsideATransaction() {
        // this test is not transactional, so there is no transaction here
        OutboxMessage message = UserEvents.deleted(UUID.randomUUID(), ALICE, Instant.now());

        assertThatThrownBy(() -> outbox.add(message)).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(count("outbox_event")).isZero();
    }
}
