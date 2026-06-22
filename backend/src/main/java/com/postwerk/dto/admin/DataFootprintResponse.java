package com.postwerk.dto.admin;

/**
 * Read-only counts of a data subject's stored data — what an export would include / an erasure
 * would remove. Credentials and tokens are deliberately excluded (never exported, revoked on erasure).
 *
 * @since 1.0
 */
public record DataFootprintResponse(
        long mailboxes,
        long emails,
        long automations,
        long conversations,
        long auditEntries
) {}
