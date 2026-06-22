package com.postwerk.dto.admin;

import java.util.List;

/** An announcement plus its change history — the admin editor's history tab. */
public record AdminAnnouncementDetailResponse(
        AdminAnnouncementResponse announcement,
        List<AnnouncementEventResponse> history
) {}
