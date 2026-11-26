package dev.wallpaperhub.platform.problem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.wallpaperhub.platform.i18n.Messages;
import dev.wallpaperhub.platform.web.Language;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Every error that leaves a controller becomes problem+json in the caller's language (E1 to E5). The mapping of exceptions to problem types is README 5.3. */
@RestControllerAdvice
public class ProblemExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ProblemExceptionHandler.class);

    private final Problems problems;

    public ProblemExceptionHandler(Problems problems) {
        this.problems = problems;
    }

    @ExceptionHandler(AppException.class)
    ResponseEntity<Map<String, Object>> app(AppException e, HttpServletRequest request) {
        return respond(request, e.status(), e.slug(), e.code(), e.args(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalidBody(MethodArgumentNotValidException e, HttpServletRequest request) {
        String lang = Language.of(request.getHeader("Accept-Language"));
        List<Problems.FieldIssue> issues = new ArrayList<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            ConstraintViolation<?> violation = error.contains(ConstraintViolation.class)
                    ? error.unwrap(ConstraintViolation.class) : null;
            issues.add(issue(lang, error.getField(), error.getCode(), violation));
        }
        return respond(request, 400, "validation-failed", null, Map.of(), issues);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Map<String, Object>> invalidParameters(ConstraintViolationException e, HttpServletRequest request) {
        String lang = Language.of(request.getHeader("Accept-Language"));
        List<Problems.FieldIssue> issues = new ArrayList<>();
        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
            String code = violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
            issues.add(issue(lang, violation.getPropertyPath().toString(), code, violation));
        }
        return respond(request, 400, "validation-failed", null, Map.of(), issues);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, Object>> malformed(Exception e, HttpServletRequest request) {
        return respond(request, 400, "validation-failed", null, Map.of(), List.of());
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<Map<String, Object>> noSuchPath(Exception e, HttpServletRequest request) {
        return respond(request, 404, "not-found", null, Map.of(), List.of());
    }

    /** README 5.3 has no type for 405; {@code forbidden} ("not permitted") is the closest. A finding for the template. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String, Object>> wrongMethod(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        ResponseEntity<Map<String, Object>> response = respond(request, 405, "forbidden", null, Map.of(), List.of());
        Set<HttpMethod> allowed = e.getSupportedHttpMethods();
        if (allowed == null || allowed.isEmpty()) {
            return response;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(response.getHeaders());
        headers.setAllow(allowed);
        return new ResponseEntity<>(response.getBody(), headers, response.getStatusCode());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Map<String, Object>> unsupportedMediaType(Exception e, HttpServletRequest request) {
        return respond(request, 415, "unsupported-media", null, Map.of(), List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, Object>> tooLarge(Exception e, HttpServletRequest request) {
        return respond(request, 413, "payload-too-large", null, Map.of(), List.of());
    }

    /** Method security throws these inside the controller layer, where the security filters never see them. */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Map<String, Object>> unauthenticated(AuthenticationException e, HttpServletRequest request) {
        return respond(request, 401, "unauthorized", null, Map.of(), List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, Object>> denied(AccessDeniedException e, HttpServletRequest request) {
        return respond(request, 403, "forbidden", null, Map.of(), List.of());
    }

    /** E1: nothing else leaves the service as HTML or as a stack trace. The cause goes to the log, not to the caller. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception e, HttpServletRequest request) {
        LOG.error("unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), e);
        return respond(request, 500, "internal-error", null, Map.of(), List.of());
    }

    private Problems.FieldIssue issue(String lang, String field, String code, ConstraintViolation<?> violation) {
        Messages messages = problems.messages();
        String shortName = field.contains(".") ? field.substring(field.lastIndexOf('.') + 1) : field;
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("field", messages.has("field." + shortName) ? messages.text(lang, "field." + shortName, Map.of()) : shortName);
        if (violation != null) {
            Map<String, Object> attributes = violation.getConstraintDescriptor().getAttributes();
            for (String name : List.of("min", "max")) {
                if (attributes.containsKey(name)) {
                    args.put(name, attributes.get(name));
                }
            }
            if ("Min".equals(code) && attributes.containsKey("value")) {
                args.put("min", attributes.get("value"));
            }
            if ("Max".equals(code) && attributes.containsKey("value")) {
                args.put("max", attributes.get("value"));
            }
        }
        String key = "validation." + code;
        String message = messages.has(key) ? messages.text(lang, key, args) : messages.text(lang, "problem.validation-failed.detail", args);
        return new Problems.FieldIssue(field, code, message);
    }

    private ResponseEntity<Map<String, Object>> respond(HttpServletRequest request, int status, String slug, String code,
                                                         Map<String, ?> args, List<Problems.FieldIssue> errors) {
        String lang = Language.of(request.getHeader("Accept-Language"));
        return ResponseEntity.status(HttpStatus.valueOf(status))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.CONTENT_LANGUAGE, lang)
                .body(problems.body(request, status, slug, code, args, errors, lang));
    }
}
