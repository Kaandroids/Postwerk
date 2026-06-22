package com.postwerk.service;

import com.postwerk.dto.ComposeEmailRequest;
import com.postwerk.dto.ComposeEmailResponse;
import com.postwerk.dto.DraftAttachmentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for composing, sending, and managing email drafts with attachment support.
 *
 * @since 1.0
 */
public interface EmailComposeService {

    ComposeEmailResponse send(UUID organizationId, UUID accountId, ComposeEmailRequest request);

    ComposeEmailResponse saveDraft(UUID organizationId, UUID accountId, ComposeEmailRequest request);

    ComposeEmailResponse updateDraft(UUID organizationId, UUID accountId, UUID draftId, ComposeEmailRequest request);

    void deleteDraft(UUID organizationId, UUID accountId, UUID draftId);

    DraftAttachmentResponse uploadAttachment(UUID organizationId, UUID accountId, UUID draftId, MultipartFile file);

    void deleteAttachment(UUID organizationId, UUID accountId, UUID draftId, UUID attachmentId);

    List<DraftAttachmentResponse> listAttachments(UUID organizationId, UUID accountId, UUID draftId);
}
