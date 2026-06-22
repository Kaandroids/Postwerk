import { test, expect } from '../../fixtures/test-fixtures';
import { AdminStaffPage } from '../../pages';

test.describe('Admin Staff & Roles', () => {
  let sf: AdminStaffPage;

  test.beforeEach(async ({ adminPage }) => {
    sf = new AdminStaffPage(adminPage);
    await adminPage.goto('/dashboard/admin/staff');
  });

  test('should render the KPI strip and the staff roster', async () => {
    await expect(sf.kpis).toBeVisible();
    await expect(sf.table).toBeVisible();
    await expect(sf.rows).toHaveCount(6);
    await expect(sf.table).toContainText('Marius Kessler');
    await expect(sf.table).toContainText('Tomáš Novák');
  });

  test('should show the single-super-admin alert and flag your own row', async () => {
    await expect(sf.alert).toBeVisible();
    await expect(sf.rows.filter({ hasText: 'Marius Kessler' })).toHaveAttribute('data-self', '1');
  });

  test('should switch to the roles reference and render the capability matrix', async () => {
    await sf.viewRoles.click();
    await expect(sf.roles).toBeVisible();
    await expect(sf.roles).toContainText('Super Admin');
    // A capability label from the matrix renders (German default).
    await expect(sf.roles).toContainText('Marktplatz moderieren');
  });

  test('should open the member detail with the role picker + capability matrix', async () => {
    await sf.rows.filter({ hasText: 'Chiara Ferrari' }).click();
    await expect(sf.modal).toBeVisible();
    await expect(sf.modal).toContainText('Chiara Ferrari');
    await expect(sf.modal.locator('[data-testid="admin-staff-rolepick"]')).toBeVisible();
    // Capabilities tab shows a granted billing capability label.
    await expect(sf.modal).toContainText('Abrechnung verwalten');
  });

  test('should lock the action row on your own record', async () => {
    await sf.rows.filter({ hasText: 'Marius Kessler' }).click();
    await expect(sf.modal).toBeVisible();
    // Self record: no role picker, a self-lock note instead.
    await expect(sf.modal.locator('[data-testid="admin-staff-rolepick"]')).toHaveCount(0);
  });

  test('should open the grant-access modal', async () => {
    await sf.grant.click();
    await expect(sf.grantModal).toBeVisible();
    await expect(sf.grantModal.locator('[data-testid="admin-staff-grant-search"]')).toBeVisible();
  });

  test('should close the detail modal via the close button', async () => {
    await sf.rows.filter({ hasText: 'Chiara Ferrari' }).click();
    await expect(sf.modal).toBeVisible();
    await sf.modalClose.click();
    await expect(sf.modal).toBeHidden();
  });
});
