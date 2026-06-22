package com.postwerk.exception;

/**
 * Thrown when a requested resource (entity) cannot be found by its identifier.
 *
 * <p>Handled globally by {@link GlobalExceptionHandler} to return HTTP 404.</p>
 *
 * @since 1.0
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super("%s not found with id: %s".formatted(resource, id));
    }
}
