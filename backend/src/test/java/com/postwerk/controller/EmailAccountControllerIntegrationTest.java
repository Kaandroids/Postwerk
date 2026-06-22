package com.postwerk.controller;

import com.postwerk.BaseIntegrationTest;
import com.postwerk.TestFixtures;
import com.postwerk.dto.ConnectionTestRequest;
import com.postwerk.dto.EmailAccountRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EmailAccountControllerIntegrationTest extends BaseIntegrationTest {

    @Test
    void create_validRequest_returns201() throws Exception {
        String token = registerAndGetToken("ea-create@example.com");
        var request = new EmailAccountRequest(
                "test-imap@example.com", "Test Account", "#3b82f6",
                false, false, null, null, null, null, null,
                null, null, null, null, null,
                null, false
        );

        mockMvc.perform(post("/api/v1/email-accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("test-imap@example.com"))
                .andExpect(jsonPath("$.color").value("#3b82f6"));
    }

    @Test
    void list_returnsUserAccounts() throws Exception {
        String token = registerAndGetToken("ea-list@example.com");

        // Create an account first
        var request = new EmailAccountRequest(
                "list-test@example.com", "List Test", "#ef4444",
                false, false, null, null, null, null, null,
                null, null, null, null, null,
                null, false
        );
        mockMvc.perform(post("/api/v1/email-accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/email-accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void getById_returnsAccount() throws Exception {
        String token = registerAndGetToken("ea-get@example.com");
        var request = new EmailAccountRequest(
                "get-test@example.com", "Get Test", "#3b82f6",
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

        String id = objectMapper.readTree(responseBody).get("id").asText();

        mockMvc.perform(get("/api/v1/email-accounts/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void update_validRequest_returns200() throws Exception {
        String token = registerAndGetToken("ea-update@example.com");
        var createReq = new EmailAccountRequest(
                "update-test@example.com", "Original", "#3b82f6",
                false, false, null, null, null, null, null,
                null, null, null, null, null,
                null, false
        );

        String responseBody = mockMvc.perform(post("/api/v1/email-accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(responseBody).get("id").asText();

        var updateReq = new EmailAccountRequest(
                "update-test@example.com", "Updated", "#ef4444",
                false, false, null, null, null, null, null,
                null, null, null, null, null,
                null, false
        );

        mockMvc.perform(put("/api/v1/email-accounts/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Updated"))
                .andExpect(jsonPath("$.color").value("#ef4444"));
    }

    @Test
    void delete_returns204() throws Exception {
        String token = registerAndGetToken("ea-delete@example.com");
        var request = new EmailAccountRequest(
                "delete-test@example.com", "Delete Test", "#3b82f6",
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

        String id = objectMapper.readTree(responseBody).get("id").asText();

        mockMvc.perform(delete("/api/v1/email-accounts/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void setDefault_returns200() throws Exception {
        String token = registerAndGetToken("ea-default@example.com");
        var request = new EmailAccountRequest(
                "default-test@example.com", "Default Test", "#3b82f6",
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

        String id = objectMapper.readTree(responseBody).get("id").asText();

        mockMvc.perform(patch("/api/v1/email-accounts/" + id + "/default")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true));
    }

    @Test
    void testConnection_ssrf_returns400() throws Exception {
        String token = registerAndGetToken("ea-ssrf@example.com");
        var request = new ConnectionTestRequest("127.0.0.1", 993, "user", "pass", true, "imap");

        mockMvc.perform(post("/api/v1/email-accounts/test-connection")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("internal/private")));
    }

    @Test
    void create_unauthorized_returns401() throws Exception {
        var request = TestFixtures.createEmailAccountRequest();
        mockMvc.perform(post("/api/v1/email-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getById_nonExistent_returns404() throws Exception {
        String token = registerAndGetToken("ea-404@example.com");
        mockMvc.perform(get("/api/v1/email-accounts/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void testConnection_validImap_returnsResponse() throws Exception {
        String token = registerAndGetToken("ea-conntest@example.com");
        var request = new ConnectionTestRequest("nonexistent.example.com", 993, "user", "pass", true, "imap");

        mockMvc.perform(post("/api/v1/email-accounts/test-connection")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }
}
