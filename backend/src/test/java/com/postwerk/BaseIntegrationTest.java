package com.postwerk;

import com.postwerk.config.TestContainersConfig;
import com.postwerk.dto.auth.AuthResponse;
import com.postwerk.dto.auth.LoginRequest;
import com.postwerk.dto.auth.RegisterRequest;
import com.postwerk.model.User;
import com.postwerk.model.enums.Role;
import com.postwerk.model.enums.StaffRole;
import com.postwerk.repository.UserRepository;
import com.postwerk.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@AutoConfigureMockMvc
@Transactional
@Tag("integration")
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected AuthService authService;

    @Autowired
    protected UserRepository userRepository;

    /** Registers a new account and marks its email verified (without logging in). */
    protected void registerVerified(String email) {
        RegisterRequest req = TestFixtures.createRegisterRequest(email);
        authService.register(req, TestFixtures.TEST_IP);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setEmailVerified(true);
        userRepository.saveAndFlush(user);
    }

    protected AuthResponse registerAndLogin(String email) {
        // Registration no longer issues tokens (account starts unverified); verify, then log in.
        registerVerified(email);
        return authService.login(new LoginRequest(email, TestFixtures.TEST_PASSWORD), TestFixtures.TEST_IP);
    }

    protected String registerAndGetToken(String email) {
        return registerAndLogin(email).accessToken();
    }

    protected AuthResponse registerAndMakeAdmin(String email) {
        registerAndLogin(email);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setRole(Role.ADMIN);
        user.setStaffRole(StaffRole.SUPER_ADMIN);
        userRepository.saveAndFlush(user);
        LoginRequest loginReq = new LoginRequest(email, TestFixtures.TEST_PASSWORD);
        return authService.login(loginReq, TestFixtures.TEST_IP);
    }
}
