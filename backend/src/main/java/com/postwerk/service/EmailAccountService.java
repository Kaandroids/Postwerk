package com.postwerk.service;

import com.postwerk.dto.EmailAccountRequest;
import com.postwerk.dto.EmailAccountResponse;
import com.postwerk.dto.FolderResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for managing user email accounts with IMAP/SMTP configuration,
 * including CRUD operations, default account selection, and IMAP folder management.
 *
 * @since 1.0
 */
public interface EmailAccountService {

    EmailAccountResponse create(UUID organizationId, UUID actingUserId, EmailAccountRequest request, String ipAddress);

    List<EmailAccountResponse> listByOrg(UUID organizationId);

    EmailAccountResponse getById(UUID organizationId, UUID accountId);

    EmailAccountResponse update(UUID organizationId, UUID actingUserId, UUID accountId, EmailAccountRequest request, String ipAddress);

    void delete(UUID organizationId, UUID actingUserId, UUID accountId, String ipAddress);

    EmailAccountResponse setDefault(UUID organizationId, UUID accountId);

    List<FolderResponse> listFolders(UUID organizationId, UUID accountId);

    FolderResponse createFolder(UUID organizationId, UUID accountId, String folderName);

    void deleteFolder(UUID organizationId, UUID accountId, UUID folderId);
}
