package com.postwerk.service.executor;

import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;
import com.postwerk.service.MailConnectionFactory;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Store;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.springframework.stereotype.Component;

/**
 * Encapsulates the shared IMAP boilerplate used by message-mutating action executors
 * (move, trash): open the store, open the source folder READ_WRITE, look up the message
 * by UID, and guarantee both folder and store are closed afterwards.
 *
 * @since 1.0
 */
@Component
public class ImapMessageExecutor {

    private final MailConnectionFactory mailConnectionFactory;

    public ImapMessageExecutor(MailConnectionFactory mailConnectionFactory) {
        this.mailConnectionFactory = mailConnectionFactory;
    }

    /**
     * Operation performed against an open IMAP source folder for a resolved message.
     * The message may be {@code null} if no message matches the email's UID.
     */
    @FunctionalInterface
    public interface MessageOperation {
        void apply(Store store, IMAPFolder source, Message message) throws Exception;
    }

    /**
     * Opens the email's source folder READ_WRITE, resolves its message by UID, runs the
     * given operation, then closes the folder (expunge) and store.
     */
    public void withMessage(EmailAccount account, Email email, MessageOperation operation) throws Exception {
        Store store = mailConnectionFactory.openImapStore(account);
        try {
            IMAPFolder source = (IMAPFolder) store.getFolder(
                    email.getFolder() != null ? email.getFolder() : "INBOX");
            source.open(Folder.READ_WRITE);

            try {
                Message msg = source.getMessageByUID(email.getUid());
                operation.apply(store, source, msg);
            } finally {
                source.close(true);
            }
        } finally {
            store.close();
        }
    }
}
