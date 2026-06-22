package com.postwerk.dto.admin;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Request DTO for assigning a subscription plan to a user. */
public record AssignPlanRequest(
        @NotNull UUID planId
) {}
