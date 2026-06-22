package com.postwerk.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of a new internal staff note about a user. */
public record StaffNoteRequest(
        @NotBlank @Size(max = 4000) String body
) {}
