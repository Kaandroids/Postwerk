package com.postwerk.service.executor;

import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Executes the TRASH action in an automation workflow.
 *
 * <p>Moves an email to the account's Trash folder on IMAP. Attempts standard "Trash"
 * and Gmail-specific "[Gmail]/Trash" folder names. Falls back to setting the DELETED
 * flag if no trash folder is found.</p>
 *
 * @since 1.0
 */
@Component
public class TrashActionExecutor implements ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(TrashActionExecutor.class);

    private final ImapMessageExecutor imapMessageExecutor;

    public TrashActionExecutor(ImapMessageExecutor imapMessageExecutor) {
        this.imapMessageExecutor = imapMessageExecutor;
    }

    @Override
    public String getActionType() {
        return "TRASH";
    }

    @Override
    public void execute(Email email, EmailAccount account, JsonNode config, ExecutionContext context) throws Exception {
        if (email.getUid() == null) {
            throw new IllegalStateException("Email has no UID — cannot trash on IMAP");
        }

        imapMessageExecutor.withMessage(account, email, (store, source, msg) -> {
            if (msg != null) {
                Folder trash = store.getFolder("Trash");
                if (!trash.exists()) {
                    trash = store.getFolder("[Gmail]/Trash");
                }
                if (trash.exists()) {
                    source.moveMessages(new Message[]{msg}, trash);
                } else {
                    msg.setFlag(Flags.Flag.DELETED, true);
                }
            }
        });

        log.info("Trashed email {}", email.getId());
    }
}
