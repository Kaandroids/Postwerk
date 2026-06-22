import { Page, Locator } from '@playwright/test';

/**
 * Page object for the topbar organization switcher: active-org selection, the pending-invitation
 * section (accept/decline) and the create-org affordance (incl. the free-org limit hint).
 */
export class OrgSwitcherPage {
  readonly page: Page;
  readonly trigger: Locator;
  readonly badge: Locator;
  readonly invitations: Locator;
  readonly options: Locator;
  readonly createOpen: Locator;
  readonly createInput: Locator;
  readonly createSubmit: Locator;
  readonly createError: Locator;
  readonly createLimit: Locator;

  constructor(page: Page) {
    this.page = page;
    this.trigger = page.locator('[data-testid="org-switcher"]');
    this.badge = page.locator('[data-testid="org-invite-badge"]');
    this.invitations = page.locator('[data-testid="org-invitation"]');
    this.options = page.locator('[data-testid="org-option"]');
    this.createOpen = page.locator('[data-testid="org-create-open"]');
    this.createInput = page.locator('[data-testid="org-create-input"]');
    this.createSubmit = page.locator('[data-testid="org-create-submit"]');
    this.createError = page.locator('[data-testid="org-create-error"]');
    this.createLimit = page.locator('[data-testid="org-create-limit"]');
  }

  /** Opens the dropdown (idempotent — the org option list signals the open state). */
  async open(): Promise<void> {
    if (await this.options.first().isVisible().catch(() => false)) return;
    await this.trigger.click();
    await this.options.first().waitFor({ state: 'visible' });
  }

  invitation(index: number): Locator {
    return this.invitations.nth(index);
  }

  acceptBtn(index: number): Locator {
    return this.invitation(index).locator('[data-testid="org-invitation-accept"]');
  }

  declineBtn(index: number): Locator {
    return this.invitation(index).locator('[data-testid="org-invitation-decline"]');
  }
}
