package com.postwerk.util;

import com.postwerk.exception.ResourceNotFoundException;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Shared lookup utility that eliminates the repeated {@code findByUserAndId} pattern
 * across service implementations.
 *
 * @since 1.0
 */
public final class RepositoryHelper {

    private RepositoryHelper() {}

    /**
     * Looks up an entity by two UUID keys (typically entityId + userId) and throws
     * {@link ResourceNotFoundException} if the entity is not found.
     *
     * @param finder     repository method reference, e.g. {@code repo::findByIdAndUserId}
     * @param entityId   the primary entity identifier
     * @param userId     the owning user identifier
     * @param entityName human-readable entity name for the error message
     * @param <T>        entity type
     * @return the found entity, never {@code null}
     */
    public static <T> T findOrThrow(BiFunction<UUID, UUID, Optional<T>> finder,
                                     UUID entityId, UUID userId, String entityName) {
        return finder.apply(entityId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(entityName, entityId));
    }
}
