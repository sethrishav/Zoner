package com.zoner.common.error;

import com.zoner.common.logging.CorrelationIdFilter;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every failure into the same {@link ApiError} body. Internal details (exception messages,
 * stack traces, SQL) are logged with the traceId but never returned to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    // ---- our own, expected failures ----

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        log.info("Request rejected: {} - {}", ex.getCode(), ex.getMessage());
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiError.FieldIssue> issues = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldIssue(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Some fields are invalid.", issues);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleStaleVersion(ObjectOptimisticLockingFailureException ex) {
        log.info("Optimistic locking conflict: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, ErrorCode.STALE_VERSION,
                "This item was changed by someone else. Reload it and try again.", List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return build(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                "The request conflicts with existing data.", List.of());
    }

    // ---- last resort ----

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "Something went wrong on our side. Please try again.", List.of());
    }

    // ---- Spring MVC's standard exceptions (bad JSON, wrong method, unknown route, ...) ----

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiError.FieldIssue> issues = ex.getBindingResult().getAllErrors().stream()
                .map(e -> new ApiError.FieldIssue(
                        e instanceof FieldError fe ? fe.getField() : e.getObjectName(),
                        e.getDefaultMessage()))
                .toList();
        return new ResponseEntity<>(
                body(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Some fields are invalid.", issues),
                headers, HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object ignoredBody, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            log.error("Server error handling request", ex);
        } else {
            log.debug("Client error: {}", ex.getMessage());
        }
        ApiError error = body(statusCode, codeFor(statusCode), messageFor(statusCode), List.of());
        return new ResponseEntity<>(error, headers, statusCode);
    }

    // ---- helpers ----

    private ResponseEntity<ApiError> build(
            HttpStatusCode status, ErrorCode code, String message, List<ApiError.FieldIssue> details) {
        return ResponseEntity.status(status).body(body(status, code, message, details));
    }

    private ApiError body(HttpStatusCode status, ErrorCode code, String message, List<ApiError.FieldIssue> details) {
        return new ApiError(status.value(), code, message, details,
                MDC.get(CorrelationIdFilter.MDC_KEY), clock.instant());
    }

    private static ErrorCode codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> ErrorCode.BAD_REQUEST;
            case 401 -> ErrorCode.UNAUTHENTICATED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 409 -> ErrorCode.CONFLICT;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case 429 -> ErrorCode.RATE_LIMITED;
            default -> status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.BAD_REQUEST;
        };
    }

    private static String messageFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "The request could not be understood.";
            case 404 -> "We could not find what you asked for.";
            case 405 -> "That action is not supported here.";
            case 415 -> "That content type is not supported.";
            default -> status.is5xxServerError()
                    ? "Something went wrong on our side. Please try again."
                    : "The request could not be completed.";
        };
    }
}
