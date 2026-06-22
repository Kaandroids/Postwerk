package com.postwerk.controller;

import com.postwerk.BaseIntegrationTest;
import com.postwerk.TestFixtures;
import com.postwerk.dto.auth.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerIntegrationTest extends BaseIntegrationTest {

    @Test
    void register_validRequest_returns200WithTokens() throws Exception {
        var request = TestFixtures.createRegisterRequest("register1@example.com");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").isNumber());
    }

    @Test
    void register_duplicateEmail_returns401() throws Exception {
        var request = TestFixtures.createRegisterRequest("dup@example.com");
        // First registration
        authService.register(request, TestFixtures.TEST_IP);

        // Second registration with same email
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        var request = new RegisterRequest("Test", "not-an-email", "SecureP@ss1", null, null, false, true);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_weakPassword_returns400() throws Exception {
        var request = new RegisterRequest("Test", "weak@example.com", "short", null, null, false, true);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_validCredentials_returnsTokens() throws Exception {
        var registerReq = TestFixtures.createRegisterRequest("login1@example.com");
        authService.register(registerReq, TestFixtures.TEST_IP);

        var loginReq = TestFixtures.createLoginRequest("login1@example.com", TestFixtures.TEST_PASSWORD);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void login_invalidPassword_returns401() throws Exception {
        var registerReq = TestFixtures.createRegisterRequest("login2@example.com");
        authService.register(registerReq, TestFixtures.TEST_IP);

        var loginReq = TestFixtures.createLoginRequest("login2@example.com", "WrongPassword123!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_nonExistentUser_returns401() throws Exception {
        var loginReq = TestFixtures.createLoginRequest("nobody@example.com", "password123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refresh_validToken_returnsNewAccessToken() throws Exception {
        var authResp = registerAndLogin("refresh1@example.com");
        var refreshReq = new RefreshRequest(authResp.refreshToken());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        var refreshReq = new RefreshRequest("invalid-refresh-token");
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_validTokens_returns200() throws Exception {
        var authResp = registerAndLogin("logout1@example.com");
        var refreshReq = new RefreshRequest(authResp.refreshToken());
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + authResp.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    @Test
    void logout_missingToken_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void resetPassword_existingEmail_returns200() throws Exception {
        authService.register(TestFixtures.createRegisterRequest("reset1@example.com"), TestFixtures.TEST_IP);
        var req = new ResetPasswordRequest("reset1@example.com");
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset email sent"));
    }

    @Test
    void resetPassword_nonExistentEmail_returns200() throws Exception {
        var req = new ResetPasswordRequest("nouser@example.com");
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/email-accounts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_validToken_returns200() throws Exception {
        String token = registerAndGetToken("protected1@example.com");
        mockMvc.perform(get("/api/v1/email-accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
