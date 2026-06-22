package com.postwerk.service.impl;

import com.postwerk.dto.ImportResultDto;
import com.postwerk.dto.TemplateExportDto;
import com.postwerk.dto.TemplateRequest;
import com.postwerk.dto.TemplateResponse;
import com.postwerk.model.AuditAction;
import com.postwerk.model.Template;
import com.postwerk.repository.ParameterSetRepository;
import com.postwerk.repository.TemplateRepository;
import com.postwerk.service.AuditService;
import com.postwerk.service.TemplateService;
import com.postwerk.util.ImportHelper;
import com.postwerk.util.RepositoryHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link TemplateService}.
 *
 * <p>Manages email reply templates with support for mustache-style placeholder extraction,
 * optional parameter set linkage, and bulk import/export. All mutations are audit-logged.</p>
 *
 * @since 1.0
 */
@Service
public class TemplateServiceImpl implements TemplateService {

    private static final Pattern PARAM_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    /** Dangerous HTML tags that could execute scripts */
    private static final Pattern DANGEROUS_TAGS = Pattern.compile(
            "<\\s*/?(script|iframe|object|embed|form|input|textarea|button|applet|meta|link|base|svg|math)[^>]*>",
            Pattern.CASE_INSENSITIVE
    );

    /** Inline event handlers (onclick, onerror, onload, etc.) */
    private static final Pattern EVENT_HANDLERS = Pattern.compile(
            "\\bon\\w+\\s*=",
            Pattern.CASE_INSENSITIVE
    );

    /** javascript: / vbscript: / data: URI schemes in attributes */
    private static final Pattern DANGEROUS_URIS = Pattern.compile(
            "(javascript|vbscript|data)\\s*:",
            Pattern.CASE_INSENSITIVE
    );

    private final TemplateRepository repository;
    private final ParameterSetRepository parameterSetRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public TemplateServiceImpl(TemplateRepository repository, ParameterSetRepository parameterSetRepository,
                               AuditService auditService, ObjectMapper objectMapper) {
        this.repository = repository;
        this.parameterSetRepository = parameterSetRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public TemplateResponse create(UUID organizationId, UUID actingUserId, TemplateRequest request, String ipAddress) {
        String sanitizedBody = sanitizeHtml(request.body());
        List<String> params = extractParams(request.subject(), sanitizedBody);

        var template = Template.builder()
                .userId(actingUserId)
                .organizationId(organizationId)
                .name(request.name())
                .subject(request.subject())
                .body(sanitizedBody)
                .params(toJsonArray(params))
                .parameterSetId(request.parameterSetId())
                .build();

        var saved = repository.save(template);
        auditService.log(actingUserId, AuditAction.TEMPLATE_CREATED, "Template: " + saved.getName(), ipAddress);
        return toResponse(saved);
    }

    @Override
    public List<TemplateResponse> listByOrg(UUID organizationId) {
        return repository.findByOrganizationId(organizationId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public TemplateResponse getById(UUID organizationId, UUID templateId) {
        return toResponse(findByOrgAndId(organizationId, templateId));
    }

    @Override
    @Transactional
    public TemplateResponse update(UUID organizationId, UUID actingUserId, UUID templateId, TemplateRequest request, String ipAddress) {
        var template = findByOrgAndId(organizationId, templateId);

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("name", template.getName());
        before.put("subject", template.getSubject() != null ? template.getSubject() : "");

        String sanitizedBody = sanitizeHtml(request.body());
        List<String> params = extractParams(request.subject(), sanitizedBody);

        template.setName(request.name());
        template.setSubject(request.subject());
        template.setBody(sanitizedBody);
        template.setParams(toJsonArray(params));
        template.setParameterSetId(request.parameterSetId());

        var saved = repository.save(template);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("name", saved.getName());
        after.put("subject", saved.getSubject() != null ? saved.getSubject() : "");

        auditService.logDiff(actingUserId, AuditAction.TEMPLATE_UPDATED, before, after, "Template: " + saved.getName(), ipAddress);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID organizationId, UUID actingUserId, UUID templateId, String ipAddress) {
        var template = findByOrgAndId(organizationId, templateId);
        repository.delete(template);
        auditService.log(actingUserId, AuditAction.TEMPLATE_DELETED, "Template: " + template.getName(), ipAddress);
    }

    @Override
    public List<TemplateExportDto> exportAll(UUID organizationId) {
        return repository.findByOrganizationId(organizationId).stream()
                .map(t -> new TemplateExportDto(t.getName(), t.getSubject(), t.getBody()))
                .toList();
    }

    @Override
    @Transactional
    public ImportResultDto importAll(UUID organizationId, UUID actingUserId, List<TemplateExportDto> items, String ipAddress) {
        return ImportHelper.runImport(items, TemplateExportDto::name, item -> {
            var request = new TemplateRequest(item.name(), item.subject(), item.body(), null);
            create(organizationId, actingUserId, request, ipAddress);
        });
    }

    @Override
    @Transactional
    public TemplateResponse toggleLock(UUID organizationId, UUID templateId) {
        var template = findByOrgAndId(organizationId, templateId);
        template.setLocked(!template.isLocked());
        return toResponse(repository.save(template));
    }

    @Override
    public boolean isLocked(UUID organizationId, UUID templateId) {
        return findByOrgAndId(organizationId, templateId).isLocked();
    }

    private Template findByOrgAndId(UUID organizationId, UUID templateId) {
        return RepositoryHelper.findOrThrow(repository::findByIdAndOrganizationId, templateId, organizationId, "Template");
    }

    /**
     * Strips dangerous HTML elements, event handlers, and script URIs from template body.
     * Preserves safe formatting tags (div, p, h1-h6, strong, em, ul, li, table, etc.).
     */
    private String sanitizeHtml(String html) {
        if (html == null) return null;
        String result = DANGEROUS_TAGS.matcher(html).replaceAll("");
        result = EVENT_HANDLERS.matcher(result).replaceAll("");
        result = DANGEROUS_URIS.matcher(result).replaceAll("");
        return result;
    }

    private List<String> extractParams(String subject, String body) {
        List<String> params = new ArrayList<>();
        extractFromText(subject, params);
        extractFromText(body, params);
        return params;
    }

    private void extractFromText(String text, List<String> params) {
        if (text == null) return;
        Matcher matcher = PARAM_PATTERN.matcher(text);
        while (matcher.find()) {
            String param = matcher.group(1);
            if (!params.contains(param)) {
                params.add(param);
            }
        }
    }

    private String toJsonArray(List<String> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            return "[]";
        }
    }

    private TemplateResponse toResponse(Template t) {
        List<String> params = parseJsonArray(t.getParams());
        String parameterSetName = null;
        if (t.getParameterSetId() != null) {
            parameterSetName = parameterSetRepository.findById(t.getParameterSetId())
                    .map(ps -> ps.getName())
                    .orElse(null);
        }
        return new TemplateResponse(
                t.getId(), t.getName(), t.getSubject(), t.getBody(),
                params, t.getParameterSetId(), parameterSetName,
                t.isLocked(), t.getCreatedAt(), t.getUpdatedAt()
        );
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
