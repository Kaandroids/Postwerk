package com.postwerk.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Request DTO for creating or updating a model's AI pricing (USD per million tokens). */
public record ModelPricingRequest(
        @NotBlank @Size(max = 100) String model,
        @PositiveOrZero(message = "inputPerMillion must be >= 0") double inputPerMillion,
        @PositiveOrZero(message = "outputPerMillion must be >= 0") double outputPerMillion
) {}
