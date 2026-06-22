package com.postwerk.service;

import com.postwerk.TestFixtures;
import com.postwerk.dto.automation.ActivityEntry;
import com.postwerk.model.Automation;
import com.postwerk.model.Email;
import com.postwerk.model.EmailAutomationTrace;
import com.postwerk.model.EmailNodeTrace;
import com.postwerk.model.enums.NodeResultStatus;
import com.postwerk.model.enums.NodeType;
import com.postwerk.model.enums.TraceStatus;
import com.postwerk.repository.AutomationRepository;
import com.postwerk.repository.EmailAutomationTraceRepository;
import com.postwerk.service.impl.ActivityServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock private AutomationRepository automationRepository;
    @Mock private EmailAutomationTraceRepository traceRepository;

    private ActivityServiceImpl service;
    private final UUID userId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ActivityServiceImpl(automationRepository, traceRepository, new ObjectMapper());
    }

    @Test
    void getRecent_noAutomations_returnsEmptyWithoutQueryingTraces() {
        when(automationRepository.findByOrganizationId(orgId)).thenReturn(List.of());

        Page<ActivityEntry> page = service.getRecent(orgId, PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        verify(traceRepository, never()).findByAutomationIdInOrderByStartedAtDesc(any(), any());
    }

    @Test
    void getRecent_mapsTraceWithCategorizeReasoning() {
        Automation automation = TestFixtures.createAutomation(userId);
        when(automationRepository.findByOrganizationId(orgId)).thenReturn(List.of(automation));

        Email email = TestFixtures.createEmail(UUID.randomUUID());
        email.setSubject("Beschwerde");
        EmailNodeTrace node = EmailNodeTrace.builder()
                .id(UUID.randomUUID()).nodeId(UUID.randomUUID()).nodeType(NodeType.CATEGORIZE)
                .nodeLabel("Sortieren").executionOrder(0).resultStatus(NodeResultStatus.CATEGORIZED)
                .resultDetail("{\"categoryName\":\"Spam\",\"confidence\":62}")
                .executedAt(Instant.now()).build();
        EmailAutomationTrace trace = EmailAutomationTrace.builder()
                .id(UUID.randomUUID()).email(email).automationId(automation.getId())
                .automationName("Hotel").status(TraceStatus.SUCCESS)
                .startedAt(Instant.now()).nodeTraces(new ArrayList<>(List.of(node)))
                .build();

        when(traceRepository.findByAutomationIdInOrderByStartedAtDesc(any(), any()))
                .thenReturn(new PageImpl<>(List.of(trace)));

        Page<ActivityEntry> page = service.getRecent(orgId, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        ActivityEntry entry = page.getContent().get(0);
        assertThat(entry.automationName()).isEqualTo("Hotel");
        assertThat(entry.emailSubject()).isEqualTo("Beschwerde");
        assertThat(entry.steps()).hasSize(1);
        assertThat(entry.steps().get(0).summary()).isEqualTo("Spam (62%)");
    }
}
