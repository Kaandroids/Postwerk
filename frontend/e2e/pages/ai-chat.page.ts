import { Locator, Page } from '@playwright/test';

export class AiChatPage {
  // Panel
  readonly panel: Locator;
  readonly overlay: Locator;
  readonly headerTitle: Locator;

  // Header actions
  readonly newChatButton: Locator;
  readonly historyButton: Locator;
  readonly closeButton: Locator;

  // History
  readonly historyDropdown: Locator;
  readonly historyItems: Locator;
  readonly historyEmpty: Locator;

  // Messages
  readonly messagesContainer: Locator;
  readonly welcomeMessage: Locator;
  readonly suggestionCards: Locator;
  readonly userMessages: Locator;
  readonly assistantMessages: Locator;
  readonly systemMessages: Locator;
  readonly errorMessage: Locator;
  readonly retryButton: Locator;
  readonly thinkingDots: Locator;

  // Tool calls
  readonly toolCalls: Locator;
  readonly toolCallBodies: Locator;

  // Phase bars
  readonly planningBar: Locator;
  readonly buildingBar: Locator;
  readonly confirmButton: Locator;
  readonly cancelButton: Locator;

  // Model toggle
  readonly modelFlashButton: Locator;
  readonly modelProButton: Locator;

  // Input
  readonly messageInput: Locator;
  readonly sendButton: Locator;
  readonly stopButton: Locator;
  readonly disclaimer: Locator;

  constructor(private page: Page) {
    this.panel = page.locator('.chat-panel');
    this.overlay = page.locator('.chat-overlay');
    this.headerTitle = page.locator('.chat-title');

    this.newChatButton = page.locator('.chat-header-actions .chat-icon-btn').first();
    this.historyButton = page.locator('.chat-header-actions .chat-icon-btn').nth(1);
    this.closeButton = page.locator('.chat-header-actions .chat-icon-btn').last();

    this.historyDropdown = page.locator('.chat-history');
    this.historyItems = page.locator('.chat-history-item');
    this.historyEmpty = page.locator('.chat-history-empty');

    this.messagesContainer = page.locator('.chat-messages');
    this.welcomeMessage = page.locator('.chat-msg[data-role="assistant"] .chat-msg-content').first();
    this.suggestionCards = page.locator('.chat-suggestion-card');
    this.userMessages = page.locator('.chat-msg[data-role="user"]');
    this.assistantMessages = page.locator('.chat-msg[data-role="assistant"]');
    this.systemMessages = page.locator('[data-testid="chat-system-msg"]');
    this.errorMessage = page.locator('.chat-msg-error');
    this.retryButton = page.locator('.chat-retry-btn');
    this.thinkingDots = page.locator('.chat-thinking');

    this.toolCalls = page.locator('.chat-tool-call');
    this.toolCallBodies = page.locator('.chat-tool-body');

    this.planningBar = page.locator('[data-testid="chat-phase-bar-planning"]');
    this.buildingBar = page.locator('[data-testid="chat-phase-bar-building"]');
    this.confirmButton = page.locator('[data-testid="chat-phase-confirm-btn"]');
    this.cancelButton = page.locator('[data-testid="chat-phase-cancel-btn"]');

    this.modelFlashButton = page.locator('[data-testid="chat-model-flash"]');
    this.modelProButton = page.locator('[data-testid="chat-model-pro"]');

    this.messageInput = page.locator('.chat-input');
    this.sendButton = page.locator('.chat-send-btn');
    this.stopButton = page.locator('.chat-stop-btn');
    this.disclaimer = page.locator('.chat-disclaimer');
  }

  historyItemTitle(index: number): Locator {
    return this.historyItems.nth(index).locator('.chat-history-title');
  }

  historyDeleteButton(index: number): Locator {
    return this.historyItems.nth(index).locator('.chat-history-delete');
  }

  toolCallAt(index: number): Locator {
    return this.toolCalls.nth(index);
  }

  toolCallName(index: number): Locator {
    return this.toolCalls.nth(index).locator('.chat-tool-name');
  }

  toolCallAction(index: number): Locator {
    return this.toolCalls.nth(index).locator('.chat-tool-action');
  }
}
