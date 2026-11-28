package dev.wallpaperhub.account.user;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wallpaperhub.account.audit.AuditWriter;
import dev.wallpaperhub.account.outbox.OutboxWriter;
import java.time.Clock;
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
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Redis does not answer (nothing listens on its port): reads and writes keep working from the database (chaos scenario redis-down). */
@SpringBootTest(
        classes = UserCacheRedisDownTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.application.name=account-test",
            // the gRPC server of the starter starts in every context: a free port, so that contexts and a running service do not collide
            "spring.grpc.server.port=0",
            "spring.data.redis.host=127.0.0.1",
            "spring.data.redis.port=1",
            "spring.data.redis.timeout=200ms",
            "spring.data.redis.connect-timeout=200ms"
        })
@EnabledIf("dev.wallpaperhub.account.user.PostgresSupport#dockerAvailable")
class UserCacheRedisDownTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackages = "dev.wallpaperhub.account.cache")
    @Import({UserService.class, UserCache.class, OutboxWriter.class, AuditWriter.class})
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
    private JdbcClient jdbc;

    @BeforeEach
    void seed() {
        clean();
        jdbc.sql(
                        "insert into users (id, username, email, display_name) values (:id, 'alice', 'alice@example.com', 'Alice')")
                .param("id", ALICE)
                .update();
    }

    @AfterEach
    void clean() {
        jdbc.sql("delete from outbox_event").update();
        jdbc.sql("delete from audit_log").update();
        jdbc.sql("delete from users").update();
    }

    @Test
    void readsAndWritesWorkWithoutRedis() {
        assertThat(service.view(ALICE).user().displayName()).isEqualTo("Alice");

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setDisplayName("Alice W.");
        service.updateProfile(ALICE, request, 0L);

        assertThat(service.view(ALICE).user().displayName()).isEqualTo("Alice W.");
    }
}
