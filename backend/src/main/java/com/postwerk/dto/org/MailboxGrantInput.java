package com.postwerk.dto.org;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * One mailbox access entry when setting a member's per-mailbox grants (#4).
 *
 * @since 1.0
 */
public record MailboxGrantInput(
        @NotNull UUID mailboxId,
        boolean canRead,
        boolean canSend) {
}
