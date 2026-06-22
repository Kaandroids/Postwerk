package com.postwerk.dto.admin;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Change an organization's plan (admin Plans &amp; Subscriptions). */
public record ChangePlanRequest(
        @NotNull UUID planId,
        String reason
) {}
