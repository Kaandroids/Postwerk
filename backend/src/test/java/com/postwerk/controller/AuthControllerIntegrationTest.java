package com.postwerk.controller;

import com.postwerk.BaseIntegrationTest;
import com.postwerk.TestFixtures;
import com.postwerk.dto.auth.*;
import com.postwerk.model.User;
import com.postwerk.service.VerificationTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private VerificationTokenService verificationTokenService;

    @Test
    void register_validRequest_returns200VerificationRequired() throws Exception {
        var request = TestFixtures.createRegisterRequest("register1@example.com");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationRequired").value(true))
                .andExpect(jsonPath("$.email").value("register1@example.com"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void register_duplicateEmail_returns401() throws Exception {
        var request = TestFixtures.createRegisterRequest("dup@example.com");
        authService.register(request, TestFixtures.TEST_IP);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        var request = new RegisterRequest("Test", "not-an-email", "SecureP@ss1", null, null, false, true, null, null);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_weakPassword_returns400() throws Exception {
        var request = new RegisterRequest("Test", "weak@example.com", "short", null, null, false, true, null, null);
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
        registerVerified("login1@example.com");

        var loginReq = TestFixtures.createLoginRequest("login1@example.com", TestFixtures.TEST_PASSWORD);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void login_unverifiedEmail_returns403WithCode() throws Exception {
        // Register but do NOT verify.
        authService.register(TestFixtures.createRegisterRequest("unverified@example.com"), TestFixtures.TEST_IP);

        var loginReq = TestFixtures.createLoginRequest("unverified@example.com", TestFixtures.TEST_PASSWORD);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
                .andExpect(jsonPath("$.email").value("unverified@example.com"));
    }

    @Test
    void login_invalidPassword_returns401() throws Exception {
        registerVerified("login2@example.com");

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
    void verifyEmail_validToken_returnsTokensAndVerifiesUser() throws Exception {
        authService.register(TestFixtures.createRegisterRequest("verify1@example.com"), TestFixtures.TEST_IP);
        User user = userRepository.findByEmail("verify1@example.com").orElseThrow();
        String token = verificationTokenService.createVerificationToken(user.getId());

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailRequest(token))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        // The account can now log in.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                TestFixtures.createLoginRequest("verify1@example.com", TestFixtures.TEST_PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    void verifyEmail_invalidToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailRequest("00000000-0000-0000-0000-000000000000"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resendVerification_returns200() throws Exception {
        authService.register(TestFixtures.createRegisterRequest("resend1@example.com"), TestFixtures.TEST_IP);
        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResendVerificationRequest("resend1@example.com", "en"))))
                .andExpect(status().isOk());
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
        registerVerified("reset1@example.com");
        var req = new ResetPasswordRequest("reset1@example.com", null);
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("password reset email")));
    }

    @Test
    void resetPassword_nonExistentEmail_returns200() throws Exception {
        var req = new ResetPasswordRequest("nouser@example.com", null);
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void resetPasswordConfirm_validToken_setsNewPasswordAndLoginWorks() throws Exception {
        registerVerified("resetc@example.com");
        User user = userRepository.findByEmail("resetc@example.com").orElseThrow();
        String token = verificationTokenService.createResetToken(user.getId());
        String newPassword = "BrandNewP@ss1";

        mockMvc.perform(post("/api/v1/auth/reset-password/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetConfirmRequest(token, newPassword))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                TestFixtures.createLoginRequest("resetc@example.com", newPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void resetPasswordConfirm_invalidToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordResetConfirmRequest("00000000-0000-0000-0000-000000000000", "BrandNewP@ss1"))))
                .andExpect(status().isUnauthorized());
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
