import { Locator, Page } from '@playwright/test';

export class SidebarPage {
  readonly sidebar: Locator;
  readonly logoutButton: Locator;
  readonly userName: Locator;
  readonly accountSwitcher: Locator;

  // Folder-specific locators
  readonly folderAddBtn: Locator;
  readonly folderNameInput: Locator;
  readonly folderError: Locator;
  readonly folderEmptyState: Locator;

  constructor(private page: Page) {
    this.sidebar = page.locator('.dash-side');
    this.logoutButton = page.locator('[data-testid="logout-btn"]');
    this.userName = page.locator('.dash-user-name');
    this.accountSwitcher = page.locator('app-account-switcher');

    this.folderAddBtn = page.locator('[data-testid="folder-add-btn"]');
    this.folderNameInput = page.locator('[data-testid="folder-name-input"]');
    this.folderError = page.locator('.folder-error');
    this.folderEmptyState = this.sidebar.locator('.dash-nav-empty');
  }

  /** Click a top-level nav item (not inside a group) */
  navItem(label: string): Locator {
    return this.sidebar.locator('.dash-nav-item, .dash-nav-child').filter({ hasText: label });
  }

  /** Click a nav item — handles both top-level and grouped items */
  async navigateTo(label: string) {
    const item = this.navItem(label);
    if (await item.isVisible({ timeout: 2000 }).catch(() => false)) {
      await item.click();
      return;
    }
    // Item might be inside a collapsed group — expand all groups first
    const groupLabels = this.sidebar.locator('.dash-nav-group-label');
    const count = await groupLabels.count();
    for (let i = 0; i < count; i++) {
      const group = groupLabels.nth(i);
      const parent = group.locator('..');
      const isOpen = await parent.getAttribute('data-open');
      if (isOpen !== '1') {
        await group.click();
      }
    }
    // Now try again
    await this.navItem(label).click();
  }

  groupToggle(label: string): Locator {
    return this.sidebar.locator('.dash-nav-group-label').filter({ hasText: label });
  }

  async expandGroup(label: string) {
    await this.groupToggle(label).click();
  }

  unreadBadge(): Locator {
    return this.sidebar.locator('.badge');
  }

  /** Get a custom folder item by name */
  customFolder(name: string): Locator {
    return this.sidebar.locator('.dash-nav-subfolder').filter({ hasText: name });
  }

  /** Get the delete button for a custom folder */
  folderDeleteBtn(name: string): Locator {
    return this.customFolder(name).locator('[data-testid="folder-del-btn"]');
  }

  /** Get all custom folder items */
  customFolders(): Locator {
    return this.sidebar.locator('.dash-nav-subfolder:not(.dash-nav-folder-input):not(.dash-nav-empty)');
  }

  /** Expand the Email group and then the Folders sub-section */
  async expandFolders() {
    await this.expandGroup('Email');
    const foldersItem = this.navItem('Ordner');
    await foldersItem.click();
  }
}
