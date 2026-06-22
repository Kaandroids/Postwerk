import { test, expect } from '../../fixtures/test-fixtures';
import { AdminAuditLogPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { mockAdminAuditLogs } from '../../mocks';

test.describe('Admin Audit Log', () => {
  let auditPage: AdminAuditLogPage;

  test.beforeEach(async ({ adminPage }) => {
    auditPage = new AdminAuditLogPage(adminPage);
    await adminPage.goto('/dashboard/admin/audit-log');
  });

  test('should display audit log entries', async () => {
    await expect(auditPage.logTable).toBeVisible();
    await expect(auditPage.logTable.locator('tbody tr')).toHaveCount(3);
  });

  test('should have action filter', async ({ adminPage }) => {
    const api = new MockApi();
    api.get('/api/v1/admin/audit-log', {
      ...mockAdminAuditLogs,
      content: [mockAdminAuditLogs.content[0]],
      totalElements: 1,
    });
    await api.apply(adminPage);

    await expect(auditPage.actionFilter).toBeVisible();
    // Use the actual enum value from auditActions array
    await auditPage.actionFilter.selectOption('USER_LOGIN');
    await expect(auditPage.logTable.locator('tbody tr')).toHaveCount(1);
  });

  test('should hide pagination when single page', async () => {
    // Mock data has totalPages=1, so pagination should not be visible
    await expect(auditPage.prevBtn).not.toBeVisible();
  });

  test('should display export button', async () => {
    await expect(auditPage.exportBtn).toBeVisible();
  });
});
