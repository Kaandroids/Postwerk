package com.postwerk.service;

import com.postwerk.TestFixtures;
import com.postwerk.dto.EmailCategoryItem;
import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.repository.EmailAccountFolderRepository;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.repository.EmailRepository;
import com.postwerk.repository.EmailAutomationTraceRepository;
import com.postwerk.repository.EmailListView;
import com.postwerk.service.impl.AutomationExecutorServiceImpl;
import com.postwerk.service.impl.EmailServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private EmailRepository emailRepository;
    @Mock private EmailAccountRepository emailAccountRepository;
    @Mock private EmailAccountFolderRepository folderRepository;
    @Mock private EmailSyncService emailSyncService;
    @Mock private EmailAutomationTraceService traceService;
    @Mock private EmailAutomationTraceRepository traceRepository;
    @Mock private AutomationExecutorServiceImpl automationExecutorService;
    @Mock private AuditService auditService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private EmailServiceImpl emailService;

    private UUID orgId;
    private UUID userId;
    private UUID accountId;
    private EmailAccount account;
    private Email email;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        userId = UUID.randomUUID();
        account = TestFixtures.createEmailAccount(userId);
        accountId = account.getId();
        email = TestFixtures.createEmail(accountId);
    }

    @Test
    void list_paginationWorks() {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId))
                .thenReturn(Optional.of(account));
        when(emailRepository.findFiltered(eq(accountId), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<EmailListView>(List.of(), PageRequest.of(0, 20), 1));

        var page = emailService.list(orgId, accountId, null, null, null, null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(page).isNotNull();
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void list_withFilters_appliesAll() {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId))
                .thenReturn(Optional.of(account));
        when(folderRepository.findByEmailAccountIdOrderByRoleAscNameAsc(accountId))
                .thenReturn(List.of());
        when(emailRepository.findFiltered(eq(accountId), eq("INBOX"), eq("test"), eq(true), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<EmailListView>(List.of(), PageRequest.of(0, 20), 0));

        var page = emailService.list(orgId, accountId, "INBOX", "test", true, null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isZero();
        verify(emailRepository).findFiltered(eq(accountId), eq("INBOX"), eq("test"), eq(true), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void getById_returnsEmail() {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId))
                .thenReturn(Optional.of(account));
        when(emailRepository.findByIdAndEmailAccountId(email.getId(), accountId))
                .thenReturn(Optional.of(email));
        when(traceService.getTracesByEmailId(email.getId())).thenReturn(List.of());

        var response = emailService.getById(orgId, userId, accountId, email.getId(), "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.subject()).isEqualTo("Test Email Subject");
    }

    @Test
    void getById_wrongAccount_throws() {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId))
                .thenReturn(Optional.of(account));
        when(emailRepository.findByIdAndEmailAccountId(any(), eq(accountId)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailService.getById(orgId, userId, accountId, UUID.randomUUID(), "127.0.0.1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markRead_togglesFlag() {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId))
                .thenReturn(Optional.of(account));
        when(emailRepository.findByIdAndEmailAccountId(email.getId(), accountId))
                .thenReturn(Optional.of(email));
        when(emailRepository.save(any(Email.class))).thenReturn(email);
        when(traceService.getTracesByEmailId(email.getId())).thenReturn(List.of());

        assertThat(email.isRead()).isFalse();

        emailService.markRead(orgId, accountId, email.getId(), true);

        assertThat(email.isRead()).isTrue();
    }

    @Test
    void toggleStar_flipsState() {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId))
                .thenReturn(Optional.of(account));
        when(emailRepository.findByIdAndEmailAccountId(email.getId(), accountId))
                .thenReturn(Optional.of(email));
        when(emailRepository.save(any(Email.class))).thenReturn(email);
        when(traceService.getTracesByEmailId(email.getId())).thenReturn(List.of());

        assertThat(email.isStarred()).isFalse();

        emailService.toggleStar(orgId, accountId, email.getId());

        assertThat(email.isStarred()).isTrue();
    }

    @Test
    void assignCategories_serializesJson() throws Exception {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId))
                .thenReturn(Optional.of(account));
        when(emailRepository.findByIdAndEmailAccountId(email.getId(), accountId))
                .thenReturn(Optional.of(email));
        when(emailRepository.save(any(Email.class))).thenReturn(email);
        when(traceService.getTracesByEmailId(email.getId())).thenReturn(List.of());

        var categories = List.of(
                new EmailCategoryItem(UUID.randomUUID(), "Work", "#3b82f6")
        );
        when(objectMapper.writeValueAsString(categories)).thenReturn("[{\"id\":\"...\",\"name\":\"Work\"}]");

        emailService.assignCategories(orgId, accountId, email.getId(), categories);

        assertThat(email.getCategories()).isNotNull();
    }

    @Test
    void reprocess_deletesOldTraces_resetsProcessed() {
        email.setProcessed(true);
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId))
                .thenReturn(Optional.of(account));
        when(emailRepository.findByIdAndEmailAccountId(email.getId(), accountId))
                .thenReturn(Optional.of(email));
        when(emailRepository.save(any(Email.class))).thenReturn(email);
        when(traceService.getTracesByEmailId(email.getId())).thenReturn(List.of());

        emailService.reprocess(orgId, accountId, email.getId());

        assertThat(email.isProcessed()).isFalse();
        verify(traceRepository).deleteByEmailId(email.getId());
    }

    @Test
    void sync_callsSyncService_audits() {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId))
                .thenReturn(Optional.of(account));
        when(emailSyncService.sync(account)).thenReturn(5);

        var response = emailService.sync(orgId, userId, accountId, "127.0.0.1");

        assertThat(response.newEmailCount()).isEqualTo(5);
        verify(auditService).log(eq(userId), any(), anyString(), anyString());
    }

    @Test
    void downloadAttachment_validIndex_returnsData() {
        email.setUid(100L);
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId))
                .thenReturn(Optional.of(account));
        when(emailRepository.findByIdAndEmailAccountId(email.getId(), accountId))
                .thenReturn(Optional.of(email));
        when(emailSyncService.downloadAttachment(account, 100L, 0, "INBOX"))
                .thenReturn(new Object[]{"file.pdf", "application/pdf", new byte[]{1, 2, 3}});

        var result = emailService.downloadAttachment(orgId, accountId, email.getId(), 0);

        assertThat(result).isNotNull();
        assertThat(result[0]).isEqualTo("file.pdf");
        assertThat(result[1]).isEqualTo("application/pdf");
    }

    @Test
    void downloadAttachment_noUid_throws() {
        email.setUid(null);
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId))
                .thenReturn(Optional.of(account));
        when(emailRepository.findByIdAndEmailAccountId(email.getId(), accountId))
                .thenReturn(Optional.of(email));

        assertThatThrownBy(() -> emailService.downloadAttachment(orgId, accountId, email.getId(), 0))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("UID");
    }

    @Test
    void list_wrongAccount_throws() {
        when(emailAccountRepository.findByIdAndOrganizationId(any(), eq(orgId)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailService.list(orgId, UUID.randomUUID(), null, null, null, null, null, null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
