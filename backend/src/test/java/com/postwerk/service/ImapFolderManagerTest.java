package com.postwerk.service;

import com.postwerk.model.EmailAccount;
import com.postwerk.model.EmailAccountFolder;
import com.postwerk.repository.EmailAccountFolderRepository;
import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Store;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ImapFolderManager}, focused on the pure folder-role classification that
 * drives SENT/TRASH/SPAM detection across providers. JavaMail {@link Folder}s are mocked so the
 * suite is fully offline. Locks the IMAP {@code \Special-Use} attribute precedence and the
 * (multi-language) name-based fallback.
 */
@ExtendWith(MockitoExtension.class)
class ImapFolderManagerTest {

    @Mock
    private EmailAccountFolderRepository folderRepository;

    @InjectMocks
    private ImapFolderManager manager;

    // ── detectRole: name-based fallback (non-IMAP folders) ───────────────

    @ParameterizedTest
    @CsvSource({
            "INBOX,            INBOX",
            "inbox,            INBOX",
            "Sent,             SENT",
            "Gesendet,         SENT",
            "Spam,             SPAM",
            "Junk Email,       SPAM",
            "Trash,            TRASH",
            "Papierkorb,       TRASH",
            "Deleted Items,    TRASH",
            "Drafts,           DRAFTS",
            "Entwürfe,         DRAFTS",
            "Archive,          OTHER",
            "Work/Projects,    OTHER",
    })
    void detectRole_fromFolderName(String folderName, String expectedRole) {
        Folder folder = mock(Folder.class);
        when(folder.getFullName()).thenReturn(folderName);

        assertThat(manager.detectRole(folder)).isEqualTo(expectedRole);
    }

    // ── detectRole: IMAP \Special-Use attributes take precedence ─────────

    @ParameterizedTest
    @CsvSource({
            "\\Sent,    SENT",
            "\\Junk,    SPAM",
            "\\Trash,   TRASH",
            "\\Drafts,  DRAFTS",
    })
    void detectRole_fromImapAttribute(String attribute, String expectedRole) throws Exception {
        IMAPFolder folder = mock(IMAPFolder.class);
        when(folder.getAttributes()).thenReturn(new String[]{attribute});

        // name is irrelevant; the attribute path returns before name-based detection
        assertThat(manager.detectRole(folder)).isEqualTo(expectedRole);
    }

    @Test
    void detectRole_imapWithoutAttributes_fallsBackToName() throws Exception {
        IMAPFolder folder = mock(IMAPFolder.class);
        when(folder.getAttributes()).thenReturn(null);
        when(folder.getFullName()).thenReturn("Gesendet");

        assertThat(manager.detectRole(folder)).isEqualTo("SENT");
    }

    // ── findSentFolderName ───────────────────────────────────────────────

    @Test
    void findSentFolderName_returnsPersistedSentFolder() {
        UUID accountId = UUID.randomUUID();
        EmailAccount account = EmailAccount.builder().id(accountId).build();
        when(folderRepository.findByEmailAccountIdOrderByRoleAscNameAsc(accountId))
                .thenReturn(List.of(
                        EmailAccountFolder.builder().name("INBOX").role("INBOX").build(),
                        EmailAccountFolder.builder().name("Gesendet").role("SENT").build()));

        // store is never touched when a persisted SENT folder exists
        assertThat(manager.findSentFolderName(null, account)).isEqualTo("Gesendet");
    }

    @Test
    void findSentFolderName_returnsNullWhenNoneFoundAndImapFails() throws MessagingException {
        UUID accountId = UUID.randomUUID();
        EmailAccount account = EmailAccount.builder().id(accountId).build();
        when(folderRepository.findByEmailAccountIdOrderByRoleAscNameAsc(accountId))
                .thenReturn(List.of());
        Store store = mock(Store.class);
        when(store.getDefaultFolder()).thenThrow(new MessagingException("offline"));

        assertThat(manager.findSentFolderName(store, account)).isNull();
    }
}
