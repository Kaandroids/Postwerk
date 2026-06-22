package com.postwerk.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Request to bind the buyer's email accounts to an installed marketplace automation's trigger. */
public record AccountBindingRequest(
        @NotNull List<UUID> accountIds
) {}
