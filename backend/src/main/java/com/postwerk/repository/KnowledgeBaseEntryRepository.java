package com.postwerk.repository;

import com.postwerk.model.KnowledgeBaseEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link KnowledgeBaseEntry} entities.
 *
 * <p>Supports hybrid retrieval — pgvector nearest-neighbour ({@link #findClosestByEmbedding})
 * and Postgres full-text ({@link #fullTextSearch}) — plus the dirty-queue feed for the async
 * (re-)embedding worker. All search methods are KB-scoped; the caller resolves the KB by org first.</p>
 *
 * @since 1.0
 */
public interface KnowledgeBaseEntryRepository extends JpaRepository<KnowledgeBaseEntry, UUID> {

    List<KnowledgeBaseEntry> findByKnowledgeBaseId(UUID knowledgeBaseId);

    long countByKnowledgeBaseId(UUID knowledgeBaseId);

    /** Bulk delete every entry of a KB — used by full-replace re-import. */
    @Modifying
    @Query("DELETE FROM KnowledgeBaseEntry e WHERE e.knowledgeBaseId = :kbId")
    int deleteAllByKnowledgeBaseId(@Param("kbId") UUID kbId);

    /** Look up a single entry by the KB's natural-key field value — used by upsert re-import. */
    @Query(value = """
            SELECT * FROM knowledge_base_entries
            WHERE knowledge_base_id = :kbId AND data ->> :field = :value
            LIMIT 1
            """, nativeQuery = true)
    KnowledgeBaseEntry findByUniqueFieldValue(@Param("kbId") UUID kbId,
                                              @Param("field") String field,
                                              @Param("value") String value);

    /** Rows awaiting (re-)embedding, oldest first, for the async batch worker. */
    List<KnowledgeBaseEntry> findByEmbeddingDirtyTrueOrderByCreatedAtAsc(Pageable pageable);

    /** Vector nearest-neighbour over a KB's entries (cosine distance). Mirrors {@code CategoryRepository}. */
    @Query(value = """
            SELECT id, data, embedding <=> cast(:queryVector as vector) AS distance
            FROM knowledge_base_entries
            WHERE knowledge_base_id = :kbId AND embedding IS NOT NULL
            ORDER BY embedding <=> cast(:queryVector as vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findClosestByEmbedding(@Param("kbId") UUID kbId,
                                          @Param("queryVector") String queryVector,
                                          @Param("limit") int limit);

    /** Postgres full-text search over a KB's keyword fields ({@code simple} config). */
    @Query(value = """
            SELECT id, data,
                   ts_rank(to_tsvector('simple', coalesce(search_text, '')),
                           plainto_tsquery('simple', :q)) AS rank
            FROM knowledge_base_entries
            WHERE knowledge_base_id = :kbId
              AND to_tsvector('simple', coalesce(search_text, '')) @@ plainto_tsquery('simple', :q)
            ORDER BY rank DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> fullTextSearch(@Param("kbId") UUID kbId,
                                  @Param("q") String q,
                                  @Param("limit") int limit);
}
