package dev.wallpaperhub.account.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.wallpaperhub.account.v1.AccountServiceGrpc;
import dev.wallpaperhub.account.v1.GetUserRequest;
import dev.wallpaperhub.account.v1.User;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/** The check of the token in front of the service, with a decoder that knows a few fake tokens (the real one is the platform's, tested there). */
class InternalCallInterceptorTest {

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private Server server;
    private ManagedChannel channel;

    private static Jwt token(String value, String... roles) {
        return Jwt.withTokenValue(value)
                .header("alg", "none")
                .claim("sub", "someone")
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .build();
    }

    @BeforeEach
    void start() throws Exception {
        JwtDecoder decoder = value -> switch (value) {
            case "service" -> token(value, "internal");
            case "user" -> token(value, "USER");
            case "bad" -> throw new BadJwtException("expired");
            case "down" -> throw new JwtException("Couldn't retrieve remote JWK set");
            default -> throw new BadJwtException("unknown");
        };
        AccountServiceGrpc.AccountServiceImplBase fake = new AccountServiceGrpc.AccountServiceImplBase() {
            @Override
            public void getUser(GetUserRequest request, StreamObserver<User> out) {
                out.onNext(User.newBuilder().setId(request.getId()).build());
                out.onCompleted();
            }
        };
        String name = UUID.randomUUID().toString();
        server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(ServerInterceptors.intercept(fake.bindService(), new InternalCallInterceptor(decoder)))
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    @AfterEach
    void stop() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    private AccountServiceGrpc.AccountServiceBlockingStub stub(String authorization) {
        AccountServiceGrpc.AccountServiceBlockingStub stub = AccountServiceGrpc.newBlockingStub(channel);
        if (authorization == null) {
            return stub;
        }
        Metadata headers = new Metadata();
        headers.put(AUTHORIZATION, authorization);
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    private Status.Code call(String authorization) {
        try {
            stub(authorization).getUser(GetUserRequest.newBuilder().setId("x").build());
            return Status.Code.OK;
        } catch (StatusRuntimeException e) {
            return e.getStatus().getCode();
        }
    }

    @Test
    void aServiceAccountTokenWithTheInternalRoleIsLetThrough() {
        assertThat(call("Bearer service")).isEqualTo(Status.Code.OK);
        assertThat(call("bearer service")).isEqualTo(Status.Code.OK);
    }

    @Test
    void noTokenOrAnotherSchemeIsUnauthenticated() {
        assertThat(call(null)).isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(call("Basic dXNlcjpwdw==")).isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(call("Bearer")).isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void aTokenThatIsNotValidIsUnauthenticated() {
        assertThat(call("Bearer bad")).isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(call("Bearer nonsense")).isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void aValidTokenWithoutTheInternalRoleIsPermissionDenied() {
        assertThat(call("Bearer user")).isEqualTo(Status.Code.PERMISSION_DENIED);
    }

    @Test
    void whenTheSigningKeysCannotBeFetchedTheServiceIsUnavailable() {
        assertThat(call("Bearer down")).isEqualTo(Status.Code.UNAVAILABLE);
    }

    @Test
    void theTokenIsNeverEchoedInTheStatus() {
        assertThatThrownBy(() -> stub("Bearer secret-token-value")
                        .getUser(GetUserRequest.newBuilder().setId("x").build()))
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        e -> assertThat(String.valueOf(e.getStatus().getDescription()))
                                .doesNotContain("secret-token-value"));
    }
}
