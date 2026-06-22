package com.postwerk.service.impl;

import com.postwerk.dto.ImportResultDto;
import com.postwerk.dto.ParameterItemDto;
import com.postwerk.dto.ParameterSetExportDto;
import com.postwerk.dto.ParameterSetRequest;
import com.postwerk.dto.ParameterSetResponse;
import com.postwerk.model.AuditAction;
import com.postwerk.model.ParameterSet;
import com.postwerk.repository.ParameterSetRepository;
import com.postwerk.service.AuditService;
import com.postwerk.service.ParameterSetService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.postwerk.util.ImportHelper;
import com.postwerk.util.RepositoryHelper;
import com.postwerk.util.ReservedParamNames;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default implementation of {@link ParameterSetService}.
 *
 * <p>Manages parameter set schemas used for structured data extraction from emails.
 * Handles CRUD operations, reserved parameter name validation, JSON serialization
 * of nested parameter definitions, and bulk import/export with audit logging.</p>
 *
 * @since 1.0
 */
@Service
public class ParameterSetServiceImpl implements ParameterSetService {

    private final ParameterSetRepository repository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ParameterSetServiceImpl(ParameterSetRepository repository, AuditService auditService, ObjectMapper objectMapper) {
        this.repository = repository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ParameterSetResponse create(UUID organizationId, UUID actingUserId, ParameterSetRequest request, String ipAddress) {
        validateNoReservedNames(request.parameters());

        var parameterSet = ParameterSet.builder()
                .userId(actingUserId)
                .organizationId(organizationId)
                .name(request.name())
                .parameters(serializeParameters(request.parameters()))
                .build();

        var saved = repository.save(parameterSet);
        auditService.log(actingUserId, AuditAction.PARAMETER_SET_CREATED, "ParameterSet: " + saved.getName(), ipAddress);
        return toResponse(saved);
    }

    @Override
    public List<ParameterSetResponse> listByOrg(UUID organizationId) {
        return repository.findByOrganizationId(organizationId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ParameterSetResponse getById(UUID organizationId, UUID parameterSetId) {
        return toResponse(findByOrgAndId(organizationId, parameterSetId));
    }

    @Override
    @Transactional
    public ParameterSetResponse update(UUID organizationId, UUID actingUserId, UUID parameterSetId, ParameterSetRequest request, String ipAddress) {
        validateNoReservedNames(request.parameters());

        var parameterSet = findByOrgAndId(organizationId, parameterSetId);

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("name", parameterSet.getName());
        before.put("parameterCount", deserializeParameters(parameterSet.getParameters()).size());

        parameterSet.setName(request.name());
        parameterSet.setParameters(serializeParameters(request.parameters()));

        var saved = repository.save(parameterSet);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("name", saved.getName());
        after.put("parameterCount", request.parameters() != null ? request.parameters().size() : 0);

        auditService.logDiff(actingUserId, AuditAction.PARAMETER_SET_UPDATED, before, after, "ParameterSet: " + saved.getName(), ipAddress);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID organizationId, UUID actingUserId, UUID parameterSetId, String ipAddress) {
        var parameterSet = findByOrgAndId(organizationId, parameterSetId);
        repository.delete(parameterSet);
        auditService.log(actingUserId, AuditAction.PARAMETER_SET_DELETED, "ParameterSet: " + parameterSet.getName(), ipAddress);
    }

    @Override
    public List<ParameterSetExportDto> exportAll(UUID organizationId) {
        return repository.findByOrganizationId(organizationId).stream()
                .map(ps -> new ParameterSetExportDto(ps.getName(), deserializeParameters(ps.getParameters())))
                .toList();
    }

    @Override
    @Transactional
    public ImportResultDto importAll(UUID organizationId, UUID actingUserId, List<ParameterSetExportDto> items, String ipAddress) {
        return ImportHelper.runImport(items, ParameterSetExportDto::name, item -> {
            var request = new ParameterSetRequest(item.name(), item.parameters());
            create(organizationId, actingUserId, request, ipAddress);
        });
    }

    @Override
    @Transactional
    public ParameterSetResponse toggleLock(UUID organizationId, UUID parameterSetId) {
        var parameterSet = findByOrgAndId(organizationId, parameterSetId);
        parameterSet.setLocked(!parameterSet.isLocked());
        return toResponse(repository.save(parameterSet));
    }

    @Override
    public boolean isLocked(UUID organizationId, UUID parameterSetId) {
        return findByOrgAndId(organizationId, parameterSetId).isLocked();
    }

    private ParameterSet findByOrgAndId(UUID organizationId, UUID parameterSetId) {
        return RepositoryHelper.findOrThrow(repository::findByIdAndOrganizationId, parameterSetId, organizationId, "ParameterSet");
    }

    private ParameterSetResponse toResponse(ParameterSet ps) {
        return new ParameterSetResponse(
                ps.getId(), ps.getName(),
                deserializeParameters(ps.getParameters()),
                ps.isLocked(), ps.getCreatedAt(), ps.getUpdatedAt()
        );
    }

    private void validateNoReservedNames(List<ParameterItemDto> parameters) {
        if (parameters == null) return;
        for (ParameterItemDto param : parameters) {
            checkReserved(param.name());
            if (param.children() != null) {
                for (ParameterItemDto child : param.children()) {
                    checkReserved(child.name());
                }
            }
        }
    }

    private void checkReserved(String name) {
        if (name != null && ReservedParamNames.RESERVED.contains(name)) {
            throw new IllegalArgumentException(
                    "Parameter name '%s' is reserved. Reserved names: %s"
                            .formatted(name, String.join(", ", ReservedParamNames.RESERVED)));
        }
    }

    private String serializeParameters(List<ParameterItemDto> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize parameters", e);
        }
    }

    private List<ParameterItemDto> deserializeParameters(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }
}
