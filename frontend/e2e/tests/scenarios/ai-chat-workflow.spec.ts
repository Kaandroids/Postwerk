/**
 * Scenario: AI Chat Assistant Workflow
 *
 * User opens AI chat, browses conversation history, loads a conversation,
 * interacts with planning phase, starts a new chat, and manages conversations.
 */
import { test, expect } from '../../fixtures/test-fixtures';
import { AiChatPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import {
  mockConversations,
  mockConversationDetail,
  mockConversationDetailPlanning,
} from '../../mocks';

const chatToggleSelector = 'button[aria-label="AI Assistant"]';

test.describe('Scenario: AI Chat Workflow', () => {
  test('user opens chat, browses history, loads conversation with tool calls', async ({
    authenticatedPage,
  }) => {
    const api = new MockApi();
    api
      .get(/\/api\/v1\/ai\/conversations\/conv-1$/, mockConversationDetail)
      .get('/api/v1/ai/conversations', mockConversations);
    await api.apply(authenticatedPage);

    await authenticatedPage.goto('/dashboard');
    const chat = new AiChatPage(authenticatedPage);

    // ── Step 1: Open chat panel via topbar button ──
    await authenticatedPage.locator(chatToggleSelector).click();
    await expect(chat.panel).toBeVisible();
    await expect(chat.overlay).toBeVisible();

    // ── Step 2: See welcome message ──
    await expect(chat.welcomeMessage).toBeVisible();
    await expect(chat.suggestionCards).toHaveCount(4);

    // ── Step 3: Open conversation history ──
    await chat.historyButton.click();
    await expect(chat.historyDropdown).toBeVisible();
    await expect(chat.historyItems).toHaveCount(2);
    await expect(chat.historyItemTitle(0)).toContainText('Spam-Filter erstellen');

    // ── Step 4: Load a conversation ──
    await chat.historyItems.nth(0).click();

    // ── Step 5: See messages and tool calls ──
    await expect(chat.userMessages).toHaveCount(1);
    await expect(chat.assistantMessages.first()).toBeVisible();
    await expect(chat.toolCalls).toHaveCount(1);

    // ── Step 6: Expand tool call to see details ──
    await chat.toolCallAt(0).click();
    await expect(chat.toolCallBodies.first()).toBeVisible();

    // ── Step 7: Start a new chat ──
    await chat.newChatButton.click();
    await expect(chat.welcomeMessage).toBeVisible();
    await expect(chat.userMessages).toHaveCount(0);

    // ── Step 8: Close chat ──
    await chat.closeButton.click();
    await expect(chat.panel).not.toBeVisible();
  });

  test('user interacts with planning phase — confirm and cancel', async ({
    authenticatedPage,
  }) => {
    const api = new MockApi();
    api
      .get(/\/api\/v1\/ai\/conversations\/conv-3$/, mockConversationDetailPlanning)
      .get('/api/v1/ai/conversations', [
        ...mockConversations,
        { id: 'conv-3', title: 'Automation planen', updatedAt: '2024-03-16T10:00:00Z' },
      ]);
    await api.apply(authenticatedPage);

    await authenticatedPage.goto('/dashboard');
    const chat = new AiChatPage(authenticatedPage);

    // ── Step 1: Open chat ──
    await authenticatedPage.locator(chatToggleSelector).click();
    await expect(chat.panel).toBeVisible();

    // ── Step 2: Open history and load the planning conversation ──
    await chat.historyButton.click();
    await chat.historyItems.filter({ hasText: 'Automation planen' }).click();

    // ── Step 3: Planning bar should be visible ──
    await expect(chat.planningBar).toBeVisible();
    await expect(chat.confirmButton).toBeVisible();
    await expect(chat.cancelButton).toBeVisible();

    // ── Step 4: Building bar should NOT be visible ──
    await expect(chat.buildingBar).not.toBeVisible();

    // ── Step 5: System message should show phase transition ──
    await expect(chat.systemMessages.first()).toBeVisible();
  });

  test('user deletes a conversation from history', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api
      .delete(/\/api\/v1\/ai\/conversations\/conv-1$/, {})
      .get('/api/v1/ai/conversations', mockConversations);
    await api.apply(authenticatedPage);

    await authenticatedPage.goto('/dashboard');
    const chat = new AiChatPage(authenticatedPage);

    // ── Step 1: Open chat and history ──
    await authenticatedPage.locator(chatToggleSelector).click();
    await chat.historyButton.click();
    await expect(chat.historyItems).toHaveCount(2);

    // ── Step 2: Delete first conversation (no confirm dialog — direct delete)
    await chat.historyItems.nth(0).hover();
    await chat.historyDeleteButton(0).click();

    // ── Step 3: Should have 1 conversation remaining
    await expect(chat.historyItems).toHaveCount(1);
  });
});
