import { Locator, Page } from '@playwright/test';

export class WizardPage {
  readonly page: Page;
  readonly flowPage: Locator;
  readonly chatIntro: Locator;
  readonly chatMessages: Locator;
  readonly composerInput: Locator;
  readonly composerSend: Locator;
  readonly chatLoading: Locator;
  readonly chatTools: Locator;
  readonly canvas: Locator;
  readonly howCard: Locator;
  readonly ctaRegister: Locator;
  readonly ctaContinue: Locator;
  readonly snackbar: Locator;
  readonly summaryBadge: Locator;
  readonly ctaCorner: Locator;

  constructor(page: Page) {
    this.page = page;
    this.flowPage = page.getByTestId('wizard-flow-page');
    this.chatIntro = page.getByTestId('wizard-chat-intro');
    this.chatMessages = page.getByTestId('wizard-chat-messages');
    this.composerInput = page.getByTestId('wizard-composer-input');
    this.composerSend = page.getByTestId('wizard-composer-send');
    this.chatLoading = page.getByTestId('wizard-chat-loading');
    this.chatTools = page.getByTestId('wizard-chat-tools');
    this.canvas = page.getByTestId('wizard-phase-canvas');
    this.howCard = page.getByTestId('wizard-how-card');
    this.ctaRegister = page.getByTestId('wizard-cta-register');
    this.ctaContinue = page.getByTestId('wizard-cta-continue');
    this.snackbar = page.getByTestId('wizard-snackbar');
    this.summaryBadge = page.getByTestId('wizard-summary-badge');
    this.ctaCorner = page.getByTestId('wizard-cta-corner');
  }

  async goto(): Promise<void> {
    await this.page.goto('/getstarted');
  }

  async sendMessage(text: string): Promise<void> {
    await this.composerInput.fill(text);
    await this.composerSend.click();
  }

  getNode(nodeId: string): Locator {
    return this.page.getByTestId(`wizard-node-${nodeId}`);
  }

  getEdge(edgeId: string): Locator {
    return this.page.getByTestId(`wizard-edge-${edgeId}`);
  }
}
