package com.postwerk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Request to publish one of the author's automations to the marketplace.
 *
 * @param automationId         the author-owned source automation
 * @param publishableConstants for PRIVATE listings: the subset of constants buyers may override
 * @param shareKbEntries       PUBLIC listings: opt-in to ship referenced knowledge-base entries to
 *                             installers (FULL); default/null = SCHEMA_ONLY. PRIVATE always ships FULL
 *                             but content-hidden, so this is ignored for PRIVATE.
 */
public record PublishListingRequest(
        @NotNull UUID automationId,
        @NotBlank @Size(max = 140) String name,
        @Size(max = 280) String tagline,
        @Size(max = 4000) String description,
        @NotBlank @Size(max = 40) String category,
        @NotBlank String visibility,
        @NotBlank String pricingModel,
        BigDecimal price,
        @Size(max = 20) String version,
        String icon,
        String color,
        String ioInIcon,
        @Size(max = 120) String ioInLabel,
        String ioOutIcon,
        @Size(max = 120) String ioOutLabel,
        List<PublishableConstantDto> publishableConstants,
        Boolean shareKbEntries
) {}
