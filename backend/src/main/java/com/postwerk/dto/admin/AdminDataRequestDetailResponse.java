package com.postwerk.dto.admin;

import java.util.List;

/**
 * A DSAR plus its data footprint and timeline — the admin Compliance detail modal.
 *
 * @since 1.0
 */
public record AdminDataRequestDetailResponse(
        AdminDataRequestResponse request,
        DataFootprintResponse footprint,
        List<DataRequestTimelineEntry> timeline
) {}
