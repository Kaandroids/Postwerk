package com.postwerk.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request DTO for creating or updating a parameter set. */
public record ParameterSetRequest(
        @NotBlank @Size(min = 3, max = 100) String name,
        @Valid List<ParameterItemDto> parameters
) {}
