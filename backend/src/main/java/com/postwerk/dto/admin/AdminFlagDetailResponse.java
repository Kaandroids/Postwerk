package com.postwerk.dto.admin;

import java.util.List;

/** A feature flag plus its change history — the admin editor's history tab. */
public record AdminFlagDetailResponse(
        AdminFlagResponse flag,
        List<AnnouncementEventResponse> history
) {}
