import { type Locator, type Page } from '@playwright/test';

/** Page object for the INBOUND webhook TRIGGER node config panel. */
export class WebhookTriggerPanelPage {
  readonly page: Page;

  readonly urlInput: Locator;
  readonly copyButton: Locator;
  readonly regenerateButton: Locator;
  readonly saveHint: Locator;
  readonly parameterSetSelect: Locator;
  readonly authModeSelect: Locator;
  readonly headerNameInput: Locator;
  readonly secretState: Locator;
  readonly secretValue: Locator;
  readonly generateSecretButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.urlInput = page.locator('[data-testid="webhook-url"]');
    this.copyButton = page.locator('[data-testid="webhook-copy-btn"]');
    this.regenerateButton = page.locator('[data-testid="webhook-regenerate-btn"]');
    this.saveHint = page.locator('[data-testid="webhook-save-hint"]');
    this.parameterSetSelect = page.locator('[data-testid="webhook-paramset-select"]');
    this.authModeSelect = page.locator('[data-testid="webhook-authmode-select"]');
    this.headerNameInput = page.locator('[data-testid="webhook-headername-input"]');
    this.secretState = page.locator('[data-testid="webhook-secret-state"]');
    this.secretValue = page.locator('[data-testid="webhook-secret-value"]');
    this.generateSecretButton = page.locator('[data-testid="webhook-generate-secret-btn"]');
  }

  /** Double-click the trigger node to open its config panel. */
  async selectTriggerNode(label = 'Inbound Hook'): Promise<void> {
    const node = this.page.locator('.ae-node').filter({ hasText: label });
    await node.dblclick();
  }
}
