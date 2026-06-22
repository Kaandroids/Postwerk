import { test, expect } from '../../fixtures/test-fixtures';
import { AdminGdprPage } from '../../pages';

test.describe('Admin GDPR / Data Requests', () => {
  let gdpr: AdminGdprPage;

  test.beforeEach(async ({ adminPage }) => {
    gdpr = new AdminGdprPage(adminPage);
    await adminPage.goto('/dashboard/admin/gdpr');
  });

  test('should render the KPI strip and the requests table', async () => {
    await expect(gdpr.kpis).toBeVisible();
    await expect(gdpr.table).toBeVisible();
    await expect(gdpr.rows).toHaveCount(4);
    await expect(gdpr.table).toContainText('Katrin Hofmann');
    await expect(gdpr.table).toContainText('Elias Brunner');
  });

  test('should render the automated retention posture row', async () => {
    await expect(gdpr.retention).toBeVisible();
    await expect(gdpr.retention).toContainText('365'); // email retention
    await expect(gdpr.retention).toContainText('730'); // audit retention
  });

  test('should show the overdue alert and flag the overdue row', async () => {
    await expect(gdpr.alert).toBeVisible();
    // The first request is past its deadline → overdue row treatment.
    await expect(gdpr.rows.filter({ hasText: 'Katrin Hofmann' })).toHaveAttribute('data-overdue', '1');
  });

  test('should open the request detail modal with footprint and timeline', async () => {
    await gdpr.rows.first().click();
    await expect(gdpr.modal).toBeVisible();
    await expect(gdpr.modal).toContainText('Katrin Hofmann');

    // Footprint tab ("Datenbestand") shows the stored-data counts.
    await gdpr.modal.getByRole('tab', { name: /Datenbestand/ }).click();
    await expect(gdpr.modal).toContainText('3.1K'); // compactNumber(3120) stored emails
    await expect(gdpr.modal).toContainText('86');

    // Timeline tab ("Verlauf") lists the status history.
    await gdpr.modal.getByRole('tab', { name: /Verlauf/ }).click();
    await expect(gdpr.modal).toContainText('Identity verified');
  });

  test('should open the new-request modal', async () => {
    await gdpr.newRequest.click();
    await expect(gdpr.createModal).toBeVisible();
    await expect(gdpr.createModal.locator('[data-testid="admin-gdpr-create-email"]')).toBeVisible();
  });

  test('should close the detail modal via the close button', async () => {
    await gdpr.rows.first().click();
    await expect(gdpr.modal).toBeVisible();
    await gdpr.modalClose.click();
    await expect(gdpr.modal).toBeHidden();
  });
});
