package com.postwerk.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** DTO for a single extraction parameter definition (name, type, description). */
public record ParameterItemDto(
        @NotBlank @Size(max = 100) String name,
        @NotBlank String type,
        @Size(max = 500) String description,
        String positiveExample,
        String negativeExample,
        boolean isList,
        boolean required,
        @Valid List<ParameterItemDto> children
) {}
