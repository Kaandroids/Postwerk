import { type Locator, type Page } from '@playwright/test';

export class ComposePanelPage {
  readonly panel: Locator;
  readonly backdrop: Locator;
  readonly toInput: Locator;
  readonly ccInput: Locator;
  readonly bccInput: Locator;
  readonly subjectInput: Locator;
  readonly ccToggle: Locator;
  readonly sendBtn: Locator;
  readonly saveDraftBtn: Locator;
  readonly discardBtn: Locator;
  readonly attachBtn: Locator;
  readonly closeBtn: Locator;
  readonly composeBtn: Locator;
  readonly replyBtn: Locator;
  readonly forwardBtn: Locator;
  readonly attachments: Locator;

  constructor(private page: Page) {
    this.panel = page.locator('[data-testid="compose-panel"]');
    this.backdrop = page.locator('[data-testid="compose-backdrop"]');
    this.toInput = page.locator('[data-testid="compose-to"]');
    this.ccInput = page.locator('[data-testid="compose-cc"]');
    this.bccInput = page.locator('[data-testid="compose-bcc"]');
    this.subjectInput = page.locator('[data-testid="compose-subject"]');
    this.ccToggle = page.locator('[data-testid="compose-cc-toggle"]');
    this.sendBtn = page.locator('[data-testid="compose-send"]');
    this.saveDraftBtn = page.locator('[data-testid="compose-save-draft"]');
    this.discardBtn = page.locator('[data-testid="compose-discard"]');
    this.attachBtn = page.locator('[data-testid="compose-attach"]');
    this.closeBtn = page.locator('[data-testid="compose-close"]');
    this.composeBtn = page.locator('[data-testid="compose-btn"]');
    this.replyBtn = page.locator('[data-testid="reply-btn"]');
    this.forwardBtn = page.locator('[data-testid="forward-btn"]');
    this.attachments = page.locator('[data-testid="compose-attachment"]');
  }

  async fillTo(value: string) {
    await this.toInput.fill(value);
  }

  async fillSubject(value: string) {
    await this.subjectInput.fill(value);
  }
}
