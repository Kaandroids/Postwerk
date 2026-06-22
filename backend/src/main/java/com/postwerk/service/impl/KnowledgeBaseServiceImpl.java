package com.postwerk.service.impl;

import com.postwerk.dto.ImportResultDto;
import com.postwerk.dto.KbEntryRequest;
import com.postwerk.dto.KbEntryResponse;
import com.postwerk.dto.KbFieldRole;
import com.postwerk.dto.KbImportRequest;
import com.postwerk.dto.KnowledgeBaseRequest;
import com.postwerk.dto.KnowledgeBaseResponse;
import com.postwerk.dto.ParameterItemDto;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.AuditAction;
import com.postwerk.model.KnowledgeBase;
import com.postwerk.model.KnowledgeBaseEntry;
import com.postwerk.model.ParameterSet;
import com.postwerk.repository.KnowledgeBaseEntryRepository;
import com.postwerk.repository.KnowledgeBaseRepository;
import com.postwerk.repository.ParameterSetRepository;
import com.postwerk.service.AuditService;
import com.postwerk.service.KbContentBuilder;
import com.postwerk.service.KnowledgeBaseService;
import com.postwerk.util.RepositoryHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Default implementation of {@link KnowledgeBaseService}.
 *
 * <p>Persists the KB shell (schema borrowed from a {@code ParameterSet} + a per-field embed/keyword
 * overlay) and its entries. On every entry write it rebuilds the keyword {@code searchText} + the
 * embed-field {@code contentHash} and flags the row {@code embeddingDirty} so the async worker
 * (re-)embeds it off the request thread. CSV import either upserts on the KB's {@code uniqueField} or
 * fully replaces the entry set. See {@code doc/KNOWLEDGE_BASE_DESIGN.md}.</p>
 *
 * @since 1.0
 */
