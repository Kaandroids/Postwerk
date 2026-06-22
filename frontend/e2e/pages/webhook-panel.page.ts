import { type Locator, type Page } from '@playwright/test';

export class WebhookPanelPage {
  readonly page: Page;

  // Section toggles
  readonly sectionRequest: Locator;
  readonly sectionAuth: Locator;
  readonly sectionBody: Locator;
  readonly sectionRetry: Locator;
  readonly sectionResponse: Locator;

  // Request fields
  readonly methodSelect: Locator;
  readonly urlInput: Locator;

  // Auth fields
  readonly authSelect: Locator;
  readonly secretSelect: Locator;
  readonly authTokenInput: Locator;
  readonly authUsernameInput: Locator;
  readonly authPasswordInput: Locator;
  readonly authHeaderNameInput: Locator;
  readonly apikeyTokenInput: Locator;

  // Body fields
  readonly bodyInput: Locator;

  // Retry fields
  readonly timeoutInput: Locator;
  readonly retryCountInput: Locator;
  readonly retryDelayInput: Locator;
  readonly retry5xx: Locator;
  readonly retry429: Locator;
  readonly retryNetwork: Locator;

  // Response schema
  readonly schemaRows: Locator;
  readonly addSchemaButton: Locator;

  constructor(page: Page) {
    this.page = page;

    this.sectionRequest = page.locator('[data-testid="webhook-sec-request"]');
    this.sectionAuth = page.locator('[data-testid="webhook-sec-auth"]');
    this.sectionBody = page.locator('[data-testid="webhook-sec-body"]');
    this.sectionRetry = page.locator('[data-testid="webhook-sec-retry"]');
    this.sectionResponse = page.locator('[data-testid="webhook-sec-response"]');

    this.methodSelect = page.locator('[data-testid="webhook-method-select"]');
    this.urlInput = page.locator('[data-testid="webhook-url-input"]');

    this.authSelect = page.locator('[data-testid="webhook-auth-select"]');
    this.secretSelect = page.locator('[data-testid="webhook-secret-select"]');
    this.authTokenInput = page.locator('[data-testid="webhook-auth-token"]');
    this.authUsernameInput = page.locator('[data-testid="webhook-auth-username"]');
    this.authPasswordInput = page.locator('[data-testid="webhook-auth-password"]');
    this.authHeaderNameInput = page.locator('[data-testid="webhook-auth-header-name"]');
    this.apikeyTokenInput = page.locator('[data-testid="webhook-apikey-token"]');

    this.bodyInput = page.locator('[data-testid="webhook-body-input"]');

    this.timeoutInput = page.locator('[data-testid="webhook-timeout-input"]');
    this.retryCountInput = page.locator('[data-testid="webhook-retry-count"]');
    this.retryDelayInput = page.locator('[data-testid="webhook-retry-delay"]');
    this.retry5xx = page.locator('[data-testid="webhook-retry-5xx"]');
    this.retry429 = page.locator('[data-testid="webhook-retry-429"]');
    this.retryNetwork = page.locator('[data-testid="webhook-retry-network"]');

    this.schemaRows = page.locator('[data-testid="webhook-resp-schema-row"]');
    this.addSchemaButton = page.locator('[data-testid="webhook-add-resp-schema"]');
  }

  schemaNameInput(index: number): Locator {
    return this.schemaRows.nth(index).locator('[data-testid="webhook-resp-name"]');
  }

  schemaConditionInput(index: number): Locator {
    return this.schemaRows.nth(index).locator('[data-testid="webhook-resp-condition"]');
  }

  schemaParameterSetSelect(index: number): Locator {
    return this.schemaRows.nth(index).locator('[data-testid="webhook-resp-ps"]');
  }

  schemaRemoveButton(index: number): Locator {
    return this.schemaRows.nth(index).locator('[data-testid="webhook-resp-remove"]');
  }

  async openSection(section: Locator): Promise<void> {
    await section.click();
  }

  async selectWebhookNode(): Promise<void> {
    const webhookNode = this.page.locator('.ae-node').filter({ hasText: 'Webhook' });
    await webhookNode.dblclick();
  }
}
