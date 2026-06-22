package com.postwerk.controller;

import com.postwerk.dto.ConnectionTestRequest;
import com.postwerk.dto.ConnectionTestResponse;
import com.postwerk.dto.EmailAccountRequest;
import com.postwerk.dto.EmailAccountResponse;
import com.postwerk.dto.FolderResponse;
import com.postwerk.model.enums.Permission;
import com.postwerk.service.ConnectionTestService;
import com.postwerk.service.EmailAccountService;
import com.postwerk.service.OrgContext;
import com.postwerk.service.OrgContextService;
import com.postwerk.service.OrgContextService.MailboxAccess;
import com.postwerk.util.IpResolverUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for email account (mailbox) management (IMAP/SMTP configuration).
 *
 * <p>Mailboxes are owned by the active organization (#4). Connecting/editing/deleting a mailbox
 * requires MAILBOX_CONNECT (Owner/Admin); the listing is filtered to mailboxes the caller may read;
 * reading a specific mailbox or its folders requires a READ grant (Owner/Admin bypass via implicit
 * all-mailbox access). Folder mutations additionally require MAILBOX_FOLDERS.</p>
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/email-accounts")
@Tag(name = "Email Accounts", description = "IMAP/SMTP account configuration, connection testing, and folder management")
public class EmailAccountController {

    private final EmailAccountService emailAccountService;
    private final ConnectionTestService connectionTestService;
    private final OrgContextService orgContext;

    public EmailAccountController(EmailAccountService emailAccountService,
                                  ConnectionTestService connectionTestService,
                                  OrgContextService orgContext) {
        this.emailAccountService = emailAccountService;
        this.connectionTestService = connectionTestService;
        this.orgContext = orgContext;
    }

    /** Connects a new mailbox with IMAP/SMTP configuration. */
    @PostMapping
    public ResponseEntity<EmailAccountResponse> create(
            OrgContext ctx,
            @Valid @RequestBody EmailAccountRequest request,
            HttpServletRequest httpRequest) {
        orgContext.require(ctx, Permission.MAILBOX_CONNECT);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(emailAccountService.create(ctx.organizationId(), ctx.userId(), request, IpResolverUtil.extractIp(httpRequest)));
    }

    /** Lists the mailboxes in the active organization the caller may read. */
    @GetMapping
    public ResponseEntity<List<EmailAccountResponse>> list(
            OrgContext ctx) {
        List<EmailAccountResponse> all = emailAccountService.listByOrg(ctx.organizationId());
        return ResponseEntity.ok(orgContext.filterReadableMailboxes(ctx, all, EmailAccountResponse::id));
    }

    /** Returns a single mailbox by ID. */
    @GetMapping("/{id}")
    public ResponseEntity<EmailAccountResponse> get(
            OrgContext ctx,
            @PathVariable UUID id) {
        orgContext.requireMailbox(ctx, id, MailboxAccess.READ);
        return ResponseEntity.ok(emailAccountService.getById(ctx.organizationId(), id));
    }

    /** Updates an existing mailbox's configuration. */
    @PutMapping("/{id}")
    public ResponseEntity<EmailAccountResponse> update(
            OrgContext ctx,
            @PathVariable UUID id,
            @Valid @RequestBody EmailAccountRequest request,
            HttpServletRequest httpRequest) {
        orgContext.require(ctx, Permission.MAILBOX_CONNECT);
        return ResponseEntity.ok(emailAccountService.update(ctx.organizationId(), ctx.userId(), id, request, IpResolverUtil.extractIp(httpRequest)));
    }

    /** Deletes a mailbox and all associated data. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            OrgContext ctx,
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        orgContext.require(ctx, Permission.MAILBOX_CONNECT);
        emailAccountService.delete(ctx.organizationId(), ctx.userId(), id, IpResolverUtil.extractIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    /** Sets a mailbox as the organization's default. */
    @PatchMapping("/{id}/default")
    public ResponseEntity<EmailAccountResponse> setDefault(
            OrgContext ctx,
            @PathVariable UUID id) {
        orgContext.requireMailbox(ctx, id, MailboxAccess.READ);
        return ResponseEntity.ok(emailAccountService.setDefault(ctx.organizationId(), id));
    }

    /** Lists all IMAP folders for the specified mailbox. */
    @GetMapping("/{id}/folders")
    public ResponseEntity<List<FolderResponse>> listFolders(
            OrgContext ctx,
            @PathVariable UUID id) {
        orgContext.requireMailbox(ctx, id, MailboxAccess.READ);
        return ResponseEntity.ok(emailAccountService.listFolders(ctx.organizationId(), id));
    }

    /** Creates a new IMAP folder on the mail server. */
    @PostMapping("/{id}/folders")
    public ResponseEntity<FolderResponse> createFolder(
            OrgContext ctx,
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        orgContext.require(ctx, Permission.MAILBOX_FOLDERS);
        orgContext.requireMailbox(ctx, id, MailboxAccess.READ);
        String folderName = body.get("name");
        if (folderName == null || folderName.isBlank()) {
            throw new IllegalArgumentException("Folder name is required");
        }
        if (!folderName.matches("^[a-zA-Z0-9 _.\\-/]{1,100}$")) {
            throw new IllegalArgumentException("Folder name contains invalid characters");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(emailAccountService.createFolder(ctx.organizationId(), id, folderName));
    }

    /** Deletes an IMAP folder from the mail server. */
    @DeleteMapping("/{id}/folders/{folderId}")
    public ResponseEntity<Void> deleteFolder(
            OrgContext ctx,
            @PathVariable UUID id,
            @PathVariable UUID folderId) {
        orgContext.require(ctx, Permission.MAILBOX_FOLDERS);
        orgContext.requireMailbox(ctx, id, MailboxAccess.READ);
        emailAccountService.deleteFolder(ctx.organizationId(), id, folderId);
        return ResponseEntity.noContent().build();
    }

    /** Tests IMAP/SMTP connection with the provided credentials. */
    @PostMapping("/test-connection")
    public ResponseEntity<ConnectionTestResponse> testConnection(
            OrgContext ctx,
            @Valid @RequestBody ConnectionTestRequest request) {
        orgContext.require(ctx, Permission.MAILBOX_CONNECT);
        return ResponseEntity.ok(connectionTestService.test(request));
    }
}
