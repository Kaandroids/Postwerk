package com.postwerk.model.enums;

/**
 * Explicit lifecycle of an announcement. The PUBLISHED state derives a finer display status
 * (SCHEDULED / LIVE / EXPIRED) from its window; DRAFT and ARCHIVED are terminal-ish explicit states.
 */
public enum AnnouncementLifecycle {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}
