package com.postwerk.service;

import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.PendingAction;
import com.postwerk.model.enums.ApprovalStatus;
import com.postwerk.model.enums.NodeType;
import com.postwerk.repository.PendingActionRepository;
import com.postwerk.service.impl.PendingActionServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingActionServiceTest {

    @Mock private PendingActionRepository repository;
    @Mock private AutomationExecutorService executor;
    @Mock private com.postwerk.service.CategoryService categoryService;
    @Mock private com.postwerk.repository.EmailRepository emailRepository;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private PendingActionServiceImpl service;
    private final UUID userId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PendingActionServiceImpl(repository, new ObjectMapper(), executor, categoryService,
                emailRepository, eventPublisher);
    }

    private PendingAction pending() {
        return PendingAction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .automationId(UUID.randomUUID())
                .nodeId(UUID.randomUUID())
                .nodeType(NodeType.EMAIL_ACTION)
                .nodeLabel("Reply")
                .actionDetail("{\"to\":\"x@y.com\"}")
                .status(ApprovalStatus.PENDING)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void approve_performsActionAndMarksApproved() {
        PendingAction a = pending();
        when(repository.findByIdAndOrganizationId(a.getId(), orgId)).thenReturn(Optional.of(a));
        when(executor.executeApprovedAction(a)).thenReturn(true);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resp = service.approve(orgId, userId, a.getId());

        verify(executor).executeApprovedAction(a);
        assertThat(a.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(a.getDecidedBy()).isEqualTo(userId);
        assertThat(a.getDecidedAt()).isNotNull();
        assertThat(resp.status()).isEqualTo("APPROVED");
    }

    @Test
    void approve_alreadyDecided_throwsAndDoesNotExecute() {
        PendingAction a = pending();
        a.setStatus(ApprovalStatus.REJECTED);
        when(repository.findByIdAndOrganizationId(a.getId(), orgId)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.approve(orgId, userId, a.getId()))
                .isInstanceOf(IllegalStateException.class);
        verify(executor, never()).executeApprovedAction(any());
    }

    @Test
    void approve_missing_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndOrganizationId(id, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(orgId, userId, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reclassify_teachesCorrectCategoryAndRejects() {
        PendingAction a = pending();
        a.setEmailId(UUID.randomUUID());
        a.setContextSnapshot("{\"category.id\":\"cat-1\",\"category.name\":\"Spam\",\"category.confidence\":62}");
        UUID correctCategory = UUID.randomUUID();

        when(repository.findByIdAndOrganizationId(a.getId(), orgId)).thenReturn(Optional.of(a));
        when(emailRepository.findById(a.getEmailId()))
                .thenReturn(Optional.of(com.postwerk.TestFixtures.createEmail(UUID.randomUUID())));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resp = service.reclassify(orgId, userId, a.getId(), correctCategory);

        verify(categoryService).addLearningExample(org.mockito.ArgumentMatchers.eq(orgId),
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq(correctCategory), org.mockito.ArgumentMatchers.anyString());
        assertThat(a.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(resp.triggerCategory()).isNotNull();
        assertThat(resp.triggerCategory().name()).isEqualTo("Spam");
        verify(executor, never()).executeApprovedAction(any());
    }

    @Test
    void reject_marksRejectedWithoutExecuting() {
        PendingAction a = pending();
        when(repository.findByIdAndOrganizationId(a.getId(), orgId)).thenReturn(Optional.of(a));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resp = service.reject(orgId, userId, a.getId(), "not relevant to us");

        assertThat(a.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(resp.decisionNote()).isEqualTo("not relevant to us");
        verify(executor, never()).executeApprovedAction(any());
    }
}
