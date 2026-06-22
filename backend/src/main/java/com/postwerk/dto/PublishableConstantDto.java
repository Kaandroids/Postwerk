package com.postwerk.dto;

/**
 * Author-declared metadata for a buyer-overridable constant of a PRIVATE listing.
 *
 * @param name        the constant key (must match a constant on the source automation)
 * @param description author-supplied explanation shown in the Configure surface
 * @param type        the constant value type ({@code text|number|boolean|url|secret}); response-only
 */
public record PublishableConstantDto(
        String name,
        String description,
        String type
) {
    public PublishableConstantDto(String name, String description) {
        this(name, description, "text");
    }
}
