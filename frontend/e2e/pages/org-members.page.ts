import { Page, Locator } from '@playwright/test';

/** Page object for the organization / team management page (#4 Phase D). */
export class OrgMembersPage {
  readonly page: Page;
  readonly memberRows: Locator;
  readonly inviteEmail: Locator;
  readonly inviteRole: Locator;
  readonly inviteSubmit: Locator;
  readonly leaveBtn: Locator;
  readonly grantsDrawer: Locator;
  readonly grantRows: Locator;
  readonly grantsSave: Locator;

  constructor(page: Page) {
    this.page = page;
    this.memberRows = page.locator('[data-testid="org-member-row"]');
    this.inviteEmail = page.locator('[data-testid="org-invite-email"]');
    this.inviteRole = page.locator('[data-testid="org-invite-role"]');
    this.inviteSubmit = page.locator('[data-testid="org-invite-submit"]');
    this.leaveBtn = page.locator('[data-testid="org-leave"]');
    this.grantsDrawer = page.locator('[data-testid="org-grants-drawer"]');
    this.grantRows = page.locator('[data-testid="org-grant-row"]');
    this.grantsSave = page.locator('[data-testid="org-grants-save"]');
  }

  row(index: number): Locator {
    return this.memberRows.nth(index);
  }

  /** Opens the mailbox-grant drawer for the member at the given row index. */
  async openGrants(index: number): Promise<void> {
    await this.row(index).locator('[data-testid="org-member-grants"]').click();
    await this.grantsDrawer.waitFor({ state: 'visible' });
  }

  async navigate(): Promise<void> {
    await this.page.goto('/dashboard/organization');
    await this.page.waitForLoadState('networkidle');
  }
}
