package com.postwerk.service;

import com.postwerk.dto.ImportResultDto;
import com.postwerk.dto.KbEntryRequest;
import com.postwerk.dto.KbEntryResponse;
import com.postwerk.dto.KbImportRequest;
import com.postwerk.dto.KnowledgeBaseRequest;
import com.postwerk.dto.KnowledgeBaseResponse;

import java.util.List;
import java.util.UUID;

/**
 * Manages org-scoped knowledge bases and their entries (doc/KNOWLEDGE_BASE_DESIGN.md).
 *
 * <p>A KB borrows its field schema from a {@code ParameterSet} and overlays per-field
 * embed/keyword roles. Entries are filled instances; saving an entry (re)builds its keyword
 * search text + content hash and flags it for asynchronous (re-)embedding. Scoped by
 * {@code organizationId} (#4); {@code actingUserId} is threaded for audit + embedding quota.</p>
 *
 * @since 1.0
 */
public interface KnowledgeBaseService {

    KnowledgeBaseResponse create(UUID organizationId, UUID actingUserId, KnowledgeBaseRequest request, String ipAddress);

    List<KnowledgeBaseResponse> listByOrg(UUID organizationId);

    KnowledgeBaseResponse getById(UUID organizationId, UUID id);

    KnowledgeBaseResponse update(UUID organizationId, UUID actingUserId, UUID id, KnowledgeBaseRequest request, String ipAddress);

    void delete(UUID organizationId, UUID actingUserId, UUID id, String ipAddress);

    List<KbEntryResponse> listEntries(UUID organizationId, UUID kbId);

    KbEntryResponse addEntry(UUID organizationId, UUID actingUserId, UUID kbId, KbEntryRequest request);

    KbEntryResponse updateEntry(UUID organizationId, UUID actingUserId, UUID kbId, UUID entryId, KbEntryRequest request);

    void deleteEntry(UUID organizationId, UUID kbId, UUID entryId);

    ImportResultDto importEntries(UUID organizationId, UUID actingUserId, UUID kbId, KbImportRequest request, String ipAddress);
}
