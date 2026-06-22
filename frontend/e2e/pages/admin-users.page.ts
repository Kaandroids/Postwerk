import { Locator, Page } from '@playwright/test';

export class AdminUsersPage {
  readonly searchInput: Locator;
  readonly roleFilter: Locator;
  readonly statusFilter: Locator;
  readonly userRows: Locator;
  readonly drawer: Locator;
  readonly roleToggleButtons: Locator;
  readonly disableButtons: Locator;
  readonly resetPasswordButton: Locator;
  readonly actionFlash: Locator;
  readonly sessionsCount: Locator;
  readonly revokeSessionsButton: Locator;
  readonly staffNotes: Locator;
  readonly noteInput: Locator;
  readonly noteAddButton: Locator;
  readonly noteItems: Locator;
  readonly noteDeleteButtons: Locator;

  constructor(private page: Page) {
    this.searchInput = page.locator('[data-testid="admin-user-search"]');
    this.roleFilter = page.locator('[data-testid="admin-role-filter"]');
    this.statusFilter = page.locator('[data-testid="admin-status-filter"]');
    this.userRows = page.locator('[data-testid="admin-user-row"]');
    this.drawer = page.locator('[data-testid="admin-user-drawer"]');
    this.roleToggleButtons = page.locator('[data-testid="admin-role-toggle"]');
    this.disableButtons = page.locator('[data-testid="admin-disable-btn"]');
    this.resetPasswordButton = page.locator('[data-testid="admin-reset-password-btn"]');
    this.actionFlash = page.locator('[data-testid="admin-action-flash"]');
    this.sessionsCount = page.locator('[data-testid="admin-sessions-count"]');
    this.revokeSessionsButton = page.locator('[data-testid="admin-revoke-sessions-btn"]');
    this.staffNotes = page.locator('[data-testid="admin-staff-notes"]');
    this.noteInput = page.locator('[data-testid="admin-note-input"]');
    this.noteAddButton = page.locator('[data-testid="admin-note-add-btn"]');
    this.noteItems = page.locator('[data-testid="admin-note-item"]');
    this.noteDeleteButtons = page.locator('[data-testid="admin-note-delete-btn"]');
  }

  /** Opens the detail modal for the row at the given index (0-based) and waits for it. */
  async openUser(index = 0) {
    await this.userRows.nth(index).click();
    await this.drawer.waitFor();
  }

  /** Switches the open detail modal to the tab with the given visible label. */
  tab(label: string): Locator {
    return this.drawer.locator('.au-tab', { hasText: label });
  }
}
