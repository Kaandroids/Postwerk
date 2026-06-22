package com.postwerk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link KbContentBuilder} — the embed/keyword text derivation + content hash used by
 * both the KB service (write path) and the embedding worker. Pure (no DB / no Gemini).
 */
class KbContentBuilderTest {

    private final KbContentBuilder builder = new KbContentBuilder(new ObjectMapper());

    /** kod = keyword-only, isim = embed+keyword, aciklama = embed-only. */
    private static final String ROLES = """
            {"kod":{"embed":false,"keyword":true},
             "isim":{"embed":true,"keyword":true},
             "aciklama":{"embed":true,"keyword":false}}""";

    private static final String DATA = """
            {"kod":"4930","isim":"Bürobedarf","aciklama":"Büromaterial"}""";

    @Test
    void embedText_joinsOnlyEmbedFieldsInOrder() {
        assertThat(builder.embedText(ROLES, DATA)).isEqualTo("Bürobedarf Büromaterial");
    }

    @Test
    void searchText_joinsOnlyKeywordFieldsInOrder() {
        assertThat(builder.searchText(ROLES, DATA)).isEqualTo("4930 Bürobedarf");
    }

    @Test
    void hash_isStableAndContentSensitive() {
        assertThat(builder.hash("Bürobedarf")).isEqualTo(builder.hash("Bürobedarf"));
        assertThat(builder.hash("Bürobedarf")).isNotEqualTo(builder.hash("Erlöse"));
        assertThat(builder.hash("anything")).hasSize(64); // SHA-256 hex
    }

    @Test
    void handlesBlankOrMissingGracefully() {
        assertThat(builder.embedText("{}", "{}")).isEmpty();
        assertThat(builder.embedText(null, null)).isEmpty();
        assertThat(builder.searchText(ROLES, "{}")).isEmpty(); // fields absent in data
    }
}
