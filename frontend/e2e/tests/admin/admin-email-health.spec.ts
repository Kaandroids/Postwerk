import { test, expect } from '../../fixtures/test-fixtures';
import { AdminEmailHealthPage } from '../../pages';

test.describe('Admin Email Health', () => {
  let eh: AdminEmailHealthPage;

  test.beforeEach(async ({ adminPage }) => {
    eh = new AdminEmailHealthPage(adminPage);
    await adminPage.goto('/dashboard/admin/email-health');
  });

  test('should render the KPI strip with totals', async () => {
    await expect(eh.kpis).toBeVisible();
    await expect(eh.kpis).toContainText('28');
  });

  test('should render the down-cluster alert strip', async () => {
    await expect(eh.alert).toBeVisible();
    await expect(eh.alert).toContainText('mx-eu-3');
  });

  test('should render the by-cluster summary', async () => {
    await expect(eh.clusters).toBeVisible();
    await expect(eh.clusters).toContainText('mx-eu-1');
    await expect(eh.clusters).toContainText('mx-eu-3');
  });

  test('should render the mailbox table rows', async () => {
    await expect(eh.table).toBeVisible();
    await expect(eh.rows).toHaveCount(4);
    await expect(eh.table).toContainText('support@nordlicht.de');
    await expect(eh.table).toContainText('Postwerk GmbH');
  });

  test('should open the detail modal when a row is clicked', async () => {
    await eh.rows.first().click();
    await expect(eh.modal).toBeVisible();
    await expect(eh.modal).toContainText('support@nordlicht.de');
    // Connection config tab shows the IMAP host from the detail mock.
    await expect(eh.modal).toContainText('imap.mx-eu-3.example.com');
  });

  test('should open the row action menu (not clipped by the table)', async ({ adminPage }) => {
    await adminPage.locator('[data-testid^="admin-eh-menu-"]').first().click();
    // The fixed-positioned menu renders its gated actions (Pause for a non-paused mailbox).
    await expect(adminPage.locator('[data-testid^="admin-eh-pause-"]').first()).toBeVisible();
  });
});
