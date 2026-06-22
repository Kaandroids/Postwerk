package com.postwerk.service.impl;

import com.postwerk.model.KnowledgeBase;
import com.postwerk.model.KnowledgeBaseEntry;
import com.postwerk.repository.KnowledgeBaseEntryRepository;
import com.postwerk.repository.KnowledgeBaseRepository;
import com.postwerk.service.EmbeddingService;
import com.postwerk.service.KbContentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Asynchronous worker that (re-)embeds knowledge-base entries flagged {@code embeddingDirty}, off the
 * request thread (design D11). Each batch resolves its entries' owning KB to rebuild the embed text and
 * to attribute the Gemini embedding cost to the right org/user via {@link EmbeddingService}.
 *
 * <p>Embedding is deliberately performed outside any wrapping transaction so a slow Gemini call does
 * not hold a DB transaction open; each entry is persisted independently and a failed embedding simply
 * leaves the row dirty for the next tick.</p>
 *
 * @since 1.0
 */
@Component
public class KnowledgeBaseEmbeddingWorker {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseEmbeddingWorker.class);

    /** Max entries embedded per tick — bounds Gemini call volume + quota burn per run. */
    private static final int BATCH_SIZE = 50;

    private final KnowledgeBaseEntryRepository entryRepository;
    private final KnowledgeBaseRepository kbRepository;
    private final KbContentBuilder contentBuilder;
    private final EmbeddingService embeddingService;

    public KnowledgeBaseEmbeddingWorker(KnowledgeBaseEntryRepository entryRepository,
                                        KnowledgeBaseRepository kbRepository,
                                        KbContentBuilder contentBuilder,
                                        EmbeddingService embeddingService) {
        this.entryRepository = entryRepository;
        this.kbRepository = kbRepository;
        this.contentBuilder = contentBuilder;
        this.embeddingService = embeddingService;
    }

    @Scheduled(
            initialDelayString = "${app.kb.reembed-initial-delay-ms:20000}",
            fixedDelayString = "${app.kb.reembed-interval-ms:15000}")
    public void processDirty() {
        List<KnowledgeBaseEntry> dirty =
                entryRepository.findByEmbeddingDirtyTrueOrderByCreatedAtAsc(PageRequest.of(0, BATCH_SIZE));
        if (dirty.isEmpty()) {
            return;
        }
        Map<UUID, KnowledgeBase> kbCache = new HashMap<>();
        for (KnowledgeBaseEntry entry : dirty) {
            try {
                KnowledgeBase kb = kbCache.computeIfAbsent(entry.getKnowledgeBaseId(),
                        id -> kbRepository.findById(id).orElse(null));
                if (kb == null) {
                    // KB deleted out from under the entry — clear the flag so we don't spin on it.
                    entry.setEmbeddingDirty(false);
                    entryRepository.save(entry);
                    continue;
                }
                String text = contentBuilder.embedText(kb.getFieldRoles(), entry.getData());
                if (text.isBlank()) {
                    entry.setEmbeddingDirty(false);
                    entryRepository.save(entry);
                    continue;
                }
                float[] vector = embeddingService.embed(kb.getOrganizationId(), kb.getUserId(), text);
                entry.setEmbedding(vector);
                entry.setEmbeddingDirty(false);
                entryRepository.save(entry);
            } catch (Exception e) {
                // Leave the row dirty; it retries next tick (quota/circuit-breaker/transient errors).
                log.warn("KB entry {} embedding failed: {}", entry.getId(), e.getMessage());
            }
        }
    }
}
