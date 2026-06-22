package com.postwerk.controller;

import com.postwerk.dto.ParameterSetExportDto;
import com.postwerk.dto.ParameterSetRequest;
import com.postwerk.dto.ParameterSetResponse;
import com.postwerk.service.OrgContextService;
import com.postwerk.service.ParameterSetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for parameter set CRUD operations with bulk import/export.
 *
 * <p>Parameter sets define named collections of extraction parameters used by the
 * EXTRACT automation node. Each parameter specifies a name, type, and description
 * that guides the AI extraction process. Scoped to the active organization (#4);
 * reads require RESOURCE_VIEW, writes RESOURCE_EDIT.
 * All eight endpoints are inherited from {@link OrgScopedCrudController}.</p>
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/parameter-sets")
@Tag(name = "Parameter Sets", description = "AI extraction schema definitions for structured data extraction")
public class ParameterSetController extends OrgScopedCrudController<ParameterSetRequest, ParameterSetResponse, ParameterSetExportDto> {

    public ParameterSetController(ParameterSetService parameterSetService, OrgContextService orgContext) {
        super(parameterSetService, orgContext);
    }
}
