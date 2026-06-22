package com.postwerk.controller;

import com.postwerk.dto.SecretRequest;
import com.postwerk.dto.SecretResponse;
import com.postwerk.model.enums.Permission;
import com.postwerk.service.OrgContext;
import com.postwerk.service.OrgContextService;
import com.postwerk.service.SecretService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing org-scoped encrypted secrets (API keys, tokens). All operations
 * require the sensitive SECRET_MANAGE permission (#4).
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/secrets")
public class SecretController {

    private final SecretService secretService;
    private final OrgContextService orgContext;

    public SecretController(SecretService secretService,
                            OrgContextService orgContext) {
        this.secretService = secretService;
        this.orgContext = orgContext;
    }

    @GetMapping
    public List<SecretResponse> list(OrgContext ctx) {
        orgContext.require(ctx, Permission.SECRET_MANAGE);
        return secretService.list(ctx.organizationId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SecretResponse create(OrgContext ctx,
                                 @Valid @RequestBody SecretRequest request) {
        orgContext.require(ctx, Permission.SECRET_MANAGE);
        return secretService.create(ctx.organizationId(), ctx.userId(), request);
    }

    @PutMapping("/{id}")
    public SecretResponse update(OrgContext ctx,
                                 @PathVariable UUID id,
                                 @Valid @RequestBody SecretRequest request) {
        orgContext.require(ctx, Permission.SECRET_MANAGE);
        return secretService.update(ctx.organizationId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(OrgContext ctx,
                       @PathVariable UUID id) {
        orgContext.require(ctx, Permission.SECRET_MANAGE);
        secretService.delete(ctx.organizationId(), id);
    }
}
