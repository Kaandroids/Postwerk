package com.postwerk;

import com.postwerk.dto.EmailAccountRequest;
import com.postwerk.dto.auth.LoginRequest;
import com.postwerk.dto.auth.RegisterRequest;
import com.postwerk.model.*;
import com.postwerk.model.enums.AutomationStatus;
import com.postwerk.model.enums.AutomationType;
import com.postwerk.model.enums.NodeType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class TestFixtures {

    private TestFixtures() {}

    public static final String TEST_EMAIL = "test@example.com";
    public static final String TEST_PASSWORD = "SecureP@ss123!";
    public static final String TEST_NAME = "Test User";
    public static final String TEST_IP = "127.0.0.1";

    public static User createUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email(TEST_EMAIL)
                .passwordHash("$2a$10$encodedPasswordHash")
                .fullName(TEST_NAME)
                .company("Test Corp")
                .phone("+49123456789")
                .marketingOptIn(false)
                .privacyAcceptedAt(Instant.now())
                .termsAcceptedAt(Instant.now())
                .privacyVersion("2026-05")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    public static User createUser(String email) {
        User user = createUser();
        user.setEmail(email);
        return user;
    }

    public static EmailAccount createEmailAccount(UUID userId) {
        return EmailAccount.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .email("inbox@example.com")
                .displayName("Test Inbox")
                .color("#3b82f6")
                .readEnabled(true)
                .writeEnabled(true)
                .imapHost("imap.example.com")
                .imapPort(993)
                .imapUsername("inbox@example.com")
                .imapPassword("encrypted-imap-pass")
                .imapSsl(true)
                .smtpHost("smtp.example.com")
                .smtpPort(587)
                .smtpUsername("inbox@example.com")
                .smtpPassword("encrypted-smtp-pass")
                .smtpSsl(true)
                .syncFromDate(LocalDate.now().minusDays(30))
                .isDefault(true)
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    public static Email createEmail(UUID accountId) {
        return Email.builder()
                .id(UUID.randomUUID())
                .emailAccountId(accountId)
                .messageId("<" + UUID.randomUUID() + "@example.com>")
                .folder("INBOX")
                .fromAddress("sender@example.com")
                .fromPersonal("Sender Name")
                .toAddresses("recipient@example.com")
                .ccAddresses("")
                .subject("Test Email Subject")
                .bodyText("This is the plain text body of the email.")
                .bodyHtml("<p>This is the HTML body of the email.</p>")
                .snippet("This is the plain text body")
                .receivedAt(Instant.now())
                .isRead(false)
                .isStarred(false)
                .hasAttachments(false)
                .attachments("[]")
                .uid(12345L)
                .processed(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    public static Automation createAutomation(UUID userId) {
        return Automation.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Test Automation")
                .description("A test automation workflow")
                .type(AutomationType.EMAIL)
                .status(AutomationStatus.ACTIVE)
                .accountIds(new UUID[]{UUID.randomUUID()})
                .color("#3b82f6")
                .flowData("{}")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    public static AutomationNode createNode(Automation automation, NodeType type, String label) {
        return AutomationNode.builder()
                .id(UUID.randomUUID())
                .automation(automation)
                .nodeType(type)
                .label(label)
                .positionX(100.0)
                .positionY(200.0)
                .config("{}")
                .createdAt(Instant.now())
                .build();
    }

    public static AutomationEdge createEdge(Automation automation, AutomationNode source, AutomationNode target) {
        return createEdge(automation, source, target, "output");
    }

    public static AutomationEdge createEdge(Automation automation, AutomationNode source, AutomationNode target, String sourceHandle) {
        return AutomationEdge.builder()
                .id(UUID.randomUUID())
                .automation(automation)
                .sourceNode(source)
                .sourceHandle(sourceHandle)
                .targetNode(target)
                .targetHandle("input")
                .createdAt(Instant.now())
                .build();
    }

    public static Category createCategory(UUID userId) {
        return Category.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Test Category")
                .color("#10b981")
                .description("A test category for email classification")
                .positiveExample("Order confirmation, invoice received")
                .negativeExample("Newsletter, marketing email")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    public static RegisterRequest createRegisterRequest() {
        return new RegisterRequest(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, "Test Corp", "+49123456789", false, true);
    }

    public static RegisterRequest createRegisterRequest(String email) {
        return new RegisterRequest(TEST_NAME, email, TEST_PASSWORD, "Test Corp", "+49123456789", false, true);
    }

    public static LoginRequest createLoginRequest() {
        return new LoginRequest(TEST_EMAIL, TEST_PASSWORD);
    }

    public static LoginRequest createLoginRequest(String email, String password) {
        return new LoginRequest(email, password);
    }

    public static EmailAccountRequest createEmailAccountRequest() {
        return new EmailAccountRequest(
                "inbox@example.com", "Test Inbox", "#3b82f6",
                true, true,
                "imap.example.com", 993, "inbox@example.com", "imapPass123", true,
                "smtp.example.com", 587, "inbox@example.com", "smtpPass123", true,
                LocalDate.now().minusDays(30), false
        );
    }

    public static AiConversation createAiConversation(UUID userId) {
        return AiConversation.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .title("Test Conversation")
                .messages("[]")
                .phase(ConversationPhase.OPEN)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    public static AiConversation createAiConversation(UUID userId, Instant updatedAt) {
        AiConversation conv = createAiConversation(userId);
        conv.setUpdatedAt(updatedAt);
        conv.setCreatedAt(updatedAt);
        return conv;
    }

    public static Plan createPlan() {
        return Plan.builder()
                .id(UUID.randomUUID())
                .name("Test Plan")
                .tokenLimit(10000)
                .automationLimit(5)
                .emailAccountLimit(3)
                .price(new java.math.BigDecimal("9.99"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
