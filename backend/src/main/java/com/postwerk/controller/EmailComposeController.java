package com.postwerk.controller;

import com.postwerk.dto.ComposeEmailRequest;
import com.postwerk.dto.ComposeEmailResponse;
import com.postwerk.dto.DraftAttachmentResponse;
import com.postwerk.service.EmailComposeService;
import com.postwerk.service.OrgContext;
import com.postwerk.service.OrgContextService;
import com.postwerk.service.OrgContextService.MailboxAccess;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for composing, sending, replying, forwarding emails, and managing draft attachments.
 *
 * <p>Sending and draft composition require a SEND grant on the mailbox (#4); viewing a draft's
 * attachments requires READ. Owner/Admin bypass via implicit all-mailbox access.</p>
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/email-accounts/{accountId}/emails")
@Tag(name = "Email Compose", description = "Send, reply, forward emails and manage drafts")
public class EmailComposeController {

    private final EmailComposeService composeService;
    private final OrgContextService orgContext;

    public EmailComposeController(EmailComposeService composeService, OrgContextService orgContext) {
        this.composeService = composeService;
        this.orgContext = orgContext;
    }

    /** Resolves org context and enforces the given access level on the path mailbox. */
    private OrgContext requireMailbox(UserDetails userDetails, UUID accountId, MailboxAccess access, HttpServletRequest httpRequest) {
        OrgContext ctx = orgContext.resolve(userDetails, httpRequest);
        orgContext.requireMailbox(ctx, accountId, access);
        return ctx;
    }

    @PostMapping("/send")
    public ResponseEntity<ComposeEmailResponse> send(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID accountId,
            @Valid @RequestBody ComposeEmailRequest request,
            HttpServletRequest httpRequest) {
        OrgContext ctx = requireMailbox(userDetails, accountId, MailboxAccess.SEND, httpRequest);
        return ResponseEntity.ok(composeService.send(ctx.organizationId(), accountId, request));
    }

    @PostMapping("/drafts")
    public ResponseEntity<ComposeEmailResponse> saveDraft(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID accountId,
            @Valid @RequestBody ComposeEmailRequest request,
            HttpServletRequest httpRequest) {
        OrgContext ctx = requireMailbox(userDetails, accountId, MailboxAccess.SEND, httpRequest);
        return ResponseEntity.ok(composeService.saveDraft(ctx.organizationId(), accountId, request));
    }

    @PutMapping("/drafts/{draftId}")
    public ResponseEntity<ComposeEmailResponse> updateDraft(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID accountId,
            @PathVariable UUID draftId,
            @Valid @RequestBody ComposeEmailRequest request,
            HttpServletRequest httpRequest) {
        OrgContext ctx = requireMailbox(userDetails, accountId, MailboxAccess.SEND, httpRequest);
        return ResponseEntity.ok(composeService.updateDraft(ctx.organizationId(), accountId, draftId, request));
    }

    @DeleteMapping("/drafts/{draftId}")
    public ResponseEntity<Void> deleteDraft(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID accountId,
            @PathVariable UUID draftId,
            HttpServletRequest httpRequest) {
        OrgContext ctx = requireMailbox(userDetails, accountId, MailboxAccess.SEND, httpRequest);
        composeService.deleteDraft(ctx.organizationId(), accountId, draftId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/drafts/{draftId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DraftAttachmentResponse> uploadAttachment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID accountId,
            @PathVariable UUID draftId,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest httpRequest) {
        OrgContext ctx = requireMailbox(userDetails, accountId, MailboxAccess.SEND, httpRequest);
        return ResponseEntity.ok(composeService.uploadAttachment(ctx.organizationId(), accountId, draftId, file));
    }

    @DeleteMapping("/drafts/{draftId}/attachments/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID accountId,
            @PathVariable UUID draftId,
            @PathVariable UUID attachmentId,
            HttpServletRequest httpRequest) {
        OrgContext ctx = requireMailbox(userDetails, accountId, MailboxAccess.SEND, httpRequest);
        composeService.deleteAttachment(ctx.organizationId(), accountId, draftId, attachmentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/drafts/{draftId}/attachments")
    public ResponseEntity<List<DraftAttachmentResponse>> listAttachments(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID accountId,
            @PathVariable UUID draftId,
            HttpServletRequest httpRequest) {
        OrgContext ctx = requireMailbox(userDetails, accountId, MailboxAccess.READ, httpRequest);
        return ResponseEntity.ok(composeService.listAttachments(ctx.organizationId(), accountId, draftId));
    }
}
