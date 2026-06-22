package com.postwerk.service;

import com.postwerk.dto.TemplateExportDto;
import com.postwerk.dto.TemplateRequest;
import com.postwerk.dto.TemplateResponse;

/**
 * Service interface for managing email reply templates with placeholder variable support.
 * Inherits the org-scoped CRUD + import/export surface from {@link OrgScopedCrudService}.
 *
 * @since 1.0
 */
public interface TemplateService extends OrgScopedCrudService<TemplateRequest, TemplateResponse, TemplateExportDto> {
}
