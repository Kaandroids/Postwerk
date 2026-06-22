package com.postwerk.service;

import com.postwerk.TestFixtures;
import com.postwerk.config.EncryptionConfig;
import com.postwerk.dto.EmailAccountRequest;
import com.postwerk.model.EmailAccount;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.repository.EmailAccountFolderRepository;
import com.postwerk.service.impl.EmailAccountServiceImpl;
import com.postwerk.util.HostSecurityValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailAccountServiceTest {

    @Mock private EmailAccountRepository repository;
    @Mock private EmailAccountFolderRepository folderRepository;
    @Mock private EncryptionConfig encryptionConfig;
    @Mock private AuditService auditService;
    @Mock private EmailSyncService emailSyncService;
    @Mock private QuotaService quotaService;
    @Mock private HostSecurityValidator hostSecurityValidator;

    @InjectMocks
    private EmailAccountServiceImpl service;

    private UUID orgId;
    private UUID userId;
    private EmailAccount existingAccount;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        userId = UUID.randomUUID();
        existingAccount = TestFixtures.createEmailAccount(userId);
    }

    @Test
    void create_firstAccount_setsDefault() {
        when(repository.findByOrganizationId(orgId)).thenReturn(List.of());
        when(encryptionConfig.encrypt(anyString())).thenReturn("encrypted");
        when(repository.save(any(EmailAccount.class))).thenAnswer(inv -> {
            EmailAccount a = inv.getArgument(0);
            if (a.getId() == null) a.setId(UUID.randomUUID());
            return a;
        });

        var request = TestFixtures.createEmailAccountRequest();
        service.create(orgId, userId, request, "127.0.0.1");

        ArgumentCaptor<EmailAccount> captor = ArgumentCaptor.forClass(EmailAccount.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isDefault()).isTrue();
        assertThat(captor.getValue().getOrganizationId()).isEqualTo(orgId);
    }

    @Test
    void create_encryptsPasswords() {
        when(repository.findByOrganizationId(orgId)).thenReturn(List.of());
        when(encryptionConfig.encrypt("imapPass123")).thenReturn("encrypted-imap");
        when(encryptionConfig.encrypt("smtpPass123")).thenReturn("encrypted-smtp");
        when(repository.save(any(EmailAccount.class))).thenAnswer(inv -> {
            EmailAccount a = inv.getArgument(0);
            if (a.getId() == null) a.setId(UUID.randomUUID());
            return a;
        });

        var request = TestFixtures.createEmailAccountRequest();
        service.create(orgId, userId, request, "127.0.0.1");

        ArgumentCaptor<EmailAccount> captor = ArgumentCaptor.forClass(EmailAccount.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getImapPassword()).isEqualTo("encrypted-imap");
        assertThat(captor.getValue().getSmtpPassword()).isEqualTo("encrypted-smtp");
    }

    @Test
    void create_secondAccount_notDefault() {
        when(repository.findByOrganizationId(orgId)).thenReturn(List.of(existingAccount));
        when(encryptionConfig.encrypt(anyString())).thenReturn("encrypted");
        when(repository.save(any(EmailAccount.class))).thenAnswer(inv -> {
            EmailAccount a = inv.getArgument(0);
            if (a.getId() == null) a.setId(UUID.randomUUID());
            return a;
        });

        var request = new EmailAccountRequest(
                "second@example.com", "Second", "#ef4444",
                true, true, "imap.example.com", 993, "second@example.com", "pass", true,
                "smtp.example.com", 587, "second@example.com", "pass", true,
                null, false
        );
        service.create(orgId, userId, request, "127.0.0.1");

        ArgumentCaptor<EmailAccount> captor = ArgumentCaptor.forClass(EmailAccount.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isDefault()).isFalse();
    }

    @Test
    void update_disableReadWrite_clearsPasswords() {
        when(repository.findByIdAndOrganizationId(existingAccount.getId(), orgId))
                .thenReturn(Optional.of(existingAccount));
        existingAccount.setImapPassword("existing-encrypted-imap");
        existingAccount.setSmtpPassword("existing-encrypted-smtp");
        when(repository.save(any(EmailAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new EmailAccountRequest(
                "inbox@example.com", "Updated", "#3b82f6",
                false, false, null, null, null, null, null,
                null, null, null, null, null,
                null, false
        );
        service.update(orgId, userId, existingAccount.getId(), request, "127.0.0.1");

        ArgumentCaptor<EmailAccount> captor = ArgumentCaptor.forClass(EmailAccount.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getImapPassword()).isNull();
        assertThat(captor.getValue().getSmtpPassword()).isNull();
    }

    @Test
    void update_newPassword_reEncrypts() {
        when(repository.findByIdAndOrganizationId(existingAccount.getId(), orgId))
                .thenReturn(Optional.of(existingAccount));
        when(encryptionConfig.encrypt("newImapPass")).thenReturn("encrypted-new-imap");
        when(encryptionConfig.encrypt("newSmtpPass")).thenReturn("encrypted-new-smtp");
        when(repository.save(any(EmailAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new EmailAccountRequest(
                "inbox@example.com", "Updated", "#3b82f6",
                true, true, "imap.example.com", 993, "inbox@example.com", "newImapPass", true,
                "smtp.example.com", 587, "inbox@example.com", "newSmtpPass", true,
                null, false
        );
        service.update(orgId, userId, existingAccount.getId(), request, "127.0.0.1");

        ArgumentCaptor<EmailAccount> captor = ArgumentCaptor.forClass(EmailAccount.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getImapPassword()).isEqualTo("encrypted-new-imap");
        assertThat(captor.getValue().getSmtpPassword()).isEqualTo("encrypted-new-smtp");
    }

    @Test
    void delete_existingAccount_deletesAccount() {
        when(repository.findByIdAndOrganizationId(existingAccount.getId(), orgId))
                .thenReturn(Optional.of(existingAccount));

        service.delete(orgId, userId, existingAccount.getId(), "127.0.0.1");

        verify(repository).delete(existingAccount);
    }

    @Test
    void delete_defaultAccount_promotesNext() {
        existingAccount.setDefault(true);
        var secondAccount = TestFixtures.createEmailAccount(userId);
        secondAccount.setDefault(false);
        secondAccount.setEmail("second@example.com");

        when(repository.findByIdAndOrganizationId(existingAccount.getId(), orgId))
                .thenReturn(Optional.of(existingAccount));
        when(repository.findByOrganizationId(orgId)).thenReturn(List.of(secondAccount));
        when(repository.save(any(EmailAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(orgId, userId, existingAccount.getId(), "127.0.0.1");

        assertThat(secondAccount.isDefault()).isTrue();
    }

    @Test
    void setDefault_clearsOldDefault() {
        var oldDefault = TestFixtures.createEmailAccount(userId);
        oldDefault.setDefault(true);
        var newDefault = TestFixtures.createEmailAccount(userId);
        newDefault.setDefault(false);

        when(repository.findByIdAndOrganizationId(newDefault.getId(), orgId))
                .thenReturn(Optional.of(newDefault));
        when(repository.findByOrganizationIdAndIsDefaultTrue(orgId))
                .thenReturn(Optional.of(oldDefault));
        when(repository.save(any(EmailAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        service.setDefault(orgId, newDefault.getId());

        assertThat(oldDefault.isDefault()).isFalse();
        assertThat(newDefault.isDefault()).isTrue();
    }

    @Test
    void getById_wrongOrg_throwsNotFound() {
        UUID otherOrgId = UUID.randomUUID();
        when(repository.findByIdAndOrganizationId(existingAccount.getId(), otherOrgId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(otherOrgId, existingAccount.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listByOrg_returnsAccountsWithoutPasswords() {
        when(repository.findByOrganizationId(orgId)).thenReturn(List.of(existingAccount));

        var result = service.listByOrg(orgId);

        assertThat(result).hasSize(1);
        // Passwords should not be in the response
        assertThat(result.get(0).imapHost()).isEqualTo("imap.example.com");
    }

    @Test
    void update_disableImap_clearsImapFields() {
        when(repository.findByIdAndOrganizationId(existingAccount.getId(), orgId))
                .thenReturn(Optional.of(existingAccount));
        when(repository.save(any(EmailAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new EmailAccountRequest(
                "inbox@example.com", "Updated", "#3b82f6",
                false, true, null, null, null, null, null,
                "smtp.example.com", 587, "inbox@example.com", "pass", true,
                null, false
        );
        service.update(orgId, userId, existingAccount.getId(), request, "127.0.0.1");

        ArgumentCaptor<EmailAccount> captor = ArgumentCaptor.forClass(EmailAccount.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isReadEnabled()).isFalse();
    }

    @Test
    void update_disableSmtp_clearsSmtpFields() {
        when(repository.findByIdAndOrganizationId(existingAccount.getId(), orgId))
                .thenReturn(Optional.of(existingAccount));
        when(repository.save(any(EmailAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new EmailAccountRequest(
                "inbox@example.com", "Updated", "#3b82f6",
                true, false, "imap.example.com", 993, "inbox@example.com", "pass", true,
                null, null, null, null, null,
                null, false
        );
        service.update(orgId, userId, existingAccount.getId(), request, "127.0.0.1");

        ArgumentCaptor<EmailAccount> captor = ArgumentCaptor.forClass(EmailAccount.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isWriteEnabled()).isFalse();
    }
}
