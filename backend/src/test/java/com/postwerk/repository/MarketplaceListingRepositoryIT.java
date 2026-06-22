package com.postwerk.repository;

import com.postwerk.config.TestContainersConfig;
import com.postwerk.model.MarketplaceListing;
import com.postwerk.model.Organization;
import com.postwerk.model.enums.ListingStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link MarketplaceListingRepository#discover} against a real Postgres.
 * Guards the {@code CAST(:q AS string)} fix: a null {@code q} must not let Postgres infer the
 * bind parameter as {@code bytea} (which previously broke {@code LOWER(... || :q || ...)}).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@Tag("integration")
class MarketplaceListingRepositoryIT {

    @Autowired
    private MarketplaceListingRepository repository;

    @Autowired
    private TestEntityManager em;

    private MarketplaceListing persistListing(String name, String tagline, String category) {
        // organization_id is a real FK → persist an owning org first.
        Organization org = Organization.builder()
                .name("Org " + name).ownerUserId(UUID.randomUUID()).personal(false).build();
        em.persist(org);
        MarketplaceListing l = MarketplaceListing.builder()
                .organizationId(org.getId())
                .automationId(UUID.randomUUID())
                .authorId(UUID.randomUUID())
                .name(name)
                .tagline(tagline)
                .category(category)
                .kbSharePolicy("{\"default\":\"SCHEMA_ONLY\"}")
                .status(ListingStatus.PUBLISHED)
                .build();
        return repository.saveAndFlush(l);
    }

    @Test
    void discover_withNullQuery_returnsAllPublished() {
        persistListing("Lead Router", "Route inbound leads", "sales");
        persistListing("Invoice Sentinel", "File invoices", "finance");

        List<MarketplaceListing> results = repository.discover(null, null, "popular");

        assertThat(results).extracting(MarketplaceListing::getName)
                .contains("Lead Router", "Invoice Sentinel");
    }

    @Test
    void discover_withCategory_filtersByCategory() {
        persistListing("Lead Router", "Route inbound leads", "sales");
        persistListing("Invoice Sentinel", "File invoices", "finance");

        List<MarketplaceListing> results = repository.discover("finance", null, "new");

        assertThat(results).extracting(MarketplaceListing::getName)
                .containsExactly("Invoice Sentinel");
    }

    @Test
    void discover_withQuery_matchesNameOrTaglineCaseInsensitively() {
        persistListing("Lead Router", "Route inbound leads", "sales");
        persistListing("Invoice Sentinel", "File invoices", "finance");

        List<MarketplaceListing> byName = repository.discover(null, "lead", "rating");
        assertThat(byName).extracting(MarketplaceListing::getName).containsExactly("Lead Router");

        List<MarketplaceListing> byTagline = repository.discover(null, "INVOICES", "installs");
        assertThat(byTagline).extracting(MarketplaceListing::getName).containsExactly("Invoice Sentinel");
    }
}
