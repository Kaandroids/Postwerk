package com.postwerk.controller;

import com.postwerk.dto.ImportResultDto;
import com.postwerk.model.enums.Permission;
import com.postwerk.service.OrgContext;
import com.postwerk.service.OrgContextService;
import com.postwerk.service.OrgScopedCrudService;
import com.postwerk.util.IpResolverUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Generic REST base for org-scoped CRUD resources with bulk import/export (categories, templates,
 * parameter sets). Concrete controllers only declare {@code @RestController},
 * {@code @RequestMapping("/api/v1/...")}, and a constructor passing the matching
 * {@link OrgScopedCrudService}; the eight endpoints below are inherited.
 *
 * <p>The active {@link OrgContext} is supplied by {@code OrgContextArgumentResolver}; each handler
 * gates access explicitly with {@link OrgContextService#require(OrgContext, Permission)}
 * — reads need {@link Permission#RESOURCE_VIEW}, writes {@link Permission#RESOURCE_EDIT}.</p>
 *
 * @param <Req>  the create/update request DTO
 * @param <Resp> the response DTO
 * @param <Exp>  the export/import row DTO
 * @since 1.0
 */
public abstract class OrgScopedCrudController<Req, Resp, Exp> {

    private final OrgScopedCrudService<Req, Resp, Exp> service;
    private final OrgContextService orgContext;

    protected OrgScopedCrudController(OrgScopedCrudService<Req, Resp, Exp> service, OrgContextService orgContext) {
        this.service = service;
        this.orgContext = orgContext;
    }

    /** Creates a new resource in the active organization. */
    @PostMapping
    public ResponseEntity<Resp> create(OrgContext ctx, @Valid @RequestBody Req request, HttpServletRequest httpRequest) {
        orgContext.require(ctx, Permission.RESOURCE_EDIT);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(ctx.organizationId(), ctx.userId(), request, IpResolverUtil.extractIp(httpRequest)));
    }

    /** Lists all resources in the active organization. */
    @GetMapping
    public ResponseEntity<List<Resp>> list(OrgContext ctx) {
        orgContext.require(ctx, Permission.RESOURCE_VIEW);
        return ResponseEntity.ok(service.listByOrg(ctx.organizationId()));
    }

    /** Returns a single resource by ID. */
    @GetMapping("/{id}")
    public ResponseEntity<Resp> get(OrgContext ctx, @PathVariable UUID id) {
        orgContext.require(ctx, Permission.RESOURCE_VIEW);
        return ResponseEntity.ok(service.getById(ctx.organizationId(), id));
    }

    /** Updates an existing resource. */
    @PutMapping("/{id}")
    public ResponseEntity<Resp> update(OrgContext ctx, @PathVariable UUID id,
                                       @Valid @RequestBody Req request, HttpServletRequest httpRequest) {
        orgContext.require(ctx, Permission.RESOURCE_EDIT);
        return ResponseEntity.ok(service.update(ctx.organizationId(), ctx.userId(), id, request, IpResolverUtil.extractIp(httpRequest)));
    }

    /** Toggles the lock state of a resource. */
    @PatchMapping("/{id}/lock")
    public ResponseEntity<Resp> toggleLock(OrgContext ctx, @PathVariable UUID id) {
        orgContext.require(ctx, Permission.RESOURCE_EDIT);
        return ResponseEntity.ok(service.toggleLock(ctx.organizationId(), id));
    }

    /** Deletes a resource by ID. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(OrgContext ctx, @PathVariable UUID id, HttpServletRequest httpRequest) {
        orgContext.require(ctx, Permission.RESOURCE_EDIT);
        service.delete(ctx.organizationId(), ctx.userId(), id, IpResolverUtil.extractIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    /** Exports all resources as portable DTOs for backup or migration. */
    @GetMapping("/export")
    public ResponseEntity<List<Exp>> exportAll(OrgContext ctx) {
        orgContext.require(ctx, Permission.RESOURCE_VIEW);
        return ResponseEntity.ok(service.exportAll(ctx.organizationId()));
    }

    /** Imports resources from a bulk export, creating each as a new entity. */
    @PostMapping("/import")
    public ResponseEntity<ImportResultDto> importAll(OrgContext ctx, @Valid @RequestBody List<Exp> items,
                                                     HttpServletRequest httpRequest) {
        orgContext.require(ctx, Permission.RESOURCE_EDIT);
        return ResponseEntity.ok(service.importAll(ctx.organizationId(), ctx.userId(), items, IpResolverUtil.extractIp(httpRequest)));
    }
}
