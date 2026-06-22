package com.postwerk.service;

import com.postwerk.dto.ParameterSetExportDto;
import com.postwerk.dto.ParameterSetRequest;
import com.postwerk.dto.ParameterSetResponse;

/**
 * Service interface for managing parameter sets (extraction schemas) that define
 * structured data fields to be extracted from emails by the AI engine.
 * Inherits the org-scoped CRUD + import/export surface from {@link OrgScopedCrudService}.
 *
 * @since 1.0
 */
public interface ParameterSetService extends OrgScopedCrudService<ParameterSetRequest, ParameterSetResponse, ParameterSetExportDto> {
}
