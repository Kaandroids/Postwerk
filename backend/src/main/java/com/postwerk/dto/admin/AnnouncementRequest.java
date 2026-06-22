package com.postwerk.dto.admin;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Create/update payload for an announcement (the editor saves the whole draft). Both DE and EN titles
 * are required to save; publishing additionally requires both bodies (enforced server-side).
 * {@code type}/{@code placement}/{@code audience} are enum names.
 *
 * @since 1.0
 */
public record AnnouncementRequest(
        @NotBlank @Size(max = 200) String titleDe,
        @NotBlank @Size(max = 200) String titleEn,
        @Size(max = 8000) String bodyDe,
        @Size(max = 8000) String bodyEn,
        @Size(max = 120) String ctaLabelDe,
        @Size(max = 120) String ctaLabelEn,
        @Size(max = 2048) String ctaUrl,
        @NotBlank String type,
        String placement,
        String audience,
        List<String> audiencePlans,
        UUID audienceOrgId,
        Boolean dismissible,
        Instant startsAt,
        Instant endsAt
) {
    /** Reject an inverted schedule window (would yield a permanently-expired, never-live announcement). */
    @AssertTrue(message = "endsAt must be after startsAt")
    public boolean isWindowOrdered() {
        return startsAt == null || endsAt == null || endsAt.isAfter(startsAt);
    }

    /** Block non-navigational CTA URIs (e.g. {@code javascript:} / {@code data:}); allow http(s) or relative. */
    @AssertTrue(message = "ctaUrl must be an http(s) or relative URL")
    public boolean isCtaUrlSafe() {
        if (ctaUrl == null || ctaUrl.isBlank()) return true;
        String u = ctaUrl.trim().toLowerCase();
        return u.startsWith("http://") || u.startsWith("https://") || u.startsWith("/");
    }
}
