package com.postwerk.service;

import com.postwerk.dto.ConnectionTestRequest;
import com.postwerk.dto.ConnectionTestResponse;
import com.postwerk.util.HostSecurityValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionTestServiceTest {

    private ConnectionTestService service;

    @BeforeEach
    void setUp() {
        service = new ConnectionTestService(new HostSecurityValidator());
    }

    @Test
    void validateHost_loopback_rejectsConnection() {
        var req = new ConnectionTestRequest("127.0.0.1", 993, "user", "pass", true, "imap");
        ConnectionTestResponse resp = service.test(req);
        assertThat(resp.success()).isFalse();
        assertThat(resp.message()).contains("internal/private");
    }

    @Test
    void validateHost_siteLocal10_rejectsConnection() {
        var req = new ConnectionTestRequest("10.0.0.1", 993, "user", "pass", true, "imap");
        ConnectionTestResponse resp = service.test(req);
        assertThat(resp.success()).isFalse();
        assertThat(resp.message()).contains("internal/private");
    }

    @Test
    void validateHost_siteLocal172_rejectsConnection() {
        var req = new ConnectionTestRequest("172.16.0.1", 993, "user", "pass", true, "imap");
        ConnectionTestResponse resp = service.test(req);
        assertThat(resp.success()).isFalse();
        assertThat(resp.message()).contains("internal/private");
    }

    @Test
    void validateHost_siteLocal192_rejectsConnection() {
        var req = new ConnectionTestRequest("192.168.1.1", 993, "user", "pass", true, "imap");
        ConnectionTestResponse resp = service.test(req);
        assertThat(resp.success()).isFalse();
        assertThat(resp.message()).contains("internal/private");
    }

    @Test
    void validateHost_linkLocal_rejectsConnection() {
        var req = new ConnectionTestRequest("169.254.1.1", 993, "user", "pass", true, "imap");
        ConnectionTestResponse resp = service.test(req);
        assertThat(resp.success()).isFalse();
        assertThat(resp.message()).contains("internal/private");
    }

    @Test
    void validateHost_metadataAddress_rejectsConnection() {
        var req = new ConnectionTestRequest("169.254.169.254", 993, "user", "pass", true, "imap");
        ConnectionTestResponse resp = service.test(req);
        assertThat(resp.success()).isFalse();
        assertThat(resp.message()).contains("internal/private");
    }

    @Test
    void validateHost_localhostName_rejectsConnection() {
        var req = new ConnectionTestRequest("localhost", 993, "user", "pass", true, "imap");
        ConnectionTestResponse resp = service.test(req);
        assertThat(resp.success()).isFalse();
        assertThat(resp.message()).contains("internal/private");
    }

    @Test
    void validateHost_nullHost_returnsError() {
        var req = new ConnectionTestRequest(null, 993, "user", "pass", true, "imap");
        ConnectionTestResponse resp = service.test(req);
        assertThat(resp.success()).isFalse();
    }

    @Test
    void validateHost_blankHost_returnsError() {
        var req = new ConnectionTestRequest("   ", 993, "user", "pass", true, "imap");
        ConnectionTestResponse resp = service.test(req);
        assertThat(resp.success()).isFalse();
        // Blank/unresolvable hosts are now rejected by the shared SSRF guard with a generic message.
        assertThat(resp.message()).isNotBlank();
    }

    @Test
    void test_unknownType_returnsError() {
        // Using a public IP that will fail connection but won't be blocked by SSRF
        var req = new ConnectionTestRequest("mail.example.com", 993, "user", "pass", true, "ftp");
        ConnectionTestResponse resp = service.test(req);
        // Will either fail with unknown type or connection error (since host is valid)
        assertThat(resp.success()).isFalse();
    }

    @Test
    void test_imapConnection_returnsResponseStructure() {
        // This will fail because the IMAP server doesn't exist, but validates the code path
        var req = new ConnectionTestRequest("nonexistent.example.com", 993, "user", "pass", true, "imap");
        ConnectionTestResponse resp = service.test(req);
        assertThat(resp).isNotNull();
        assertThat(resp.success()).isFalse();
        assertThat(resp.message()).isNotBlank();
    }

    @Test
    void test_smtpConnection_returnsResponseStructure() {
        var req = new ConnectionTestRequest("nonexistent.example.com", 587, "user", "pass", true, "smtp");
        ConnectionTestResponse resp = service.test(req);
        assertThat(resp).isNotNull();
        assertThat(resp.success()).isFalse();
        assertThat(resp.message()).isNotBlank();
    }
}
