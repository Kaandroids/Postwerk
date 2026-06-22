package com.postwerk.controller;

import com.postwerk.BaseIntegrationTest;
import com.postwerk.dto.EmailAccountRequest;
import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.repository.EmailRepository;
import com.postwerk.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EmailControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private EmailRepository emailRepository;

    @Autowired
    private EmailAccountRepository emailAccountRepository;

    @Autowired
    private UserRepository userRepository;

    private String createAccountAndGetId(String token, String accountEmail) throws Exception {
        var request = new EmailAccountRequest(
                accountEmail, "Test", "#3b82f6",
                false, false, null, null, null, null, null,
                null, null, null, null, null,
                null, false
        );

        String responseBody = mockMvc.perform(post("/api/v1/email-accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(responseBody).get("id").asText();
    }

    private Email createTestEmail(UUID accountId) {
        return emailRepository.save(Email.builder()
                .emailAccountId(accountId)
                .messageId("<" + UUID.randomUUID() + "@test.com>")
                .folder("INBOX")
                .fromAddress("sender@test.com")
                .fromPersonal("Sender")
                .toAddresses("recipient@test.com")
                .ccAddresses("")
                .subject("Test Email")
                .bodyText("Plain text body")
                .bodyHtml("<p>HTML body</p>")
                .snippet("Plain text body")
                .receivedAt(Instant.now())
                .isRead(false)
                .isStarred(false)
                .hasAttachments(false)
                .attachments("[]")
                .uid(1L)
                .processed(false)
                .build());
    }

    @Test
    void list_returnsPagedEmails() throws Exception {
        String token = registerAndGetToken("email-list@example.com");
        String accountId = createAccountAndGetId(token, "emaillist@test.com");
        createTestEmail(UUID.fromString(accountId));

        mockMvc.perform(get("/api/v1/email-accounts/" + accountId + "/emails")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].subject").value("Test Email"));
    }

    @Test
    void getById_returnsFullEmail() throws Exception {
        String token = registerAndGetToken("email-get@example.com");
        String accountId = createAccountAndGetId(token, "emailget@test.com");
        Email email = createTestEmail(UUID.fromString(accountId));

        mockMvc.perform(get("/api/v1/email-accounts/" + accountId + "/emails/" + email.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Test Email"))
                .andExpect(jsonPath("$.bodyText").value("Plain text body"))
                .andExpect(jsonPath("$.bodyHtml").value("<p>HTML body</p>"));
    }

    @Test
    void markRead_togglesReadFlag() throws Exception {
        String token = registerAndGetToken("email-read@example.com");
        String accountId = createAccountAndGetId(token, "emailread@test.com");
        Email email = createTestEmail(UUID.fromString(accountId));

        mockMvc.perform(patch("/api/v1/email-accounts/" + accountId + "/emails/" + email.getId() + "/read")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("read", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true));
    }

    @Test
    void toggleStar_flipsStarred() throws Exception {
        String token = registerAndGetToken("email-star@example.com");
        String accountId = createAccountAndGetId(token, "emailstar@test.com");
        Email email = createTestEmail(UUID.fromString(accountId));

        mockMvc.perform(patch("/api/v1/email-accounts/" + accountId + "/emails/" + email.getId() + "/star")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isStarred").value(true));
    }

    @Test
    void list_withFolderFilter_filtersCorrectly() throws Exception {
        String token = registerAndGetToken("email-folder@example.com");
        String accountId = createAccountAndGetId(token, "emailfolder@test.com");
        createTestEmail(UUID.fromString(accountId));

        mockMvc.perform(get("/api/v1/email-accounts/" + accountId + "/emails")
                        .header("Authorization", "Bearer " + token)
                        .param("folder", "INBOX"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(get("/api/v1/email-accounts/" + accountId + "/emails")
                        .header("Authorization", "Bearer " + token)
                        .param("folder", "SENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void list_unauthorized_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/email-accounts/00000000-0000-0000-0000-000000000000/emails"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getById_nonExistentEmail_returns404() throws Exception {
        String token = registerAndGetToken("email-404@example.com");
        String accountId = createAccountAndGetId(token, "email404@test.com");

        mockMvc.perform(get("/api/v1/email-accounts/" + accountId + "/emails/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void reprocess_resetsProcessedFlag() throws Exception {
        String token = registerAndGetToken("email-reprocess@example.com");
        String accountId = createAccountAndGetId(token, "emailreprocess@test.com");
        Email email = createTestEmail(UUID.fromString(accountId));
        email.setProcessed(true);
        emailRepository.save(email);

        mockMvc.perform(post("/api/v1/email-accounts/" + accountId + "/emails/" + email.getId() + "/reprocess")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(true));
    }
}
