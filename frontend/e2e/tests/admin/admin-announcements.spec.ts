import { test, expect } from '../../fixtures/test-fixtures';
import { AdminAnnouncementsPage } from '../../pages';

test.describe('Admin Announcements', () => {
  let ann: AdminAnnouncementsPage;

  test.beforeEach(async ({ adminPage }) => {
    ann = new AdminAnnouncementsPage(adminPage);
    await adminPage.goto('/dashboard/admin/announcements');
  });

  test('should render the KPI strip and the announcements table', async () => {
    await expect(ann.kpis).toBeVisible();
    await expect(ann.table).toBeVisible();
    await expect(ann.rows).toHaveCount(4);
    await expect(ann.table).toContainText('Scheduled maintenance on mail delivery');
    await expect(ann.table).toContainText('Marketplace: 100 new templates');
  });

  test('should show the maintenance-live alert and flag the maintenance row', async () => {
    await expect(ann.alert).toBeVisible();
    await expect(ann.rows.filter({ hasText: 'Scheduled maintenance' })).toHaveAttribute('data-maint', '1');
  });

  test('should open the editor with the bilingual content + live preview', async () => {
    await ann.rows.first().click();
    await expect(ann.modal).toBeVisible();
    // Editor defaults to the DE locale, so the live preview shows the German title.
    await expect(ann.modal).toContainText('Geplante Wartung am Mailversand');
    // History tab ("Verlauf") shows the change log.
    await ann.modal.getByRole('tab', { name: /Verlauf/ }).click();
    await expect(ann.modal).toContainText('Published');
  });

  test('should open the new-announcement editor', async () => {
    await ann.newBtn.click();
    await expect(ann.modal).toBeVisible();
    await expect(ann.modal.locator('[data-testid="admin-ann-title-de"]')).toBeVisible();
  });

  test('should close the editor via the close button', async () => {
    await ann.rows.first().click();
    await expect(ann.modal).toBeVisible();
    await ann.modalClose.click();
    await expect(ann.modal).toBeHidden();
  });
});
