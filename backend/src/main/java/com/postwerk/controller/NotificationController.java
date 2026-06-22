package com.postwerk.controller;

import com.postwerk.dto.NotificationListResponse;
import com.postwerk.dto.NotificationPreferenceDto;
import com.postwerk.dto.UnreadCountResponse;
import com.postwerk.service.NotificationService;
import com.postwerk.service.OrgContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for the current user's notification inbox and delivery preferences. The inbox is
 * personal (recipient-scoped), so endpoints key off {@code ctx.userId()} — the {@link OrgContext} is
 * resolved only to authenticate the caller (active membership). See {@code doc/NOTIFICATION_SYSTEM_DESIGN.md}.
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Per-user notification inbox and delivery preferences")
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public NotificationListResponse list(OrgContext ctx,
                                         @RequestParam(name = "unread", defaultValue = "false") boolean unread,
                                         @RequestParam(name = "page", defaultValue = "0") int page,
                                         @RequestParam(name = "size", defaultValue = "30") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return service.list(ctx.userId(), unread, PageRequest.of(Math.max(page, 0), safeSize));
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(OrgContext ctx) {
        return new UnreadCountResponse(service.unreadCount(ctx.userId()));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(OrgContext ctx, @PathVariable UUID id) {
        service.markRead(ctx.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(OrgContext ctx) {
        service.markAllRead(ctx.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/preferences")
    public List<NotificationPreferenceDto> getPreferences(OrgContext ctx) {
        return service.getPreferences(ctx.userId());
    }

    @PutMapping("/preferences")
    public List<NotificationPreferenceDto> updatePreferences(OrgContext ctx,
                                                             @RequestBody List<NotificationPreferenceDto> preferences) {
        return service.updatePreferences(ctx.userId(), preferences);
    }
}
