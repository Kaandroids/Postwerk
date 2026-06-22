package com.postwerk.dto;

/**
 * A knowledge-base field's retrieval roles: whether the field feeds the semantic embedding
 * ({@code embed}) and/or the keyword full-text index ({@code keyword}). Stored per-field in the
 * KB's {@code fieldRoles} overlay; keys reference the borrowed parameter set's field names.
 *
 * @since 1.0
 */
public record KbFieldRole(boolean embed, boolean keyword) {}
