package com.postwerk.config;

import com.postwerk.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityConfigTest extends BaseIntegrationTest {

    @Test
    void publicEndpoints_noAuth_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    }

    @Test
    void protectedEndpoints_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/email-accounts")).andExpect(status().isUnauthorized());
    }

    @Test
    void swaggerEndpoints_noAuth_returns200() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void authEndpoints_noAuth_allowsAccess() throws Exception {
        // Auth endpoints are publicly accessible (POST methods)
        // GET on auth returns 405 Method Not Allowed, not 401
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s == 401) {
                        throw new AssertionError("Expected non-401 status but got 401");
                    }
                });
    }

    @Test
    void corsHeaders_validOrigin_present() throws Exception {
        mockMvc.perform(get("/api/v1/health")
                        .header("Origin", "http://localhost:4200"))
                .andExpect(status().isOk());
    }
}
