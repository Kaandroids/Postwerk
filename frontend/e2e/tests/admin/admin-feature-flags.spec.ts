import { test, expect } from '../../fixtures/test-fixtures';
import { AdminFeatureFlagsPage } from '../../pages';

test.describe('Admin Feature Flags', () => {
  let ff: AdminFeatureFlagsPage;

  test.beforeEach(async ({ adminPage }) => {
    ff = new AdminFeatureFlagsPage(adminPage);
    await adminPage.goto('/dashboard/admin/flags');
  });

  test('should render the KPI strip and the flags table', async () => {
    await expect(ff.kpis).toBeVisible();
    await expect(ff.table).toBeVisible();
    await expect(ff.rows).toHaveCount(4);
    await expect(ff.table).toContainText('marketplace.publish');
    await expect(ff.table).toContainText('wizard.v2');
  });

  test('should show the killed alert and flag the killed row', async () => {
    await expect(ff.alert).toBeVisible();
    await expect(ff.rows.filter({ hasText: 'email.failover_provider' })).toHaveAttribute('data-killed', '1');
  });

  test('should mark a stale flag with the cleanup tag', async () => {
    // marketplace.publish is stale (100% on > 60 days) → "CLEANUP"/"AUFRÄUMEN" tag (German default).
    await expect(ff.rows.filter({ hasText: 'marketplace.publish' })).toContainText('AUFRÄUMEN');
  });

  test('should open the flag editor with rollout + history tabs', async () => {
    await ff.rows.filter({ hasText: 'wizard.v2' }).click();
    await expect(ff.modal).toBeVisible();
    await expect(ff.modal).toContainText('wizard.v2');
    // History tab ("Verlauf") shows the rollout change log.
    await ff.modal.getByRole('tab', { name: /Verlauf/ }).click();
    await expect(ff.modal).toContainText('Rollout 10% → 35%');
  });

  test('should open the new-flag editor with a key field', async () => {
    await ff.newBtn.click();
    await expect(ff.modal).toBeVisible();
    await expect(ff.modal.locator('[data-testid="admin-ff-key"]')).toBeVisible();
  });

  test('should close the editor via the close button', async () => {
    await ff.rows.first().click();
    await expect(ff.modal).toBeVisible();
    await ff.modalClose.click();
    await expect(ff.modal).toBeHidden();
  });
});
