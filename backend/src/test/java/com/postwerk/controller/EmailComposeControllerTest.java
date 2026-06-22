package com.postwerk.controller;

import com.postwerk.dto.ComposeEmailRequest;
import com.postwerk.dto.ComposeEmailResponse;
import com.postwerk.dto.DraftAttachmentResponse;
import com.postwerk.model.enums.OrgRole;
import com.postwerk.service.EmailComposeService;
import com.postwerk.service.OrgContext;
import com.postwerk.service.OrgContextService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailComposeControllerTest {

    @Mock private EmailComposeService composeService;
    @Mock private OrgContextService orgContext;

    @InjectMocks
    private EmailComposeController controller;

    private UUID userId;
    private UUID orgId;
    private UUID accountId;
    private UUID draftId;
    private UserDetails userDetails;
    private HttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        draftId = UUID.randomUUID();
        userDetails = new User("test@example.com", "password", Collections.emptyList());
        httpRequest = new MockHttpServletRequest();
        OrgContext ctx = new OrgContext(orgId, userId, UUID.randomUUID(), OrgRole.OWNER,
                OrgRole.OWNER.permissions(), true);
        lenient().when(orgContext.resolve(eq(userDetails), any())).thenReturn(ctx);
    }

    // --- send ---

    @Test
    void send_delegatesToServiceAndReturnsOk() {
        ComposeEmailRequest request = new ComposeEmailRequest(
                "to@example.com", null, null, "Subject", "<p>Body</p>", null, null, false);
        ComposeEmailResponse expected = new ComposeEmailResponse(
                null, "<msg@id>", "SENT", "to@example.com", null, null,
                "Subject", "<p>Body</p>", Instant.now(), false, List.of());
        when(composeService.send(orgId, accountId, request)).thenReturn(expected);

        ResponseEntity<ComposeEmailResponse> response = controller.send(userDetails, accountId, request, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(composeService).send(orgId, accountId, request);
    }

    // --- saveDraft ---

    @Test
    void saveDraft_delegatesToServiceAndReturnsOk() {
        ComposeEmailRequest request = new ComposeEmailRequest(
                "to@example.com", null, null, "Draft", "<p>Body</p>", null, null, true);
        ComposeEmailResponse expected = new ComposeEmailResponse(
                draftId, null, "DRAFTS", "to@example.com", null, null,
                "Draft", "<p>Body</p>", Instant.now(), true, List.of());
        when(composeService.saveDraft(orgId, accountId, request)).thenReturn(expected);

        ResponseEntity<ComposeEmailResponse> response = controller.saveDraft(userDetails, accountId, request, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isDraft()).isTrue();
    }

    // --- updateDraft ---

    @Test
    void updateDraft_delegatesToServiceAndReturnsOk() {
        ComposeEmailRequest request = new ComposeEmailRequest(
                "updated@example.com", null, null, "Updated", null, null, null, true);
        ComposeEmailResponse expected = new ComposeEmailResponse(
                draftId, null, "DRAFTS", "updated@example.com", null, null,
                "Updated", null, Instant.now(), true, List.of());
        when(composeService.updateDraft(orgId, accountId, draftId, request)).thenReturn(expected);

        ResponseEntity<ComposeEmailResponse> response = controller.updateDraft(userDetails, accountId, draftId, request, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().toAddresses()).isEqualTo("updated@example.com");
    }

    // --- deleteDraft ---

    @Test
    void deleteDraft_delegatesToServiceAndReturnsNoContent() {
        ResponseEntity<Void> response = controller.deleteDraft(userDetails, accountId, draftId, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(composeService).deleteDraft(orgId, accountId, draftId);
    }

    // --- uploadAttachment ---

    @Test
    void uploadAttachment_delegatesToServiceAndReturnsOk() {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[1024]);
        DraftAttachmentResponse expected = new DraftAttachmentResponse(UUID.randomUUID(), "test.pdf", "application/pdf", 1024);
        when(composeService.uploadAttachment(orgId, accountId, draftId, file)).thenReturn(expected);

        ResponseEntity<DraftAttachmentResponse> response = controller.uploadAttachment(userDetails, accountId, draftId, file, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().fileName()).isEqualTo("test.pdf");
    }

    // --- deleteAttachment ---

    @Test
    void deleteAttachment_delegatesToServiceAndReturnsNoContent() {
        UUID attachmentId = UUID.randomUUID();

        ResponseEntity<Void> response = controller.deleteAttachment(userDetails, accountId, draftId, attachmentId, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(composeService).deleteAttachment(orgId, accountId, draftId, attachmentId);
    }

    // --- listAttachments ---

    @Test
    void listAttachments_delegatesToServiceAndReturnsOk() {
        List<DraftAttachmentResponse> expected = List.of(
                new DraftAttachmentResponse(UUID.randomUUID(), "file1.pdf", "application/pdf", 5000),
                new DraftAttachmentResponse(UUID.randomUUID(), "image.png", "image/png", 2000));
        when(composeService.listAttachments(orgId, accountId, draftId)).thenReturn(expected);

        ResponseEntity<List<DraftAttachmentResponse>> response = controller.listAttachments(userDetails, accountId, draftId, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }
}
