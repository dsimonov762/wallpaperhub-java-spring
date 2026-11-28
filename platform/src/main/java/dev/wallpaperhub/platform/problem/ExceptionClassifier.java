package dev.wallpaperhub.platform.problem;

/**
 * Lets a service say which of its unexpected exceptions are not bugs. The platform does not know a service's database or client
 * libraries; a service that does registers a bean of this type, and {@link ProblemExceptionHandler} asks every one before it falls
 * back to {@code 500}. The answer is the {@link AppException} to send, or {@code null} when the failure is not known.
 */
@FunctionalInterface
public interface ExceptionClassifier {

    AppException classify(Throwable failure);
}
