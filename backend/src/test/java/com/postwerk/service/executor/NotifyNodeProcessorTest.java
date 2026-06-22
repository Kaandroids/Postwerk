package com.postwerk.service.executor;

import com.postwerk.dto.automation.NodeMock;
import com.postwerk.model.AutomationNode;
import com.postwerk.model.Membership;
import com.postwerk.model.Template;
import com.postwerk.model.enums.MembershipStatus;
import com.postwerk.model.enums.NodeResultStatus;
import com.postwerk.model.enums.NotificationType;
import com.postwerk.repository.MembershipRepository;
import com.postwerk.repository.TemplateRepository;
import com.postwerk.service.NewNotification;
import com.postwerk.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotifyNodeProcessorTest {

    @Mock private NotificationService notificationService;
    @Mock private MembershipRepository membershipRepository;
    @Mock private TemplateRepository templateRepository;

    private NotifyNodeProcessor processor;
    private final ObjectMapper mapper = new ObjectMapper();

    private final UUID orgId = UUID.randomUUID();
    private final UUID recipientId = UUID.randomUUID();
    private final UUID nodeId = UUID.randomUUID();
    private final UUID runnerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        processor = new NotifyNodeProcessor(mapper, new VariableResolver(), notificationService,
                membershipRepository, templateRepository);
    }

    private AutomationNode node(Map<String, Object> config) {
        AutomationNode n = org.mockito.Mockito.mock(AutomationNode.class);
        try {
            when(n.getConfig()).thenReturn(mapper.writeValueAsString(config));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        org.mockito.Mockito.lenient().when(n.getId()).thenReturn(nodeId);
        org.mockito.Mockito.lenient().when(n.getNodeKey()).thenReturn(null);
        return n;
    }

    private ExecutionContext context(boolean dryRun) {
        return new ExecutionContext(null, null, dryRun).withOrganizationId(orgId);
    }

    private void recipientIsActiveMember() {
        when(membershipRepository.findByOrganizationIdAndUserId(orgId, recipientId))
                .thenReturn(Optional.of(Membership.builder().status(MembershipStatus.ACTIVE).build()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void live_validUser_createsNotificationAndInjectsSent() {
        recipientIsActiveMember();
        AutomationNode n = node(Map.of("recipientType", "USER", "recipientUserId", recipientId.toString(),
                "title", "Order", "message", "New order arrived", "severity", "WARNING"));

        NodeProcessorResult result = processor.process(n, context(false), runnerId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.EXECUTED);
        assertThat(result.activeHandles()).containsExactly("success");
        ExecutionContext enriched = result.contextByHandle().get("success");
        assertThat(enriched.getVariable("notify_" + nodeId + ".sent")).isEqualTo(true);
        assertThat(enriched.getVariable("notify_" + nodeId + ".recipientCount")).isEqualTo(1);

        ArgumentCaptor<Collection<UUID>> recipients = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<NewNotification> spec = ArgumentCaptor.forClass(NewNotification.class);
        verify(notificationService).create(recipients.capture(), spec.capture());
        assertThat(recipients.getValue()).containsExactly(recipientId);
        assertThat(spec.getValue().type()).isEqualTo(NotificationType.NOTIFY_NODE);
        assertThat(spec.getValue().params()).containsEntry("message", "New order arrived");
    }

    @Test
    @SuppressWarnings("unchecked")
    void live_template_usesTemplateSubjectAndFlattenedBody() {
        recipientIsActiveMember();
        UUID templateId = UUID.randomUUID();
        Template t = org.mockito.Mockito.mock(Template.class);
        when(t.getSubject()).thenReturn("Order alert");
        when(t.getBody()).thenReturn("<p>Hello <b>world</b></p>");
        when(templateRepository.findByIdAndOrganizationId(templateId, orgId)).thenReturn(Optional.of(t));
        AutomationNode n = node(Map.of("recipientType", "USER", "recipientUserId", recipientId.toString(),
                "templateId", templateId.toString()));

        NodeProcessorResult result = processor.process(n, context(false), runnerId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.EXECUTED);
        ArgumentCaptor<NewNotification> spec = ArgumentCaptor.forClass(NewNotification.class);
        verify(notificationService).create(any(), spec.capture());
        assertThat(spec.getValue().params()).containsEntry("title", "Order alert");
        assertThat(spec.getValue().params()).containsEntry("message", "Hello world");
    }

    @Test
    void dryRun_doesNotCreate() {
        recipientIsActiveMember();
        AutomationNode n = node(Map.of("recipientType", "USER", "recipientUserId", recipientId.toString(),
                "message", "hi"));

        NodeProcessorResult result = processor.process(n, context(true), runnerId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.SIMULATED);
        assertThat(result.activeHandles()).containsExactly("success");
        verify(notificationService, never()).create(any(), any());
    }

    @Test
    void userWithoutRecipient_routesFail() {
        AutomationNode n = node(Map.of("recipientType", "USER", "message", "hi"));

        NodeProcessorResult result = processor.process(n, context(false), runnerId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.ERROR);
        assertThat(result.activeHandles()).containsExactly("fail");
        verify(notificationService, never()).create(any(), any());
    }

    @Test
    void noMessage_routesFail() {
        AutomationNode n = node(Map.of("recipientType", "ADMINS"));

        NodeProcessorResult result = processor.process(n, context(false), runnerId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.ERROR);
        assertThat(result.activeHandles()).containsExactly("fail");
        verify(notificationService, never()).create(any(), any());
    }

    @Test
    void mockForceError_routesFail() {
        NodeMock mock = org.mockito.Mockito.mock(NodeMock.class);
        when(mock.isMock()).thenReturn(true);
        when(mock.shouldForceError()).thenReturn(true);
        AutomationNode n = node(Map.of("recipientType", "ADMINS", "message", "hi"));
        ExecutionContext ctx = context(false).withMocks(Map.of(nodeId.toString(), mock));

        NodeProcessorResult result = processor.process(n, ctx, runnerId);

        assertThat(result.status()).isEqualTo(NodeResultStatus.ERROR);
        assertThat(result.activeHandles()).containsExactly("fail");
        verify(notificationService, never()).create(any(), any());
    }
}
