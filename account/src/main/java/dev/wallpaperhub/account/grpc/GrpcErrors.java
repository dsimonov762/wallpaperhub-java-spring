package dev.wallpaperhub.account.grpc;

import dev.wallpaperhub.account.DatabaseFailures;
import dev.wallpaperhub.platform.problem.AppException;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** README 5.3, the gRPC column: the exception model of the service, as gRPC status codes. Descriptions never carry details of the server. */
public final class GrpcErrors {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcErrors.class);

    private GrpcErrors() {}

    public static Status.Code codeOf(int httpStatus) {
        return switch (httpStatus) {
            case 400, 415 -> Status.Code.INVALID_ARGUMENT;
            case 401 -> Status.Code.UNAUTHENTICATED;
            case 403 -> Status.Code.PERMISSION_DENIED;
            case 404 -> Status.Code.NOT_FOUND;
            case 409 -> Status.Code.ABORTED;
            case 413, 429 -> Status.Code.RESOURCE_EXHAUSTED;
            case 503 -> Status.Code.UNAVAILABLE;
            default -> Status.Code.INTERNAL;
        };
    }

    public static StatusRuntimeException toStatus(Throwable failure) {
        if (failure instanceof StatusRuntimeException status) {
            return status;
        }
        if (failure instanceof StatusException status) {
            return status.getStatus().asRuntimeException(status.getTrailers());
        }
        if (failure instanceof AppException app) {
            String description = app.code() == null ? app.slug() : app.slug() + ": " + app.code();
            return codeOf(app.status()).toStatus().withDescription(description).asRuntimeException();
        }
        if (DatabaseFailures.isUnavailable(failure)) {
            LOG.warn("gRPC call failed, database unavailable: {}", failure.getClass().getSimpleName());
            return Status.UNAVAILABLE.withDescription("database unavailable").asRuntimeException();
        }
        LOG.error("gRPC call failed", failure);
        return Status.INTERNAL.withDescription("internal error").asRuntimeException();
    }

    /** Ends the call with the status of {@code failure}. */
    public static void fail(StreamObserver<?> observer, Throwable failure) {
        observer.onError(toStatus(failure));
    }
}
