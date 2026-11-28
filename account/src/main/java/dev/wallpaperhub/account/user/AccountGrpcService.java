package dev.wallpaperhub.account.user;

import com.google.protobuf.Timestamp;
import dev.wallpaperhub.account.grpc.GrpcErrors;
import dev.wallpaperhub.account.v1.AccountServiceGrpc;
import dev.wallpaperhub.account.v1.GetUserRequest;
import dev.wallpaperhub.account.v1.GetUsersByIdsRequest;
import dev.wallpaperhub.account.v1.GetUsersByIdsResponse;
import dev.wallpaperhub.account.v1.UpdatedSinceRequest;
import dev.wallpaperhub.account.v1.User;
import dev.wallpaperhub.platform.problem.AppException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The {@code AccountService} of contracts/proto/account/v1, for the other services (catalog, notification). A deleted user does not
 * exist for any call. Authentication is not here: {@code UserGrpcConfiguration} puts {@code InternalCallInterceptor} in front.
 */
final class AccountGrpcService extends AccountServiceGrpc.AccountServiceImplBase {

    static final int MAX_IDS = 100;
    static final int STREAM_CHUNK = 500;

    private final UserService users;

    AccountGrpcService(UserService users) {
        this.users = users;
    }

    @Override
    public void getUser(GetUserRequest request, StreamObserver<User> out) {
        try {
            out.onNext(toProto(users.view(parse(request.getId())).user()));
            out.onCompleted();
        } catch (RuntimeException e) {
            GrpcErrors.fail(out, e);
        }
    }

    @Override
    public void getUsersByIds(GetUsersByIdsRequest request, StreamObserver<GetUsersByIdsResponse> out) {
        try {
            if (request.getIdsCount() > MAX_IDS) {
                throw AppException.validation("grpc.too-many-ids", "max", MAX_IDS);
            }
            Set<UUID> ids = new LinkedHashSet<>();
            for (String id : request.getIdsList()) {
                ids.add(parse(id));
            }
            GetUsersByIdsResponse.Builder response = GetUsersByIdsResponse.newBuilder();
            for (UserEntity user : users.byIds(ids)) {
                response.addUsers(toProto(UserResponse.of(user)));
            }
            out.onNext(response.build());
            out.onCompleted();
        } catch (RuntimeException e) {
            GrpcErrors.fail(out, e);
        }
    }

    /**
     * Streams in chunks of {@link #STREAM_CHUNK} rows and waits while the client cannot take more (flow control), so a slow resync job
     * does not make the server buffer the table. Stops when the client cancels.
     */
    @Override
    public void listUsersUpdatedSince(UpdatedSinceRequest request, StreamObserver<User> out) {
        try {
            Instant since = since(request);
            ServerCallStreamObserver<User> call = out instanceof ServerCallStreamObserver<User> s ? s : null;
            users.updatedSince(since, STREAM_CHUNK, user -> {
                if (call != null && !awaitReady(call)) {
                    return false;
                }
                out.onNext(toProto(UserResponse.of(user)));
                return true;
            });
            if (call == null || !call.isCancelled()) {
                out.onCompleted();
            }
        } catch (RuntimeException e) {
            GrpcErrors.fail(out, e);
        }
    }

    private static Instant since(UpdatedSinceRequest request) {
        if (!request.hasSince()) {
            return Instant.EPOCH; // no lower bound: everybody
        }
        Timestamp timestamp = request.getSince();
        try {
            return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
        } catch (DateTimeException e) {
            throw AppException.validation("grpc.invalid-timestamp", "field", "since");
        }
    }

    /** True when the next message may be sent; false when the call is cancelled or the thread is interrupted. */
    private static boolean awaitReady(ServerCallStreamObserver<User> call) {
        try {
            while (!call.isReady()) {
                if (call.isCancelled()) {
                    return false;
                }
                Thread.sleep(5);
            }
            return !call.isCancelled();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw AppException.validation("grpc.invalid-id", "id", id.length() > 40 ? id.substring(0, 40) : id);
        }
    }

    /** {@code avatar_url} is an empty string without an avatar (proto3 has no null). */
    static User toProto(UserResponse user) {
        return User.newBuilder()
                .setId(user.id().toString())
                .setUsername(user.username())
                .setDisplayName(user.displayName())
                .setEmail(user.email())
                .setLocale(user.locale())
                .setAvatarUrl(user.avatarUrl() == null ? "" : user.avatarUrl())
                .build();
    }
}
