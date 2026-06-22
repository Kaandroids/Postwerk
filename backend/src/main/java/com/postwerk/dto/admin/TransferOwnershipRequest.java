package com.postwerk.dto.admin;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Request to transfer an organization's ownership to one of its existing members. */
public record TransferOwnershipRequest(
        @NotNull UUID newOwnerUserId
) {}
