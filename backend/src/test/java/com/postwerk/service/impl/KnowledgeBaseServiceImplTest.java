package com.postwerk.service.impl;

import com.postwerk.dto.KbFieldRole;
import com.postwerk.dto.KnowledgeBaseRequest;
import com.postwerk.dto.KnowledgeBaseResponse;
import com.postwerk.model.KnowledgeBase;
import com.postwerk.model.ParameterSet;
import com.postwerk.repository.KnowledgeBaseEntryRepository;
import com.postwerk.repository.KnowledgeBaseRepository;
import com.postwerk.repository.ParameterSetRepository;
import com.postwerk.service.AuditService;
import com.postwerk.service.KbContentBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KnowledgeBaseServiceImpl} KB-config validation: at least one embed field
 * (KB_NO_EMBED_FIELD) and field-role keys must exist in the borrowed parameter set.
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceImplTest {

    @Mock private KnowledgeBaseRepository kbRepository;
    @Mock private KnowledgeBaseEntryRepository entryRepository;
    @Mock private ParameterSetRepository parameterSetRepository;
    @Mock private AuditService auditService;

    private KnowledgeBaseServiceImpl service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID psId = UUID.randomUUID();
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new KnowledgeBaseServiceImpl(
                kbRepository, entryRepository, parameterSetRepository, auditService, new KbContentBuilder(om), om);
    }

    private ParameterSet ps() {
        return ParameterSet.builder().id(psId).organizationId(orgId).userId(userId).name("schema")
                .parameters("[{\"name\":\"isim\",\"type\":\"TEXT\"},{\"name\":\"kod\",\"type\":\"TEXT\"}]")
                .build();
    }

    @Test
    void create_rejectsWhenNoEmbedField() {
        when(parameterSetRepository.findByIdAndOrganizationId(psId, orgId)).thenReturn(Optional.of(ps()));
        var req = new KnowledgeBaseRequest("KB", null, psId, Map.of("kod", new KbFieldRole(false, true)), null);

        assertThatThrownBy(() -> service.create(orgId, userId, req, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("KB_NO_EMBED_FIELD");
    }

    @Test
    void create_rejectsUnknownField() {
        when(parameterSetRepository.findByIdAndOrganizationId(psId, orgId)).thenReturn(Optional.of(ps()));
        var req = new KnowledgeBaseRequest("KB", null, psId, Map.of("ghost", new KbFieldRole(true, false)), null);

        assertThatThrownBy(() -> service.create(orgId, userId, req, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not part of the parameter set");
    }

    @Test
    void create_succeedsWithEmbedField() {
        when(parameterSetRepository.findByIdAndOrganizationId(psId, orgId)).thenReturn(Optional.of(ps()));
        when(kbRepository.save(any())).thenAnswer(inv -> {
            KnowledgeBase k = inv.getArgument(0);
            k.setId(UUID.randomUUID());
            return k;
        });
        var req = new KnowledgeBaseRequest("KB", "desc", psId,
                Map.of("isim", new KbFieldRole(true, true)), "kod");

        KnowledgeBaseResponse resp = service.create(orgId, userId, req, null);

        assertThat(resp.fieldRoles()).containsKey("isim");
        assertThat(resp.uniqueField()).isEqualTo("kod");
    }
}
