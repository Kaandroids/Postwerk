import { Locator, Page } from '@playwright/test';

export class AdminStaffPage {
  readonly kpis: Locator;
  readonly alert: Locator;
  readonly viewStaff: Locator;
  readonly viewRoles: Locator;
  readonly table: Locator;
  readonly rows: Locator;
  readonly roles: Locator;
  readonly search: Locator;
  readonly grant: Locator;
  readonly modal: Locator;
  readonly modalClose: Locator;
  readonly grantModal: Locator;

  constructor(private page: Page) {
    this.kpis = page.locator('[data-testid="admin-staff-kpis"]');
    this.alert = page.locator('[data-testid="admin-staff-alert"]');
    this.viewStaff = page.locator('[data-testid="admin-staff-view-staff"]');
    this.viewRoles = page.locator('[data-testid="admin-staff-view-roles"]');
    this.table = page.locator('[data-testid="admin-staff-table"]');
    this.rows = page.locator('[data-testid="admin-staff-row"]');
    this.roles = page.locator('[data-testid="admin-staff-roles"]');
    this.search = page.locator('[data-testid="admin-staff-search"]');
    this.grant = page.locator('[data-testid="admin-staff-grant"]');
    this.modal = page.locator('[data-testid="admin-staff-modal"]');
    this.modalClose = page.locator('[data-testid="admin-staff-modal-close"]');
    this.grantModal = page.locator('[data-testid="admin-staff-grant-modal"]');
  }
}
