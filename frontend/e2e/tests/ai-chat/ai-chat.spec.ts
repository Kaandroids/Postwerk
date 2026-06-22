import { test, expect } from '../../fixtures/test-fixtures';
import { AiChatPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import {
  mockConversations,
  mockConversationDetail,
  mockConversationDetailPlanning,
  mockEmptyConversations,
} from '../../mocks';

test.describe('AI Chat Panel', () => {
  let chatPage: AiChatPage;

  test.beforeEach(async ({ authenticatedPage }) => {
    chatPage = new AiChatPage(authenticatedPage);
    const api = new MockApi();
    api.get('/api/v1/ai/conversations', mockConversations);
    await api.apply(authenticatedPage);
    await authenticatedPage.goto('/dashboard');
  });

  async function openChat(authenticatedPage: import('@playwright/test').Page) {
    await authenticatedPage.locator('button[aria-label="AI Assistant"]').click();
    await expect(chatPage.panel).toBeVisible();
  }

  // ─── Panel open/close ─────────────────────────────

  test('should open chat panel via topbar button', async ({ authenticatedPage }) => {
    await openChat(authenticatedPage);
    await expect(chatPage.headerTitle).toBeVisible();
    await expect(chatPage.messageInput).toBeVisible();
  });

  test('should close chat panel via close button', async ({ authenticatedPage }) => {
    await openChat(authenticatedPage);
    await chatPage.closeButton.click();
    await expect(chatPage.panel).not.toBeVisible();
  });

  test('should close chat panel via overlay click', async ({ authenticatedPage }) => {
    await openChat(authenticatedPage);
    await chatPage.overlay.click({ position: { x: 10, y: 10 } });
    await expect(chatPage.panel).not.toBeVisible();
  });

  // ─── Welcome state ────────────────────────────────

  test('should display welcome message and suggestions', async ({ authenticatedPage }) => {
    await openChat(authenticatedPage);
    await expect(chatPage.welcomeMessage).toBeVisible();
    await expect(chatPage.suggestionCards).toHaveCount(4);
  });

  test('should have send button disabled when input is empty', async ({ authenticatedPage }) => {
    await openChat(authenticatedPage);
    await expect(chatPage.sendButton).toBeDisabled();
  });

  test('should enable send button when text is entered', async ({ authenticatedPage }) => {
    await openChat(authenticatedPage);
    await chatPage.messageInput.fill('Hallo');
    await expect(chatPage.sendButton).toBeEnabled();
  });

  // ─── Conversation history ─────────────────────────

  test('should display conversation history', async ({ authenticatedPage }) => {
    await openChat(authenticatedPage);
    await chatPage.historyButton.click();
    await expect(chatPage.historyDropdown).toBeVisible();
    await expect(chatPage.historyItems).toHaveCount(2);
    await expect(chatPage.historyItemTitle(0)).toContainText('Spam-Filter erstellen');
    await expect(chatPage.historyItemTitle(1)).toContainText('Kategorie anlegen');
  });

  test('should show empty history state', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api.get('/api/v1/ai/conversations', mockEmptyConversations);
    await api.apply(authenticatedPage);

    await openChat(authenticatedPage);
    await chatPage.historyButton.click();
    await expect(chatPage.historyEmpty).toBeVisible();
  });

  test('should load conversation from history', async ({ authenticatedPage }) => {
    const api = new MockApi();
    // Specific pattern FIRST (FIFO matching)
    api
      .get(/\/api\/v1\/ai\/conversations\/conv-1$/, mockConversationDetail)
      .get('/api/v1/ai/conversations', mockConversations);
    await api.apply(authenticatedPage);

    await openChat(authenticatedPage);
    await chatPage.historyButton.click();
    await chatPage.historyItems.first().click();

    await expect(chatPage.historyDropdown).not.toBeVisible();
    await expect(chatPage.userMessages).toHaveCount(1);
    await expect(chatPage.userMessages.first().locator('.chat-msg-content')).toContainText('Erstelle einen Filter');
  });

  test('should delete conversation from history', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api
      .delete(/\/api\/v1\/ai\/conversations\/conv-1$/, {})
      .get('/api/v1/ai/conversations', mockConversations);
    await api.apply(authenticatedPage);

    await openChat(authenticatedPage);
    await chatPage.historyButton.click();
    await chatPage.historyItems.first().hover();
    await chatPage.historyDeleteButton(0).click();
    await expect(chatPage.historyItems).toHaveCount(1);
  });

  // ─── Loaded conversation with tool calls ──────────

  test('should display tool calls in loaded conversation', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api
      .get(/\/api\/v1\/ai\/conversations\/conv-1$/, mockConversationDetail)
      .get('/api/v1/ai/conversations', mockConversations);
    await api.apply(authenticatedPage);

    await openChat(authenticatedPage);
    await chatPage.historyButton.click();
    await chatPage.historyItems.first().click();

    await expect(chatPage.toolCalls).toHaveCount(1);
    await expect(chatPage.toolCallName(0)).toContainText('Spam');
  });

  test('should expand tool call details on click', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api
      .get(/\/api\/v1\/ai\/conversations\/conv-1$/, mockConversationDetail)
      .get('/api/v1/ai/conversations', mockConversations);
    await api.apply(authenticatedPage);

    await openChat(authenticatedPage);
    await chatPage.historyButton.click();
    await chatPage.historyItems.first().click();

    await expect(chatPage.toolCallBodies).toHaveCount(0);
    await chatPage.toolCallAt(0).click();
    await expect(chatPage.toolCallBodies).toHaveCount(1);
  });

  // ─── Planning phase ───────────────────────────────

  test('should show planning bar and system messages for PLANNING phase', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api
      .get(/\/api\/v1\/ai\/conversations\/conv-3$/, mockConversationDetailPlanning)
      .get('/api/v1/ai/conversations', [
        { id: 'conv-3', title: 'Automation planen', updatedAt: '2024-03-16T10:00:00Z' },
        ...mockConversations,
      ]);
    await api.apply(authenticatedPage);

    await openChat(authenticatedPage);
    await chatPage.historyButton.click();
    await chatPage.historyItems.first().click();

    await expect(chatPage.systemMessages).toHaveCount(1);
    await expect(chatPage.planningBar).toBeVisible();
    await expect(chatPage.confirmButton).toBeVisible();
    await expect(chatPage.cancelButton).toBeVisible();
  });

  // ─── New chat ─────────────────────────────────────

  test('should start new chat and clear messages', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api
      .get(/\/api\/v1\/ai\/conversations\/conv-1$/, mockConversationDetail)
      .get('/api/v1/ai/conversations', mockConversations);
    await api.apply(authenticatedPage);

    await openChat(authenticatedPage);
    await chatPage.historyButton.click();
    await chatPage.historyItems.first().click();

    await expect(chatPage.userMessages).toHaveCount(1);

    await chatPage.newChatButton.click();
    await expect(chatPage.welcomeMessage).toBeVisible();
    await expect(chatPage.suggestionCards).toHaveCount(4);
  });

  // ─── Model toggle ──────────────────────────────────

  test('should display model toggle with Flash selected by default', async ({ authenticatedPage }) => {
    await openChat(authenticatedPage);
    await expect(chatPage.modelFlashButton).toBeVisible();
    await expect(chatPage.modelProButton).toBeVisible();
    await expect(chatPage.modelFlashButton).toHaveClass(/active/);
    await expect(chatPage.modelProButton).not.toHaveClass(/active/);
  });

  test('should switch to Pro model on click', async ({ authenticatedPage }) => {
    await openChat(authenticatedPage);
    await chatPage.modelProButton.click();
    await expect(chatPage.modelProButton).toHaveClass(/active/);
    await expect(chatPage.modelFlashButton).not.toHaveClass(/active/);
  });

  // ─── Input area ───────────────────────────────────

  test('should clear input and show disclaimer', async ({ authenticatedPage }) => {
    await openChat(authenticatedPage);
    await expect(chatPage.disclaimer).toBeVisible();
    await expect(chatPage.messageInput).toHaveValue('');
  });
});
