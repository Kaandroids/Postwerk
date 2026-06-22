package com.postwerk.controller;

import com.postwerk.dto.TemplateExportDto;
import com.postwerk.dto.TemplateRequest;
import com.postwerk.dto.TemplateResponse;
import com.postwerk.service.OrgContextService;
import com.postwerk.service.TemplateService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for email template CRUD operations with bulk import/export.
 *
 * <p>Templates support parameter placeholders ({@code {{paramName}}}) that are resolved
 * at automation execution time from extracted data or built-in email fields.
 * Scoped to the active organization (#4); reads require RESOURCE_VIEW, writes RESOURCE_EDIT.
 * All eight endpoints are inherited from {@link OrgScopedCrudController}.</p>
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/templates")
@Tag(name = "Templates", description = "Email reply/forward template management with parameter placeholders")
public class TemplateController extends OrgScopedCrudController<TemplateRequest, TemplateResponse, TemplateExportDto> {

    public TemplateController(TemplateService templateService, OrgContextService orgContext) {
        super(templateService, orgContext);
    }
}