@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseRepository kbRepository;
    private final KnowledgeBaseEntryRepository entryRepository;
    private final ParameterSetRepository parameterSetRepository;
    private final AuditService auditService;
    private final KbContentBuilder contentBuilder;
    private final ObjectMapper objectMapper;

    public KnowledgeBaseServiceImpl(KnowledgeBaseRepository kbRepository,
                                    KnowledgeBaseEntryRepository entryRepository,
                                    ParameterSetRepository parameterSetRepository,
                                    AuditService auditService,
                                    KbContentBuilder contentBuilder,
                                    ObjectMapper objectMapper) {
        this.kbRepository = kbRepository;
        this.entryRepository = entryRepository;
        this.parameterSetRepository = parameterSetRepository;
        this.auditService = auditService;
        this.contentBuilder = contentBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public KnowledgeBaseResponse create(UUID organizationId, UUID actingUserId, KnowledgeBaseRequest request, String ipAddress) {
        validateConfig(organizationId, request.parameterSetId(), request.fieldRoles());
        KnowledgeBase kb = KnowledgeBase.builder()
                .organizationId(organizationId)
                .userId(actingUserId)
                .name(request.name())
                .description(request.description())
                .parameterSetId(request.parameterSetId())
                .fieldRoles(serializeRoles(request.fieldRoles()))
                .uniqueField(blankToNull(request.uniqueField()))
                .build();
        KnowledgeBase saved = kbRepository.save(kb);
        auditService.log(actingUserId, AuditAction.KNOWLEDGE_BASE_CREATED, "KnowledgeBase: " + saved.getName(), ipAddress);
        return toResponse(saved);
    }

    @Override
    public List<KnowledgeBaseResponse> listByOrg(UUID organizationId) {
        return kbRepository.findByOrganizationId(organizationId).stream().map(this::toResponse).toList();
    }

    @Override
    public KnowledgeBaseResponse getById(UUID organizationId, UUID id) {
        return toResponse(findKb(organizationId, id));
    }

    @Override
    @Transactional
    public KnowledgeBaseResponse update(UUID organizationId, UUID actingUserId, UUID id, KnowledgeBaseRequest request, String ipAddress) {
        KnowledgeBase kb = findKb(organizationId, id);
        validateConfig(organizationId, request.parameterSetId(), request.fieldRoles());

        String newRoles = serializeRoles(request.fieldRoles());
        // An embed/keyword config or schema change means every entry's search_text + embedding is stale.
        boolean rebuild = !Objects.equals(kb.getFieldRoles(), newRoles)
                || !Objects.equals(kb.getParameterSetId(), request.parameterSetId());

        kb.setName(request.name());
        kb.setDescription(request.description());
        kb.setParameterSetId(request.parameterSetId());
        kb.setFieldRoles(newRoles);
        kb.setUniqueField(blankToNull(request.uniqueField()));
        KnowledgeBase saved = kbRepository.save(kb);

        if (rebuild) {
            for (KnowledgeBaseEntry entry : entryRepository.findByKnowledgeBaseId(saved.getId())) {
                applyContent(saved, entry, entry.getData(), true);
                entryRepository.save(entry);
            }
        }
        auditService.log(actingUserId, AuditAction.KNOWLEDGE_BASE_UPDATED, "KnowledgeBase: " + saved.getName(), ipAddress);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID organizationId, UUID actingUserId, UUID id, String ipAddress) {
        KnowledgeBase kb = findKb(organizationId, id);
        entryRepository.deleteAllByKnowledgeBaseId(id);   // entries have no value without their KB
        kb.setDeletedAt(Instant.now());                   // KB soft-deleted (@SQLRestriction)
        kbRepository.save(kb);
        auditService.log(actingUserId, AuditAction.KNOWLEDGE_BASE_DELETED, "KnowledgeBase: " + kb.getName(), ipAddress);
    }

    @Override
    public List<KbEntryResponse> listEntries(UUID organizationId, UUID kbId) {
        KnowledgeBase kb = findKb(organizationId, kbId); // authorize org ownership
        if (kb.isHidden()) {
            return List.of(); // content-hidden KB (materialized from a PRIVATE marketplace listing)
        }
        return entryRepository.findByKnowledgeBaseId(kbId).stream().map(this::toEntryResponse).toList();
    }

    @Override
    @Transactional
    public KbEntryResponse addEntry(UUID organizationId, UUID actingUserId, UUID kbId, KbEntryRequest request) {
        KnowledgeBase kb = findKb(organizationId, kbId);
        KnowledgeBaseEntry entry = KnowledgeBaseEntry.builder()
                .knowledgeBaseId(kb.getId())
                .organizationId(kb.getOrganizationId())
                .build();
        applyContent(kb, entry, dataJson(request.data()), true);
        return toEntryResponse(entryRepository.save(entry));
    }

    @Override
    @Transactional
    public KbEntryResponse updateEntry(UUID organizationId, UUID actingUserId, UUID kbId, UUID entryId, KbEntryRequest request) {
        KnowledgeBase kb = findKb(organizationId, kbId);
        KnowledgeBaseEntry entry = findEntry(organizationId, kbId, entryId);
        applyContent(kb, entry, dataJson(request.data()), false);
        return toEntryResponse(entryRepository.save(entry));
    }

    @Override
    @Transactional
    public void deleteEntry(UUID organizationId, UUID kbId, UUID entryId) {
        entryRepository.delete(findEntry(organizationId, kbId, entryId));
    }

    @Override
    @Transactional
    public ImportResultDto importEntries(UUID organizationId, UUID actingUserId, UUID kbId, KbImportRequest request, String ipAddress) {
        KnowledgeBase kb = findKb(organizationId, kbId);
        List<Map<String, Object>> rows = request.rows() != null ? request.rows() : List.of();
        String uniqueField = blankToNull(kb.getUniqueField());

        if (uniqueField == null) {
            entryRepository.deleteAllByKnowledgeBaseId(kbId); // full replace
        }

        int imported = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            try {
                String json = dataJson(row);
                KnowledgeBaseEntry entry = null;
                if (uniqueField != null) {
                    Object key = row.get(uniqueField);
                    if (key != null) {
                        entry = entryRepository.findByUniqueFieldValue(kbId, uniqueField, String.valueOf(key));
                    }
                }
                if (entry == null) {
                    entry = KnowledgeBaseEntry.builder()
                            .knowledgeBaseId(kb.getId())
                            .organizationId(kb.getOrganizationId())
                            .build();
                }
                applyContent(kb, entry, json, false);
                entryRepository.save(entry);
                imported++;
            } catch (Exception e) {
                failed++;
                errors.add(e.getMessage());
            }
        }
        auditService.log(actingUserId, AuditAction.KNOWLEDGE_BASE_IMPORTED,
                "KnowledgeBase: " + kb.getName() + " (" + imported + " rows)", ipAddress);
        return new ImportResultDto(imported, failed, errors);
    }

    // ---- helpers ----------------------------------------------------------

    /**
     * Writes {@code dataJson} onto the entry and rebuilds its derived fields. Re-embedding is flagged
     * only when the embed-field content actually changed (hash differs) — or when {@code force} is set
     * (new entry / config rebuild) — so unchanged upserts keep their existing vector.
     */
    private void applyContent(KnowledgeBase kb, KnowledgeBaseEntry entry, String dataJson, boolean force) {
        String hash = contentBuilder.hash(contentBuilder.embedText(kb.getFieldRoles(), dataJson));
        boolean changed = force || !hash.equals(entry.getContentHash());
        entry.setData(dataJson);
        entry.setSearchText(contentBuilder.searchText(kb.getFieldRoles(), dataJson));
        if (changed) {
            entry.setContentHash(hash);
            entry.setEmbedding(null);
            entry.setEmbeddingDirty(true);
        }
    }

    private void validateConfig(UUID organizationId, UUID parameterSetId, Map<String, KbFieldRole> fieldRoles) {
        ParameterSet ps = parameterSetRepository.findByIdAndOrganizationId(parameterSetId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Parameter set not found in this organization"));
        Set<String> fields = paramFieldNames(ps);
        if (fieldRoles != null) {
            for (String key : fieldRoles.keySet()) {
                if (!fields.contains(key)) {
                    throw new IllegalArgumentException("Field '" + key + "' is not part of the parameter set");
                }
            }
        }
        boolean hasEmbed = fieldRoles != null && fieldRoles.values().stream().anyMatch(KbFieldRole::embed);
        if (!hasEmbed) {
            throw new IllegalArgumentException("KB_NO_EMBED_FIELD: at least one field must be marked embeddable");
        }
    }

    private Set<String> paramFieldNames(ParameterSet ps) {
        Set<String> names = new LinkedHashSet<>();
        try {
            String json = (ps.getParameters() == null || ps.getParameters().isBlank()) ? "[]" : ps.getParameters();
            List<ParameterItemDto> params = objectMapper.readValue(json, new TypeReference<>() {});
            for (ParameterItemDto p : params) {
                names.add(p.name());
                if (p.children() != null) {
                    for (ParameterItemDto child : p.children()) {
                        names.add(child.name());
                    }
                }
            }
        } catch (Exception ignored) {
            // malformed schema → empty set (validateConfig then rejects any declared role)
        }
        return names;
    }

    private KnowledgeBase findKb(UUID organizationId, UUID kbId) {
        return RepositoryHelper.findOrThrow(kbRepository::findByIdAndOrganizationId, kbId, organizationId, "KnowledgeBase");
    }

    private KnowledgeBaseEntry findEntry(UUID organizationId, UUID kbId, UUID entryId) {
        return entryRepository.findById(entryId)
                .filter(e -> e.getKnowledgeBaseId().equals(kbId) && e.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new ResourceNotFoundException("KnowledgeBaseEntry", entryId.toString()));
    }

    private KnowledgeBaseResponse toResponse(KnowledgeBase kb) {
        return new KnowledgeBaseResponse(
                kb.getId(), kb.getName(), kb.getDescription(), kb.getParameterSetId(),
                deserializeRoles(kb.getFieldRoles()), kb.getUniqueField(),
                entryRepository.countByKnowledgeBaseId(kb.getId()), kb.isLocked(),
                kb.getCreatedAt(), kb.getUpdatedAt());
    }

    private KbEntryResponse toEntryResponse(KnowledgeBaseEntry e) {
        return new KbEntryResponse(e.getId(), parseData(e.getData()), e.getCreatedAt(), e.getUpdatedAt());
    }

    private String serializeRoles(Map<String, KbFieldRole> roles) {
        if (roles == null || roles.isEmpty()) return "{}";
        try {
            return objectMapper.writeValueAsString(roles);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize field roles", e);
        }
    }

    private Map<String, KbFieldRole> deserializeRoles(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String dataJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data == null ? Map.of() : data);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize entry data", e);
        }
    }

    private Map<String, Object> parseData(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
