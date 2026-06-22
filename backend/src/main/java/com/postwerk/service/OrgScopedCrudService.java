package com.postwerk.service;

import com.postwerk.dto.ImportResultDto;

import java.util.List;
import java.util.UUID;

/**
 * Common contract for the org-scoped CRUD resources that share an identical management surface:
 * categories, templates, and parameter sets. Centralizes the create/list/get/update/delete +
 * lock + bulk import/export operations so {@link com.postwerk.controller.OrgScopedCrudController}
 * can serve all three from one generic base.
 *
 * <p>Methods are scoped by {@code organizationId} (the multi-tenant key, #4); an
 * {@code actingUserId} is threaded separately for audit logging and AI-embedding quota
 * attribution.</p>
 *
 * @param <Req>  the create/update request DTO
 * @param <Resp> the response DTO
 * @param <Exp>  the export/import row DTO
 * @since 1.0
 */
public interface OrgScopedCrudService<Req, Resp, Exp> {

    Resp create(UUID organizationId, UUID actingUserId, Req request, String ipAddress);

    List<Resp> listByOrg(UUID organizationId);

    Resp getById(UUID organizationId, UUID id);

    Resp update(UUID organizationId, UUID actingUserId, UUID id, Req request, String ipAddress);

    void delete(UUID organizationId, UUID actingUserId, UUID id, String ipAddress);

    List<Exp> exportAll(UUID organizationId);

    ImportResultDto importAll(UUID organizationId, UUID actingUserId, List<Exp> items, String ipAddress);

    Resp toggleLock(UUID organizationId, UUID id);

    boolean isLocked(UUID organizationId, UUID id);
}
