package dev.wallpaperhub.account.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.Timestamp;
import dev.wallpaperhub.account.v1.AccountServiceGrpc;
import dev.wallpaperhub.account.v1.GetUserRequest;
import dev.wallpaperhub.account.v1.GetUsersByIdsRequest;
import dev.wallpaperhub.account.v1.UpdatedSinceRequest;
import dev.wallpaperhub.account.v1.User;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The AccountService on a real PostgreSQL (in-process transport, so no port; the check of the token is InternalCallInterceptorTest). */
@SpringBootTest(
        classes = UserPersistenceTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            // application.yml takes this from the environment (OTEL_SERVICE_NAME), which a unit test does not have
            "spring.application.name=account-test",
            // the gRPC server of the starter starts in every context: a free port, so that contexts and a running
            // service do not collide
            "spring.grpc.server.port=0",
            // REDIS_HOST and REDIS_PORT are not set either; nothing connects, the cache of this test is a no-op
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=6379"
        })
@EnabledIf("dev.wallpaperhub.account.user.PostgresSupport#dockerAvailable")
class AccountGrpcServiceTest {

    private static final Instant T1 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-01T11:00:00.123456Z");
    private static final Instant T3 = Instant.parse("2026-01-01T12:00:00Z");

    private static final UUID U1 = id(1);
    private static final UUID U2A = id(2);
    private static final UUID U2B = id(3);
    private static final UUID U3 = id(4);
    private static final UUID DELETED = id(5);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresSupport.register(registry);
    }

    @Autowired
    private UserService service;

    @Autowired
    private JdbcClient jdbc;

    private Server server;
    private ManagedChannel channel;
    private AccountServiceGrpc.AccountServiceBlockingStub stub;

    private static UUID id(int n) {
        return UUID.fromString("00000000-0000-4000-8000-0000000000%02d".formatted(n));
    }

    @BeforeEach
    void start() throws Exception {
        clean();
        insert(U1, "u1a", T1, false);
        insert(U2A, "u2a", T2, false);
        insert(U2B, "u2b", T2, false); // the same timestamp as u2a: the order falls back to the id
        insert(U3, "u3a", T3, false);
        insert(DELETED, "gone", T2, true);

        String name = UUID.randomUUID().toString();
        server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(new AccountGrpcService(service))
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        stub = AccountServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void clean() {
        if (channel != null) {
            channel.shutdownNow();
            server.shutdownNow();
        }
        jdbc.sql("delete from outbox_event").update();
        jdbc.sql("delete from audit_log").update();
        jdbc.sql("delete from users").update();
    }

    private void insert(UUID id, String username, Instant updatedAt, boolean deleted) {
        jdbc.sql("""
                        insert into users (id, username, email, display_name, locale, updated_at, deleted_at)
                        values (:id, :username, :email, :displayName, 'de', :updatedAt, case when :deleted then now() end)
                        """)
                .param("id", id)
                .param("username", username)
                .param("email", username + "@example.com")
                .param("displayName", "Name of " + username)
                .param("updatedAt", java.sql.Timestamp.from(updatedAt))
                .param("deleted", deleted)
                .update();
    }

    private static Status.Code codeOf(Runnable call) {
        try {
            call.run();
            return Status.Code.OK;
        } catch (StatusRuntimeException e) {
            return e.getStatus().getCode();
        }
    }

    private static List<String> usernames(Iterator<User> users) {
        List<String> names = new ArrayList<>();
        users.forEachRemaining(user -> names.add(user.getUsername()));
        return names;
    }

    @Test
    void getUserReturnsTheContractFieldsWithAnEmptyAvatarUrl() {
        User user =
                stub.getUser(GetUserRequest.newBuilder().setId(U1.toString()).build());

        assertThat(user.getId()).isEqualTo(U1.toString());
        assertThat(user.getUsername()).isEqualTo("u1a");
        assertThat(user.getDisplayName()).isEqualTo("Name of u1a");
        assertThat(user.getEmail()).isEqualTo("u1a@example.com");
        assertThat(user.getLocale()).isEqualTo("de");
        assertThat(user.getAvatarUrl()).isEmpty();
    }

    @Test
    void getUserOfAnUnknownOrDeletedUserIsNotFoundAndOfABadIdInvalidArgument() {
        assertThat(codeOf(() -> stub.getUser(
                        GetUserRequest.newBuilder().setId(id(99).toString()).build())))
                .isEqualTo(Status.Code.NOT_FOUND);
        assertThat(codeOf(() -> stub.getUser(
                        GetUserRequest.newBuilder().setId(DELETED.toString()).build())))
                .isEqualTo(Status.Code.NOT_FOUND);
        assertThat(codeOf(() -> stub.getUser(
                        GetUserRequest.newBuilder().setId("not-a-uuid").build())))
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(codeOf(() -> stub.getUser(GetUserRequest.getDefaultInstance())))
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void getUsersByIdsOmitsUnknownDeletedAndDuplicateIds() {
        var response = stub.getUsersByIds(GetUsersByIdsRequest.newBuilder()
                .addIds(U1.toString())
                .addIds(U3.toString())
                .addIds(id(99).toString())
                .addIds(DELETED.toString())
                .addIds(U1.toString())
                .build());

        assertThat(response.getUsersList()).extracting(User::getUsername).containsExactlyInAnyOrder("u1a", "u3a");
    }

    @Test
    void getUsersByIdsRefusesMoreThanAHundredIdsAndBadIds() {
        GetUsersByIdsRequest.Builder tooMany = GetUsersByIdsRequest.newBuilder();
        IntStream.range(0, 101).forEach(i -> tooMany.addIds(UUID.randomUUID().toString()));
        GetUsersByIdsRequest.Builder exactly = GetUsersByIdsRequest.newBuilder();
        IntStream.range(0, 100).forEach(i -> exactly.addIds(UUID.randomUUID().toString()));

        assertThat(codeOf(() -> stub.getUsersByIds(tooMany.build()))).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(codeOf(() -> stub.getUsersByIds(exactly.build()))).isEqualTo(Status.Code.OK);
        assertThat(codeOf(() -> stub.getUsersByIds(
                        GetUsersByIdsRequest.newBuilder().addIds("nope").build())))
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(stub.getUsersByIds(GetUsersByIdsRequest.getDefaultInstance()).getUsersList())
                .isEmpty();
    }

    @Test
    void theStreamIsOldestFirstFromSinceOnwardsAndWithoutDeletedUsers() {
        Timestamp since = Timestamp.newBuilder()
                .setSeconds(T2.getEpochSecond())
                .setNanos(T2.getNano())
                .build();

        assertThat(usernames(stub.listUsersUpdatedSince(
                        UpdatedSinceRequest.newBuilder().setSince(since).build())))
                .containsExactly("u2a", "u2b", "u3a");
        assertThat(usernames(stub.listUsersUpdatedSince(UpdatedSinceRequest.getDefaultInstance())))
                .containsExactly("u1a", "u2a", "u2b", "u3a");
    }

    @Test
    void chunkedPagingNeitherSkipsNorRepeatsRowsWithTheSameTimestamp() {
        List<String> seen = new ArrayList<>();

        service.updatedSince(Instant.EPOCH, 2, user -> seen.add(user.getUsername()));

        assertThat(seen).containsExactly("u1a", "u2a", "u2b", "u3a");
    }

    @Test
    void theSinkCanStopTheStream() {
        List<String> seen = new ArrayList<>();

        service.updatedSince(Instant.EPOCH, 2, user -> {
            seen.add(user.getUsername());
            return seen.size() < 3;
        });

        assertThat(seen).containsExactly("u1a", "u2a", "u2b");
    }

    @Test
    void aTimestampOutOfRangeIsInvalidArgument() {
        UpdatedSinceRequest request = UpdatedSinceRequest.newBuilder()
                .setSince(Timestamp.newBuilder().setSeconds(Long.MAX_VALUE).build())
                .build();

        assertThatThrownBy(() -> stub.listUsersUpdatedSince(request).hasNext())
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
    }
}
