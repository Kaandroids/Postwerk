package com.postwerk.dto;

import jakarta.validation.constraints.NotNull;

public record TestModeFeedbackRequest(
        @NotNull String feedback,
        String note
) {}
