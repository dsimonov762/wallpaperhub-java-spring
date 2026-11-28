package dev.wallpaperhub.account;

import dev.wallpaperhub.platform.problem.AppException;
import dev.wallpaperhub.platform.problem.ExceptionClassifier;
import org.springframework.stereotype.Component;

/** A database that cannot be reached is {@code 503 upstream.unavailable} on HTTP (chaos scenario postgres-down), not a 500. */
@Component
class DatabaseExceptionClassifier implements ExceptionClassifier {

    @Override
    public AppException classify(Throwable failure) {
        return DatabaseFailures.isUnavailable(failure) ? AppException.dependencyDown("database") : null;
    }
}
