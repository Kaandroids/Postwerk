package com.postwerk.controller;

import com.postwerk.dto.ImportResultDto;
import com.postwerk.dto.KbEntryRequest;
import com.postwerk.dto.KbEntryResponse;
import com.postwerk.dto.KbImportRequest;
import com.postwerk.dto.KnowledgeBaseRequest;
import com.postwerk.dto.KnowledgeBaseResponse;
import com.postwerk.model.enums.Permission;
import com.postwerk.service.KnowledgeBaseService;
import com.postwerk.service.OrgContext;
import com.postwerk.service.OrgContextService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for knowledge bases and their entries. Scoped to the active organization (#4);
 * reads require {@code RESOURCE_VIEW}, writes {@code RESOURCE_EDIT}. Entries are searched at runtime
 * by the {@code VECTOR_SEARCH} automation node (see {@code doc/KNOWLEDGE_BASE_DESIGN.md}).
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/knowledge-bases")
@Tag(name = "Knowledge Bases", description = "Org-scoped reference data for the VECTOR_SEARCH automation node")
public class KnowledgeBaseController {

    private final KnowledgeBaseService service;
    private final OrgContextService orgContext;

    public KnowledgeBaseController(KnowledgeBaseService service, OrgContextService orgContext) {
        this.service = service;
        this.orgContext = orgContext;
    }

    @GetMapping
    public ResponseEntity<List<KnowledgeBaseResponse>> list(OrgContext ctx) {
        orgContext.require(ctx, Permission.RESOURCE_VIEW);
        return ResponseEntity.ok(service.listByOrg(ctx.organizationId()));
    }

    @PostMapping
    public ResponseEntity<KnowledgeBaseResponse> create(OrgContext ctx, @Valid @RequestBody KnowledgeBaseRequest request) {
        orgContext.require(ctx, Permission.RESOURCE_EDIT);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(ctx.organizationId(), ctx.userId(), request, null));
    }

    @GetMapping("/{id}")
    public ResponseEntity<KnowledgeBaseResponse> get(OrgContext ctx, @PathVariable UUID id) {
        orgContext.require(ctx, Permission.RESOURCE_VIEW);
        return ResponseEntity.ok(service.getById(ctx.organizationId(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<KnowledgeBaseResponse> update(OrgContext ctx, @PathVariable UUID id,
                                                        @Valid @RequestBody KnowledgeBaseRequest request) {
        orgContext.require(ctx, Permission.RESOURCE_EDIT);
        return ResponseEntity.ok(service.update(ctx.organizationId(), ctx.userId(), id, request, null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(OrgContext ctx, @PathVariable UUID id) {
        orgContext.require(ctx, Permission.RESOURCE_EDIT);
        service.delete(ctx.organizationId(), ctx.userId(), id, null);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/entries")
    public ResponseEntity<List<KbEntryResponse>> entries(OrgContext ctx, @PathVariable UUID id) {
        orgContext.require(ctx, Permission.RESOURCE_VIEW);
        return ResponseEntity.ok(service.listEntries(ctx.organizationId(), id));
    }

    @PostMapping("/{id}/entries")
    public ResponseEntity<KbEntryResponse> addEntry(OrgContext ctx, @PathVariable UUID id,
                                                    @Valid @RequestBody KbEntryRequest request) {
        orgContext.require(ctx, Permission.RESOURCE_EDIT);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.addEntry(ctx.organizationId(), ctx.userId(), id, request));
    }

    @PutMapping("/{id}/entries/{entryId}")
    public ResponseEntity<KbEntryResponse> updateEntry(OrgContext ctx, @PathVariable UUID id, @PathVariable UUID entryId,
                                                       @Valid @RequestBody KbEntryRequest request) {
        orgContext.require(ctx, Permission.RESOURCE_EDIT);
        return ResponseEntity.ok(service.updateEntry(ctx.organizationId(), ctx.userId(), id, entryId, request));
    }

    @DeleteMapping("/{id}/entries/{entryId}")
    public ResponseEntity<Void> deleteEntry(OrgContext ctx, @PathVariable UUID id, @PathVariable UUID entryId) {
        orgContext.require(ctx, Permission.RESOURCE_EDIT);
        service.deleteEntry(ctx.organizationId(), id, entryId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/import")
    public ResponseEntity<ImportResultDto> importEntries(OrgContext ctx, @PathVariable UUID id,
                                                         @Valid @RequestBody KbImportRequest request) {
        orgContext.require(ctx, Permission.RESOURCE_EDIT);
        return ResponseEntity.ok(service.importEntries(ctx.organizationId(), ctx.userId(), id, request, null));
    }
}
