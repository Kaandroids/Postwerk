package com.postwerk.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * DTO for importing/exporting parameter sets (AI extraction schemas) in bulk.
 *
 * @param name       the parameter set display name
 * @param parameters the list of parameter definitions for structured data extraction
 */
public record ParameterSetExportDto(
        @NotBlank(message = "Parameter set name is required") @Size(max = 200) String name,
        @NotEmpty(message = "At least one parameter is required") @Valid List<ParameterItemDto> parameters
) {}
