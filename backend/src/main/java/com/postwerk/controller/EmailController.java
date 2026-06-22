package com.postwerk.controller;

import com.postwerk.dto.EmailCategoryItem;
import com.postwerk.dto.EmailListResponse;
import com.postwerk.dto.EmailResponse;
import com.postwerk.dto.EmailSyncResponse;
import com.postwerk.service.EmailService;
import com.postwerk.service.OrgContext;
import com.postwerk.service.OrgContextService;
import com.postwerk.service.OrgContextService.MailboxAccess;
import com.postwerk.util.IpResolverUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for email operations scoped to a specific mailbox.
 *
 * <p>Every operation requires a READ grant on the target mailbox (#4): Owner/Admin bypass via
 * implicit all-mailbox access, Member/Viewer need an explicit MailboxGrant. The mailbox itself must
 * belong to the active organization.</p>
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/email-accounts/{accountId}/emails")
@Tag(name = "Emails", description = "Email listing, filtering, read/star operations, attachment download, and IMAP sync")
public class EmailController {

    private final EmailService emailService;
    private final OrgContextService orgContext;

    public EmailController(EmailService emailService, OrgContextService orgContext) {
        this.emailService = emailService;
        this.orgContext = orgContext;
    }

    /** Resolves the org context and enforces a READ grant on the path mailbox. */
    private OrgContext requireRead(UserDetails userDetails, UUID accountId, HttpServletRequest httpRequest) {
        OrgContext ctx = orgContext.resolve(userDetails, httpRequest);
        orgContext.requireMailbox(ctx, accountId, MailboxAccess.READ);
        return ctx;
    }

    /** Lists emails with optional filtering by folder, query, read status, date range, category, and automation. */
    @GetMapping
    public ResponseEntity<Page<EmailListResponse>> list(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID accountId,
            @RequestParam(required = false) String folder,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(required = false) Instant dateFrom,
            @RequestParam(required = false) Instant dateTo,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) Boolean processed,
            @RequestParam(required = false) UUID automationId,
            @PageableDefault(size = 20, sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletRequest httpRequest) {
        OrgContext ctx = requireRead(userDetails, accountId, httpRequest);
        return ResponseEntity.ok(emailService.list(ctx.organizationId(), accountId, folder, query, isRead, dateFrom, dateTo,
                categoryId, processed, automationId, pageable));
    }

    /** Returns a single email with full body and on-demand attachment metadata backfill. */
    @GetMapping("/{emailId}")
    public ResponseEntity<EmailResponse> get(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID accountId,
            @PathVariable UUID emailId,
            HttpServletRequest httpRequest) {
        OrgContext ctx = requireRead(userDetails, accountId, httpRequest);
        return ResponseEntity.ok(emailService.getById(ctx.organizationId(), ctx.userId(), accountId, emailId, IpResolverUtil.extractIp(httpRequest)));
    }

    /** Marks an email as read or unread. */
    @PatchMapping("/{emailId}/read")
    public ResponseEntity<EmailResponse> markRead(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID accountId,
            @PathVariable UUID emailId,
            @RequestBody Map<String, Boolean> body,
            HttpServletRequest httpRequest) {
        OrgContext ctx = requireRead(userDetails, accountId, httpRequest);
        boolean read = body.getOrDefault("read", true);
        return ResponseEntity.ok(emailService.markRead(ctx.organizationId(), accountId, emailId, read));
    }

    /** Toggles the starred flag on an email. */
    @PatchMapping("/{emailId}/star")
    public ResponseEntity<EmailResponse> toggleStar(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID accountId,
            @PathVariable UUID emailId,
            HttpServletRequest httpRequest) {
        OrgContext ctx = requireRead(userDetails, accountId, httpRequest);
        return ResponseEntity.ok(emailService.toggleStar(ctx.organizationId(), accountId, emailId));
    }

    /** Assigns or replaces categories on an email. */
    @PatchMapping("/{emailId}/categories")
    public ResponseEntity<EmailResponse> assignCategories(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID accountId,
            @PathVariable UUID emailId,
            @RequestBody List<EmailCategoryItem> categories,
            HttpServletRequest httpRequest) {
        OrgContext ctx = requireRead(userDetails, accountId, httpRequest);
        return ResponseEntity.ok(emailService.assignCategories(ctx.organizationId(), accountId, emailId, categories));
    }

    /** Re-runs automation processing on an already-processed email. */
    @PostMapping("/{emailId}/reprocess")
    public ResponseEntity<EmailResponse> reprocess(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID accountId,
            @PathVariable UUID emailId,
            HttpServletRequest httpRequest) {
        OrgContext ctx = requireRead(userDetails, accountId, httpRequest);
        return ResponseEntity.ok(emailService.reprocess(ctx.organizationId(), accountId, emailId));
    }

    /** Downloads a specific attachment by index directly from the IMAP server. */
    @GetMapping("/{emailId}/attachments/{index}")
    public ResponseEntity<byte[]> downloadAttachment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID accountId,
            @PathVariable UUID emailId,
            @PathVariable int index,
            HttpServletRequest httpRequest) {
        OrgContext ctx = requireRead(userDetails, accountId, httpRequest);
        Object[] result = emailService.downloadAttachment(ctx.organizationId(), accountId, emailId, index);
        String fileName = (String) result[0];
        byte[] data = (byte[]) result[2];

        // Sanitize filename against path traversal
        String safeName = java.nio.file.Paths.get(fileName).getFileName().toString()
                .replaceAll("[^a-zA-Z0-9._\\-]", "_");
        if (safeName.isBlank()) safeName = "attachment";

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(safeName)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(data);
    }

    /** Moves an email to Trash (Papierkorb); deleting one already in Trash removes it permanently. */
    @DeleteMapping("/{emailId}")
    public ResponseEntity<Void> deleteEmail(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID accountId,
            @PathVariable UUID emailId,
            HttpServletRequest httpRequest) {
        OrgContext ctx = requireRead(userDetails, accountId, httpRequest);
        emailService.deleteEmail(ctx.organizationId(), accountId, emailId);
        return ResponseEntity.noContent().build();
    }

    /** Restores a trashed email back to its original folder. */
    @PostMapping("/{emailId}/restore")
    public ResponseEntity<Void> restoreEmail(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID accountId,
            @PathVariable UUID emailId,
            HttpServletRequest httpRequest) {
        OrgContext ctx = requireRead(userDetails, accountId, httpRequest);
        emailService.restoreEmail(ctx.organizationId(), accountId, emailId);
        return ResponseEntity.noContent().build();
    }

    /** Empties the Trash for this mailbox (permanently deletes all trashed emails). */
    @DeleteMapping("/trash")
    public ResponseEntity<Map<String, Integer>> emptyTrash(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID accountId,
            HttpServletRequest httpRequest) {
        OrgContext ctx = requireRead(userDetails, accountId, httpRequest);
        int deleted = emailService.emptyTrash(ctx.organizationId(), accountId);
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    /** Triggers an IMAP sync for the specified mailbox. */
    @PostMapping("/sync")
    public ResponseEntity<EmailSyncResponse> sync(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID accountId,
            HttpServletRequest httpRequest) {
        OrgContext ctx = requireRead(userDetails, accountId, httpRequest);
        return ResponseEntity.ok(emailService.sync(ctx.organizationId(), ctx.userId(), accountId, IpResolverUtil.extractIp(httpRequest)));
    }
}
