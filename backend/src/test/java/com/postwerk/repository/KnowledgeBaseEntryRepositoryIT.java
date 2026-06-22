package com.postwerk.repository;

import com.postwerk.config.TestContainersConfig;
import com.postwerk.model.KnowledgeBase;
import com.postwerk.model.KnowledgeBaseEntry;
import com.postwerk.model.Organization;
import com.postwerk.model.ParameterSet;
import com.postwerk.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the knowledge-base native SQL against a real pgvector Postgres:
 * cosine nearest-neighbour ({@code embedding <=> cast(:v as vector)}), Postgres full-text
 * ({@code to_tsvector @@ plainto_tsquery}), the JSONB unique-key lookup ({@code data ->> :field}),
 * the dirty-queue feed, and the bulk delete. Booting under Flyway also verifies the V82 migration
 * (vector(3072) column + GIN expression index) applies cleanly.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@Tag("integration")
class KnowledgeBaseEntryRepositoryIT {

    private static final int DIM = 3072;

    @Autowired private TestEntityManager em;
    @Autowired private KnowledgeBaseRepository kbRepository;
    @Autowired private KnowledgeBaseEntryRepository entryRepository;

    private UUID orgId;
    private KnowledgeBase kb;

    @BeforeEach
    void setUp() {
        Organization org = em.persistAndFlush(Organization.builder().name("Test Org").build());
        orgId = org.getId();
        User user = em.persistAndFlush(User.builder()
                .email("kb-it@test.local").passwordHash("x").fullName("KB IT").build());
        ParameterSet ps = em.persistAndFlush(ParameterSet.builder()
                .userId(user.getId()).organizationId(orgId).name("SKR schema").parameters("[]").build());
        kb = kbRepository.saveAndFlush(KnowledgeBase.builder()
                .organizationId(orgId).userId(user.getId()).name("SKR 03")
                .parameterSetId(ps.getId())
                .fieldRoles("{\"isim\":{\"embed\":true,\"keyword\":true}}")
                .build());
    }

    @Test
    void findClosestByEmbedding_ordersByCosineDistance() {
        KnowledgeBaseEntry a = persistEntry("{\"kod\":\"4930\"}", vec(0, 1f), "Bürobedarf");
        persistEntry("{\"kod\":\"8400\"}", vec(1, 1f), "Erlöse");

        // Query vector points at slot 0 → entry A (also slot 0) must rank first.
        List<Object[]> rows = entryRepository.findClosestByEmbedding(kb.getId(), vecStr(vec(0, 0.9f)), 5);

        assertThat(rows).hasSize(2);
        assertThat((UUID) rows.get(0)[0]).isEqualTo(a.getId());
    }

    @Test
    void fullTextSearch_matchesKeywordText() {
        persistEntry("{\"kod\":\"4930\"}", vec(0, 1f), "Bürobedarf Büromaterial");
        persistEntry("{\"kod\":\"8400\"}", vec(1, 1f), "Erlöse Umsatz");

        List<Object[]> rows = entryRepository.fullTextSearch(kb.getId(), "Bürobedarf", 5);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[1].toString()).contains("4930");
    }

    @Test
    void findByUniqueFieldValue_resolvesJsonbKey() {
        KnowledgeBaseEntry a = persistEntry("{\"kod\":\"4930\",\"isim\":\"Bürobedarf\"}", vec(0, 1f), "Bürobedarf");
        persistEntry("{\"kod\":\"8400\"}", vec(1, 1f), "Erlöse");

        KnowledgeBaseEntry found = entryRepository.findByUniqueFieldValue(kb.getId(), "kod", "4930");

        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(a.getId());
        assertThat(entryRepository.findByUniqueFieldValue(kb.getId(), "kod", "0000")).isNull();
    }

    @Test
    void dirtyQueueFeed_and_bulkDelete() {
        persistEntry("{\"kod\":\"4930\"}", vec(0, 1f), "Bürobedarf"); // dirty = false
        KnowledgeBaseEntry dirty = entryRepository.saveAndFlush(KnowledgeBaseEntry.builder()
                .knowledgeBaseId(kb.getId()).organizationId(orgId)
                .data("{\"kod\":\"8400\"}").embeddingDirty(true).build());

        assertThat(entryRepository.findByEmbeddingDirtyTrueOrderByCreatedAtAsc(PageRequest.of(0, 10)))
                .extracting(KnowledgeBaseEntry::getId)
                .containsExactly(dirty.getId());

        assertThat(entryRepository.countByKnowledgeBaseId(kb.getId())).isEqualTo(2);
        entryRepository.deleteAllByKnowledgeBaseId(kb.getId());
        em.flush();
        em.clear();
        assertThat(entryRepository.countByKnowledgeBaseId(kb.getId())).isZero();
    }

    // ── helpers ────────────────────────────────────────────────

    private KnowledgeBaseEntry persistEntry(String dataJson, float[] embedding, String searchText) {
        return entryRepository.saveAndFlush(KnowledgeBaseEntry.builder()
                .knowledgeBaseId(kb.getId()).organizationId(orgId)
                .data(dataJson).embedding(embedding).searchText(searchText)
                .embeddingDirty(false).build());
    }

    /** A 3072-dim vector with {@code value} at {@code slot}, zeros elsewhere (matches vector(3072)). */
    private static float[] vec(int slot, float value) {
        float[] v = new float[DIM];
        v[slot] = value;
        return v;
    }

    private static String vecStr(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
