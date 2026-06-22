package com.postwerk.service;

import com.postwerk.dto.automation.ActivityEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Production activity feed (#3d): recent live automation runs for a user, surfaced from persisted
 * execution traces with each step's result and the AI's reasoning.
 *
 * @since 1.0
 */
public interface ActivityService {

    /** Recent live runs across all the organization's automations, newest first. */
    Page<ActivityEntry> getRecent(UUID organizationId, Pageable pageable);
}
