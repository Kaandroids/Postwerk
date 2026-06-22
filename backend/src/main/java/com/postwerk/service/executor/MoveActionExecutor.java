package com.postwerk.service.executor;

import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;
import com.postwerk.util.NodeConfigReader;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Executes the MOVE_FOLDER action in an automation workflow.
 *
 * <p>Moves an email from its current IMAP folder to a target folder specified in the
 * node config. Creates the target folder on the server if it does not exist.</p>
 *
 * @since 1.0
 */
@Component
public class MoveActionExecutor implements ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(MoveActionExecutor.class);

    private final ImapMessageExecutor imapMessageExecutor;

    public MoveActionExecutor(ImapMessageExecutor imapMessageExecutor) {
        this.imapMessageExecutor = imapMessageExecutor;
    }

    @Override
    public String getActionType() {
        return "MOVE_FOLDER";
    }

    @Override
    public void execute(Email email, EmailAccount account, JsonNode config, ExecutionContext context) throws Exception {
        String targetFolder = NodeConfigReader.text(config, "folder", null);
        if (targetFolder == null || targetFolder.isBlank()) {
            throw new IllegalArgumentException("folder is required for MOVE_FOLDER action");
        }

        if (email.getUid() == null) {
            throw new IllegalStateException("Email has no UID — cannot move on IMAP");
        }

        imapMessageExecutor.withMessage(account, email, (store, source, msg) -> {
            Folder target = store.getFolder(targetFolder);
            if (!target.exists()) {
                target.create(Folder.HOLDS_MESSAGES);
            }
            if (msg != null) {
                source.moveMessages(new Message[]{msg}, target);
            }
        });

        log.info("Moved email {} to folder {}", email.getId(), targetFolder);
    }
}
