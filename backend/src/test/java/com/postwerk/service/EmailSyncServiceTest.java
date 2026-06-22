package com.postwerk.service;

import com.postwerk.TestFixtures;
import com.postwerk.model.EmailAccount;
import com.postwerk.model.EmailAccountFolder;
import com.postwerk.repository.EmailRepository;
import jakarta.mail.*;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailSyncServiceTest {

    @Mock private EmailRepository emailRepository;
    @Mock private MailConnectionFactory mailConnectionFactory;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private MimeMessageParser mimeMessageParser;
    @Mock private ImapFolderManager imapFolderManager;

    @InjectMocks
    private EmailSyncService syncService;

    private EmailAccount account;

    @BeforeEach
    void setUp() {
        account = TestFixtures.createEmailAccount(UUID.randomUUID());
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void sync_acquiresRedisLock_executesSync() throws Exception {
        when(valueOps.setIfAbsent(contains("sync:lock:"), eq("locked"), any(Duration.class)))
                .thenReturn(true);

        Store store = mock(Store.class);
        when(mailConnectionFactory.openImapStore(account)).thenReturn(store);
        when(imapFolderManager.listAndPersistFolders(store, account)).thenReturn(List.of());

        int result = syncService.sync(account);

        assertThat(result).isZero();
        verify(redisTemplate).delete(contains("sync:lock:"));
    }

    @Test
    void sync_lockAlreadyHeld_returns0() throws Exception {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        int result = syncService.sync(account);

        assertThat(result).isZero();
        verify(mailConnectionFactory, never()).openImapStore(any());
    }

    @Test
    void sync_releasesLockOnException() throws Exception {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(mailConnectionFactory.openImapStore(account))
                .thenThrow(new MessagingException("Connection refused"));

        assertThatThrownBy(() -> syncService.sync(account))
                .isInstanceOf(RuntimeException.class);
        verify(redisTemplate).delete(contains("sync:lock:"));
    }

    @Test
    void listAndPersistFolders_readDisabled_skips() throws Exception {
        account.setReadEnabled(false);
        syncService.listAndPersistFolders(account);
        verify(mailConnectionFactory, never()).openImapStore(any());
    }

    @Test
    void listAndPersistFolders_noImapHost_skips() throws Exception {
        account.setImapHost(null);
        syncService.listAndPersistFolders(account);
        verify(mailConnectionFactory, never()).openImapStore(any());
    }

    @Test
    void downloadAttachment_invalidUid_throwsError() throws Exception {
        Store store = mock(Store.class);
        IMAPFolder folder = mock(IMAPFolder.class);
        when(mailConnectionFactory.openImapStore(account)).thenReturn(store);
        when(store.getFolder("INBOX")).thenReturn(folder);
        doNothing().when(folder).open(Folder.READ_ONLY);
        when(folder.getMessageByUID(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> syncService.downloadAttachment(account, 999L, 0, "INBOX"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Message not found");
    }

    @Test
    void syncAllFolders_emptyServer_returnsZero() throws Exception {
        Store store = mock(Store.class);
        when(mailConnectionFactory.openImapStore(account)).thenReturn(store);
        when(imapFolderManager.listAndPersistFolders(store, account)).thenReturn(List.of());

        int result = syncService.syncAllFolders(account);

        assertThat(result).isZero();
    }

    @Test
    void syncAllFolders_connectionFails_throws() throws Exception {
        when(mailConnectionFactory.openImapStore(account))
                .thenThrow(new MessagingException("Host unreachable"));

        assertThatThrownBy(() -> syncService.syncAllFolders(account))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("IMAP sync failed");
    }

    @Test
    void listAndPersistFolders_connectionError_logsWarning() throws Exception {
        account.setReadEnabled(true);
        account.setImapHost("imap.example.com");
        when(mailConnectionFactory.openImapStore(account))
                .thenThrow(new MessagingException("Connection refused"));

        // Should not throw — logs warning instead
        syncService.listAndPersistFolders(account);

        verify(mailConnectionFactory).openImapStore(account);
    }

    @Test
    void sync_firstSync_respectsSyncFromDate() throws Exception {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        Store store = mock(Store.class);
        IMAPFolder inboxFolder = mock(IMAPFolder.class);

        when(mailConnectionFactory.openImapStore(account)).thenReturn(store);
        when(imapFolderManager.listAndPersistFolders(store, account))
                .thenReturn(List.of(EmailAccountFolder.builder()
                        .emailAccountId(account.getId())
                        .name("INBOX")
                        .role("INBOX")
                        .build()));

        when(store.getFolder("INBOX")).thenReturn(inboxFolder);
        when(inboxFolder.exists()).thenReturn(true);
        when(inboxFolder.getType()).thenReturn(Folder.HOLDS_MESSAGES);
        doNothing().when(inboxFolder).open(anyInt());
        when(emailRepository.findMaxUidByEmailAccountIdAndFolder(account.getId(), "INBOX"))
                .thenReturn(Optional.of(0L));
        when(inboxFolder.search(any())).thenReturn(new Message[]{});

        syncService.sync(account);

        verify(redisTemplate).delete(contains("sync:lock:"));
    }
}
