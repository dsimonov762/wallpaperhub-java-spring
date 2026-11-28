package dev.wallpaperhub.account.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wallpaperhub.account.DatabaseFailures;
import dev.wallpaperhub.platform.problem.AppException;
import io.grpc.Status;
import java.net.ConnectException;
import java.net.SocketException;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.CannotCreateTransactionException;

class GrpcErrorsTest {

    @Test
    void theExceptionModelMapsToTheGrpcCodesOfReadme53() {
        assertThat(GrpcErrors.codeOf(400)).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(GrpcErrors.codeOf(401)).isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(GrpcErrors.codeOf(403)).isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(GrpcErrors.codeOf(404)).isEqualTo(Status.Code.NOT_FOUND);
        assertThat(GrpcErrors.codeOf(409)).isEqualTo(Status.Code.ABORTED);
        assertThat(GrpcErrors.codeOf(429)).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
        assertThat(GrpcErrors.codeOf(503)).isEqualTo(Status.Code.UNAVAILABLE);
        assertThat(GrpcErrors.codeOf(500)).isEqualTo(Status.Code.INTERNAL);
    }

    @Test
    void anAppExceptionKeepsItsSlugAndCodeInTheDescription() {
        var status =
                GrpcErrors.toStatus(AppException.notFound("user.not-found")).getStatus();

        assertThat(status.getCode()).isEqualTo(Status.Code.NOT_FOUND);
        assertThat(status.getDescription()).contains("user.not-found");
    }

    @Test
    void aDatabaseThatCannotBeReachedIsUnavailableNotInternal() {
        assertThat(GrpcErrors.toStatus(new CannotCreateTransactionException("no connection"))
                        .getStatus()
                        .getCode())
                .isEqualTo(Status.Code.UNAVAILABLE);
        assertThat(DatabaseFailures.isUnavailable(
                        new IllegalStateException("wrapped", new SQLTransientConnectionException("pool timeout"))))
                .isTrue();
        assertThat(DatabaseFailures.isUnavailable(
                        new DataAccessResourceFailureException("down", new ConnectException())))
                .isTrue();
    }

    @Test
    void aLostOrRefusedConnectionOfTheDriverIsUnavailable() {
        // pgjdbc: SQLSTATE 08xxx for "connection attempt failed" and "I/O error while sending to the backend"
        SQLException refused = new SQLException("connection attempt failed", "08001");
        SQLException reset =
                new SQLException("An I/O error occurred", "08006", new SocketException("Connection reset"));

        assertThat(DatabaseFailures.isUnavailable(new RuntimeException("wrapped", refused)))
                .isTrue();
        assertThat(DatabaseFailures.isUnavailable(new RuntimeException(reset))).isTrue();
        assertThat(DatabaseFailures.isUnavailable(new SQLException("unique violation", "23505")))
                .isFalse();
        assertThat(DatabaseFailures.isUnavailable(new IllegalArgumentException("a bug")))
                .isFalse();
    }

    @Test
    void anythingElseIsInternalAndLeaksNothing() {
        var status = GrpcErrors.toStatus(new DuplicateKeyException("secret SQL detail"))
                .getStatus();

        assertThat(status.getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(status.getDescription()).doesNotContain("secret");
    }
}
