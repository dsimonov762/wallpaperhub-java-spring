package dev.wallpaperhub.account.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.wallpaperhub.account.audit.AuditWriter;
import dev.wallpaperhub.account.outbox.OutboxWriter;
import dev.wallpaperhub.platform.problem.AppException;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Cache-aside against a real Redis and a real PostgreSQL: what is stored under which key and with which TTL, that a second read
 * does not reach the database, and that every change removes the entry. The service is the real one, the web layer is not there.
 */
@SpringBootTest(
        classes = UserCacheRedisTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            // application.yml takes this from the environment (OTEL_SERVICE_NAME), which a unit test does not have
            "spring.application.name=account-test",
            // the gRPC server of the starter starts in every context: a free port, so that contexts and a running
            // service do not collide
            "spring.grpc.server.port=0"
        })
@EnabledIf("dev.wallpaperhub.account.user.PostgresSupport#dockerAvailable")
class UserCacheRedisTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String KEY = "cache:user:" + ALICE;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackages = "dev.wallpaperhub.account.cache") // RedisCacheStore, RedisReadiness
    @Import({UserService.class, UserCache.class, OutboxWriter.class, AuditWriter.class})
    static class TestConfig {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @DynamicPropertySource
    static void containers(DynamicPropertyRegistry registry) {
        PostgresSupport.register(registry);
        RedisSupport.register(registry);
    }

    @Autowired
    private UserService service;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcClient jdbc;

    @AfterEach
    void clean() {
        redis.delete(KEY);
        jdbc.sql("delete from outbox_event").update();
        jdbc.sql("delete from audit_log").update();
        jdbc.sql("delete from users").update();
    }

    @BeforeEach
    void seed() {
        clean();
        jdbc.sql(
                        "insert into users (id, username, email, display_name) values (:id, 'alice', 'alice@example.com', 'Alice')")
                .param("id", ALICE)
                .update();
    }

    private void changeBehindTheCachesBack(String displayName) {
        jdbc.sql("update users set display_name = :name where id = :id")
                .param("name", displayName)
                .param("id", ALICE)
                .update();
    }

    @Test
    void theFirstReadFillsTheCacheUnderTheContractKeyWithAJitteredFiveMinuteTtl() {
        UserView view = service.view(ALICE);

        assertThat(view.user().displayName()).isEqualTo("Alice");
        assertThat(redis.opsForValue().get(KEY))
                .contains("\"username\":\"alice\"")
                .contains("\"version\":0");
        Long ttl = redis.getExpire(KEY, TimeUnit.SECONDS);
        assertThat(ttl).isBetween(238L, 361L);
    }

    @Test
    void theSecondReadComesFromRedisNotFromTheDatabase() {
        service.view(ALICE);
        changeBehindTheCachesBack("Changed in the database");

        assertThat(service.view(ALICE).user().displayName()).isEqualTo("Alice");
    }

    @Test
    void anUpdateRemovesTheEntryAndTheNextReadSeesTheChange() {
        service.view(ALICE);
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setDisplayName("Alice W.");

        service.updateProfile(ALICE, request, 0L);

        assertThat(redis.hasKey(KEY)).isFalse();
        UserView view = service.view(ALICE);
        assertThat(view.user().displayName()).isEqualTo("Alice W.");
        assertThat(view.version()).isEqualTo(1);
    }

    @Test
    void aDeleteRemovesTheEntryAndTheUserIsGone() {
        service.view(ALICE);

        service.delete(ALICE, ALICE, false);

        assertThat(redis.hasKey(KEY)).isFalse();
        assertThatThrownBy(() -> service.view(ALICE))
                .isInstanceOfSatisfying(
                        AppException.class, e -> assertThat(e.status()).isEqualTo(404));
        assertThat(redis.hasKey(KEY)).isFalse(); // a missing user is not cached
    }
}
