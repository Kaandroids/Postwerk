package com.postwerk.service.executor;

import com.postwerk.TestFixtures;
import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link MoveActionExecutor}. The IMAP move is delegated to {@link ImapMessageExecutor}
 * (mocked), so these verify the config + UID guards and that the move is delegated — without IMAP.
 */
@ExtendWith(MockitoExtension.class)
class MoveActionExecutorTest {

    @Mock private ImapMessageExecutor imapMessageExecutor;

    private MoveActionExecutor executor;
    private final ObjectMapper mapper = new ObjectMapper();
    private Email email;
    private EmailAccount account;
    private ExecutionContext ctx;

    @BeforeEach
    void setUp() {
        executor = new MoveActionExecutor(imapMessageExecutor);
        account = TestFixtures.createEmailAccount(UUID.randomUUID());
        email = TestFixtures.createEmail(UUID.randomUUID());
        ctx = new ExecutionContext(email, account);
    }

    private JsonNode cfg(String json) throws Exception {
        return mapper.readTree(json);
    }

    @Test
    void getActionType_isMoveFolder() {
        assertThat(executor.getActionType()).isEqualTo("MOVE_FOLDER");
    }

    @Test
    void missingTargetFolder_throws() throws Exception {
        assertThatThrownBy(() -> executor.execute(email, account, cfg("{}"), ctx))
                .isInstanceOf(IllegalArgumentException.class);

        verify(imapMessageExecutor, never()).withMessage(any(), any(), any());
    }

    @Test
    void missingUid_throws() throws Exception {
        email.setUid(null);

        assertThatThrownBy(() -> executor.execute(email, account, cfg("{\"folder\":\"Archive\"}"), ctx))
                .isInstanceOf(IllegalStateException.class);

        verify(imapMessageExecutor, never()).withMessage(any(), any(), any());
    }

    @Test
    void validMove_delegatesToImap() throws Exception {
        executor.execute(email, account, cfg("{\"folder\":\"Archive\"}"), ctx);

        verify(imapMessageExecutor).withMessage(eq(account), eq(email), any());
    }
}
