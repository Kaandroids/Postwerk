package com.postwerk.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.mail.MessagingException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized exception handler for the REST API.
 *
 * <p>Catches domain-specific and framework exceptions and maps them to
 * consistent JSON error responses with appropriate HTTP status codes.
 * Covers authentication errors (401), validation errors (400), not-found (404),
 * conflict/duplicate (409), mail transport failures (502), and unexpected
 * server errors (500).</p>
 *
 * @since 1.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Standard error response envelope returned to API clients. */
    public record ErrorResponse(int status, String message, Instant timestamp) {}

    /** Quota-specific error response with limit details. */
    public record QuotaErrorResponse(int status, String limitType, long currentUsage,
                                      long maxAllowed, String planName, String message,
                                      Instant timestamp) {}

    /** Handles quota exceeded errors (HTTP 429). */
    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<QuotaErrorResponse> handleQuota(QuotaExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new QuotaErrorResponse(429, ex.getLimitType(), ex.getCurrentUsage(),
                        ex.getMaxAllowed(), ex.getPlanName(), ex.getMessage(), Instant.now()));
    }

    /** Handles domain resource not-found errors. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** Handles Bean Validation failures on request DTOs. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", (Object) e.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(Map.of(
                "status", 400,
                "message", "Validation failed",
                "errors", errors,
                "timestamp", Instant.now()
        ));
    }

    /** Handles Jakarta Bean Validation constraint violations (e.g. @PathVariable, @RequestParam). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));
        return ResponseEntity.badRequest().body(errors);
    }

    /** Handles authentication and authorization errors. */
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponse> handleAuth(AuthException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    /** Handles illegal argument errors from service-layer validation. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Handles illegal state errors (e.g. missing configuration, invalid workflow state). */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        log.warn("Illegal state: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "The request could not be processed due to an invalid state");
    }

    /** Handles Spring Security access denied errors (insufficient privileges). */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, "Access denied");
    }

    /** Handles database constraint violations (unique key, foreign key). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return buildResponse(HttpStatus.CONFLICT, "A resource with the same unique identifier already exists");
    }

    /** Handles malformed JSON request bodies. */
    @ExceptionHandler({HttpMessageNotReadableException.class, JsonProcessingException.class})
    public ResponseEntity<ErrorResponse> handleMalformedJson(Exception ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Malformed request body — expected valid JSON");
    }

    /** Handles missing required query parameters. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Missing required parameter: " + ex.getParameterName());
    }

    /** Handles type conversion errors for path/query parameters. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid value for parameter: " + ex.getName());
    }

    /** Handles JavaMail transport failures (IMAP/SMTP errors). */
    @ExceptionHandler(MessagingException.class)
    public ResponseEntity<ErrorResponse> handleMessaging(MessagingException ex) {
        log.error("Mail transport error: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_GATEWAY, "Mail server communication failed");
    }

    /** Catch-all handler for unexpected server errors. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), message, Instant.now()));
    }
}
