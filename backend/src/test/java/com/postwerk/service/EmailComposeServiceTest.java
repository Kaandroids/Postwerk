package com.postwerk.service;

import com.postwerk.dto.ComposeEmailRequest;
import com.postwerk.dto.ComposeEmailResponse;
import com.postwerk.dto.DraftAttachmentResponse;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.DraftAttachment;
import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;
import com.postwerk.repository.DraftAttachmentRepository;
import com.postwerk.repository.EmailAccountFolderRepository;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.repository.EmailRepository;
import com.postwerk.service.impl.EmailComposeServiceImpl;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static com.postwerk.TestFixtures.createEmail;
import static com.postwerk.TestFixtures.createEmailAccount;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailComposeServiceTest {

    @Mock private EmailRepository emailRepository;
    @Mock private EmailAccountRepository emailAccountRepository;
    @Mock private EmailAccountFolderRepository folderRepository;
    @Mock private DraftAttachmentRepository draftAttachmentRepository;
    @Mock private MailConnectionFactory mailConnectionFactory;
    @Mock private EmailSyncService emailSyncService;

    @InjectMocks
    private EmailComposeServiceImpl composeService;

    private UUID userId;
    private UUID orgId;
    private UUID accountId;
    private EmailAccount account;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        account = createEmailAccount(userId);
        accountId = account.getId();
    }

    // --- resolveAccount ---

    @Test
    void send_unknownAccount_throwsResourceNotFound() {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId)).thenReturn(Optional.empty());

        ComposeEmailRequest request = new ComposeEmailRequest(
                "to@example.com", null, null, "Subject", "<p>Body</p>", null, null, false);

        assertThatThrownBy(() -> composeService.send(orgId, accountId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- send ---

    @Test
    void send_smtpNotConfigured_throwsIllegalState() {
        account.setWriteEnabled(false);
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId)).thenReturn(Optional.of(account));

        ComposeEmailRequest request = new ComposeEmailRequest(
                "to@example.com", null, null, "Subject", "<p>Body</p>", null, null, false);

        assertThatThrownBy(() -> composeService.send(orgId, accountId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMTP not configured");
    }

    @Test
    void send_smtpHostNull_throwsIllegalState() {
        account.setWriteEnabled(true);
        account.setSmtpHost(null);
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId)).thenReturn(Optional.of(account));

        ComposeEmailRequest request = new ComposeEmailRequest(
                "to@example.com", null, null, "Subject", "<p>Body</p>", null, null, false);

        assertThatThrownBy(() -> composeService.send(orgId, accountId, request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void send_isDraftTrue_delegatesToSaveDraft() {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId)).thenReturn(Optional.of(account));
        Email savedDraft = createEmail(accountId);
        savedDraft.setFolder("DRAFTS");
        when(emailRepository.save(any(Email.class))).thenReturn(savedDraft);
        when(draftAttachmentRepository.findByEmailId(any())).thenReturn(List.of());

        ComposeEmailRequest request = new ComposeEmailRequest(
                "to@example.com", null, null, "Subject", "<p>Body</p>", null, null, true);

        ComposeEmailResponse response = composeService.send(orgId, accountId, request);

        assertThat(response.isDraft()).isTrue();
        assertThat(response.folder()).isEqualTo("DRAFTS");
        verifyNoInteractions(mailConnectionFactory);
    }

    @Test
    void send_success_returnsSentResponse() throws Exception {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId)).thenReturn(Optional.of(account));
        Session session = Session.getInstance(new Properties());
        when(mailConnectionFactory.createSmtpSession(account)).thenReturn(session);

        ComposeEmailRequest request = new ComposeEmailRequest(
                "to@example.com", "cc@example.com", "bcc@example.com",
                "Test Subject", "<p>Hello</p>", null, null, false);

        try (var transportMock = mockStatic(jakarta.mail.Transport.class)) {
            transportMock.when(() -> jakarta.mail.Transport.send(any(MimeMessage.class))).thenAnswer(inv -> null);

            ComposeEmailResponse response = composeService.send(orgId, accountId, request);

            assertThat(response.folder()).isEqualTo("SENT");
            assertThat(response.isDraft()).isFalse();
            assertThat(response.toAddresses()).isEqualTo("to@example.com");
            assertThat(response.ccAddresses()).isEqualTo("cc@example.com");
            assertThat(response.bccAddresses()).isEqualTo("bcc@example.com");
            assertThat(response.subject()).isEqualTo("Test Subject");
            verify(emailSyncService).appendToSentFolder(eq(account), any(MimeMessage.class));
        }
    }

    @Test
    void send_fromDraft_cleansDraftAndAttachments() throws Exception {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId)).thenReturn(Optional.of(account));
        Session session = Session.getInstance(new Properties());
        when(mailConnectionFactory.createSmtpSession(account)).thenReturn(session);

        Email draft = createEmail(accountId);
        draft.setFolder("DRAFTS");
        UUID draftId = draft.getId();
        when(emailRepository.findByIdAndEmailAccountId(draftId, accountId)).thenReturn(Optional.of(draft));
        when(draftAttachmentRepository.findByEmailId(draftId)).thenReturn(List.of());

        ComposeEmailRequest request = new ComposeEmailRequest(
                "to@example.com", null, null, "Subject", "<p>Body</p>", null, draftId.toString(), false);

        try (var transportMock = mockStatic(jakarta.mail.Transport.class)) {
            transportMock.when(() -> jakarta.mail.Transport.send(any(MimeMessage.class))).thenAnswer(inv -> null);

            composeService.send(orgId, accountId, request);

            verify(draftAttachmentRepository).deleteByEmailId(draftId);
            verify(emailRepository).delete(draft);
        }
    }

    // --- saveDraft ---

    @Test
    void saveDraft_success_createsDraftEmail() {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId)).thenReturn(Optional.of(account));
        Email savedDraft = createEmail(accountId);
        savedDraft.setFolder("DRAFTS");
        when(emailRepository.save(any(Email.class))).thenReturn(savedDraft);
        when(draftAttachmentRepository.findByEmailId(any())).thenReturn(List.of());

        ComposeEmailRequest request = new ComposeEmailRequest(
                "to@example.com", "cc@example.com", "bcc@example.com",
                "Draft Subject", "<p>Draft body</p>", null, null, true);

        ComposeEmailResponse response = composeService.saveDraft(orgId, accountId, request);

        assertThat(response.isDraft()).isTrue();
        assertThat(response.folder()).isEqualTo("DRAFTS");

        ArgumentCaptor<Email> captor = ArgumentCaptor.forClass(Email.class);
        verify(emailRepository).save(captor.capture());
        Email captured = captor.getValue();
        assertThat(captured.getFolder()).isEqualTo("DRAFTS");
        assertThat(captured.getToAddresses()).isEqualTo("to@example.com");
        assertThat(captured.getCcAddresses()).isEqualTo("cc@example.com");
        assertThat(captured.getBccAddresses()).isEqualTo("bcc@example.com");
        assertThat(captured.getSubject()).isEqualTo("Draft Subject");
    }

    @Test
    void saveDraft_unknownAccount_throwsResourceNotFound() {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId)).thenReturn(Optional.empty());

        ComposeEmailRequest request = new ComposeEmailRequest(
                "to@example.com", null, null, "Subject", null, null, null, true);

        assertThatThrownBy(() -> composeService.saveDraft(orgId, accountId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- updateDraft ---

    @Test
    void updateDraft_success_updatesExistingDraft() {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId)).thenReturn(Optional.of(account));
        Email draft = createEmail(accountId);
        draft.setFolder("DRAFTS");
        UUID draftId = draft.getId();
        when(emailRepository.findByIdAndEmailAccountId(draftId, accountId)).thenReturn(Optional.of(draft));
        when(draftAttachmentRepository.findByEmailId(draftId)).thenReturn(List.of());
        when(emailRepository.save(any(Email.class))).thenReturn(draft);

        ComposeEmailRequest request = new ComposeEmailRequest(
                "updated@example.com", null, null, "Updated Subject", "<p>Updated</p>", null, null, true);

        ComposeEmailResponse response = composeService.updateDraft(orgId, accountId, draftId, request);

        assertThat(response).isNotNull();
        verify(emailRepository).save(draft);
        assertThat(draft.getToAddresses()).isEqualTo("updated@example.com");
        assertThat(draft.getSubject()).isEqualTo("Updated Subject");
    }

    @Test
    void updateDraft_notADraft_throwsIllegalState() {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId)).thenReturn(Optional.of(account));
        Email sentEmail = createEmail(accountId);
        sentEmail.setFolder("SENT");
        UUID emailId = sentEmail.getId();
        when(emailRepository.findByIdAndEmailAccountId(emailId, accountId)).thenReturn(Optional.of(sentEmail));

        ComposeEmailRequest request = new ComposeEmailRequest(
                "to@example.com", null, null, "Subject", null, null, null, true);

        assertThatThrownBy(() -> composeService.updateDraft(orgId, accountId, emailId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a draft");
    }

    @Test
    void updateDraft_notFound_throwsResourceNotFound() {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId)).thenReturn(Optional.of(account));
        UUID draftId = UUID.randomUUID();
        when(emailRepository.findByIdAndEmailAccountId(draftId, accountId)).thenReturn(Optional.empty());

        ComposeEmailRequest request = new ComposeEmailRequest(
                "to@example.com", null, null, "Subject", null, null, null, true);

        assertThatThrownBy(() -> composeService.updateDraft(orgId, accountId, draftId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- deleteDraft ---

    @Test
    void deleteDraft_success_deletesDraftAndAttachments() {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId)).thenReturn(Optional.of(account));
        Email draft = createEmail(accountId);
        draft.setFolder("DRAFTS");
        UUID draftId = draft.getId();
        when(emailRepository.findByIdAndEmailAccountId(draftId, accountId)).thenReturn(Optional.of(draft));

        composeService.deleteDraft(orgId, accountId, draftId);

        verify(draftAttachmentRepository).deleteByEmailId(draftId);
        verify(emailRepository).delete(draft);
    }

    @Test
    void deleteDraft_notADraft_throwsIllegalState() {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId)).thenReturn(Optional.of(account));
        Email sentEmail = createEmail(accountId);
        sentEmail.setFolder("INBOX");
        UUID emailId = sentEmail.getId();
        when(emailRepository.findByIdAndEmailAccountId(emailId, accountId)).thenReturn(Optional.of(sentEmail));

        assertThatThrownBy(() -> composeService.deleteDraft(orgId, accountId, emailId))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- uploadAttachment ---

    @Test
    void uploadAttachment_success_savesAttachment() throws IOException {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId)).thenReturn(Optional.of(account));
        Email draft = createEmail(accountId);
        draft.setFolder("DRAFTS");
        UUID draftId = draft.getId();
        when(emailRepository.findByIdAndEmailAccountId(draftId, accountId)).thenReturn(Optional.of(draft));
        when(emailRepository.save(any(Email.class))).thenReturn(draft);

        DraftAttachment saved = DraftAttachment.builder()
                .id(UUID.randomUUID()).emailId(draftId)
                .fileName("test.pdf").contentType("application/pdf")
                .sizeBytes(1024L).data(new byte[1024])
                .createdAt(Instant.now()).build();
        when(draftAttachmentRepository.save(any(DraftAttachment.class))).thenReturn(saved);

        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(1024L);
        when(file.getOriginalFilename()).thenReturn("test.pdf");
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getBytes()).thenReturn(new byte[1024]);

        DraftAttachmentResponse response = composeService.uploadAttachment(orgId, accountId, draftId, file);

        assertThat(response.fileName()).isEqualTo("test.pdf");
        assertThat(response.contentType()).isEqualTo("application/pdf");
        assertThat(response.sizeBytes()).isEqualTo(1024L);
        verify(draftAttachmentRepository).save(any(DraftAttachment.class));
    }

    @Test
    void uploadAttachment_blockedExecutableExtension_throwsIllegalArgument() {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId)).thenReturn(Optional.of(account));
        Email draft = createEmail(accountId);
        draft.setFolder("DRAFTS");
        UUID draftId = draft.getId();
        when(emailRepository.findByIdAndEmailAccountId(draftId, accountId)).thenReturn(Optional.of(draft));

        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(1024L);
        when(file.getOriginalFilename()).thenReturn("malware.exe");

        assertThatThrownBy(() -> composeService.uploadAttachment(orgId, accountId, draftId, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".exe");
        verify(draftAttachmentRepository, never()).save(any(DraftAttachment.class));
    }

    @Test
    void uploadAttachment_cumulativeSizeExceeded_throwsIllegalArgument() {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId)).thenReturn(Optional.of(account));
        Email draft = createEmail(accountId);
        draft.setFolder("DRAFTS");
        UUID draftId = draft.getId();
        when(emailRepository.findByIdAndEmailAccountId(draftId, accountId)).thenReturn(Optional.of(draft));

        // 20MB already attached + a 9MB upload = 29MB > 25MB per-draft cap
        DraftAttachment existing = DraftAttachment.builder()
                .id(UUID.randomUUID()).emailId(draftId).fileName("big.pdf")
                .contentType("application/pdf").sizeBytes(20L * 1024 * 1024).build();
        when(draftAttachmentRepository.findByEmailId(draftId)).thenReturn(List.of(existing));

        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(9L * 1024 * 1024);
        when(file.getOriginalFilename()).thenReturn("more.pdf");

        assertThatThrownBy(() -> composeService.uploadAttachment(orgId, accountId, draftId, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("25MB");
        verify(draftAttachmentRepository, never()).save(any(DraftAttachment.class));
    }

    @Test
    void uploadAttachment_exceedsMaxSize_throwsIllegalArgument() {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId)).thenReturn(Optional.of(account));
        Email draft = createEmail(accountId);
        draft.setFolder("DRAFTS");
        UUID draftId = draft.getId();
        when(emailRepository.findByIdAndEmailAccountId(draftId, accountId)).thenReturn(Optional.of(draft));

        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(11 * 1024 * 1024L); // 11MB

        assertThatThrownBy(() -> composeService.uploadAttachment(orgId, accountId, draftId, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10MB");
    }

    @Test
    void uploadAttachment_notADraft_throwsIllegalState() {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId)).thenReturn(Optional.of(account));
        Email sentEmail = createEmail(accountId);
        sentEmail.setFolder("SENT");
        UUID emailId = sentEmail.getId();
        when(emailRepository.findByIdAndEmailAccountId(emailId, accountId)).thenReturn(Optional.of(sentEmail));

        MultipartFile file = mock(MultipartFile.class);

        assertThatThrownBy(() -> composeService.uploadAttachment(orgId, accountId, emailId, file))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- deleteAttachment ---

    @Test
    void deleteAttachment_success_removesAttachment() {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId)).thenReturn(Optional.of(account));
        Email draft = createEmail(accountId);
        draft.setFolder("DRAFTS");
        UUID draftId = draft.getId();
        when(emailRepository.findByIdAndEmailAccountId(draftId, accountId)).thenReturn(Optional.of(draft));

        DraftAttachment attachment = DraftAttachment.builder()
                .id(UUID.randomUUID()).emailId(draftId)
                .fileName("file.txt").contentType("text/plain")
                .sizeBytes(100).data(new byte[100])
                .createdAt(Instant.now()).build();
        when(draftAttachmentRepository.findById(attachment.getId())).thenReturn(Optional.of(attachment));
        when(draftAttachmentRepository.findByEmailId(draftId)).thenReturn(List.of());
        when(emailRepository.findById(draftId)).thenReturn(Optional.of(draft));
        when(emailRepository.save(any(Email.class))).thenReturn(draft);

        composeService.deleteAttachment(orgId, accountId, draftId, attachment.getId());

        verify(draftAttachmentRepository).delete(attachment);
        assertThat(draft.isHasAttachments()).isFalse();
    }

    @Test
    void deleteAttachment_wrongDraft_throwsIllegalState() {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId)).thenReturn(Optional.of(account));
        Email draft = createEmail(accountId);
        draft.setFolder("DRAFTS");
        UUID draftId = draft.getId();
        when(emailRepository.findByIdAndEmailAccountId(draftId, accountId)).thenReturn(Optional.of(draft));

        DraftAttachment attachment = DraftAttachment.builder()
                .id(UUID.randomUUID()).emailId(UUID.randomUUID()) // different draft
                .fileName("file.txt").contentType("text/plain")
                .sizeBytes(100).data(new byte[100])
                .createdAt(Instant.now()).build();
        when(draftAttachmentRepository.findById(attachment.getId())).thenReturn(Optional.of(attachment));

        assertThatThrownBy(() -> composeService.deleteAttachment(orgId, accountId, draftId, attachment.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not belong");
    }

    // --- listAttachments ---

    @Test
    void listAttachments_success_returnsAttachmentResponses() {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId)).thenReturn(Optional.of(account));
        Email draft = createEmail(accountId);
        draft.setFolder("DRAFTS");
        UUID draftId = draft.getId();
        when(emailRepository.findByIdAndEmailAccountId(draftId, accountId)).thenReturn(Optional.of(draft));

        DraftAttachment att1 = DraftAttachment.builder()
                .id(UUID.randomUUID()).emailId(draftId)
                .fileName("file1.pdf").contentType("application/pdf")
                .sizeBytes(5000).data(new byte[5000])
                .createdAt(Instant.now()).build();
        DraftAttachment att2 = DraftAttachment.builder()
                .id(UUID.randomUUID()).emailId(draftId)
                .fileName("image.png").contentType("image/png")
                .sizeBytes(2000).data(new byte[2000])
                .createdAt(Instant.now()).build();
        when(draftAttachmentRepository.findByEmailId(draftId)).thenReturn(List.of(att1, att2));

        List<DraftAttachmentResponse> result = composeService.listAttachments(orgId, accountId, draftId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).fileName()).isEqualTo("file1.pdf");
        assertThat(result.get(1).fileName()).isEqualTo("image.png");
    }

    @Test
    void listAttachments_draftNotFound_throwsResourceNotFound() {
        when(emailAccountRepository.findByIdAndOrganizationId(accountId, orgId)).thenReturn(Optional.of(account));
        UUID draftId = UUID.randomUUID();
        when(emailRepository.findByIdAndEmailAccountId(draftId, accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> composeService.listAttachments(orgId, accountId, draftId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
