import { test, expect } from '../../fixtures/test-fixtures';
import { AdminUsersPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { mockAdminUsers } from '../../mocks';

test.describe('Admin Users', () => {
  let usersPage: AdminUsersPage;

  test.beforeEach(async ({ adminPage }) => {
    usersPage = new AdminUsersPage(adminPage);
    await adminPage.goto('/dashboard/admin/users');
  });

  test('should display user table with rows', async () => {
    await expect(usersPage.userRows).toHaveCount(3);
  });

  test('should filter users by search', async ({ adminPage }) => {
    const api = new MockApi();
    api.get(/\/api\/v1\/admin\/users/, {
      ...mockAdminUsers,
      content: [mockAdminUsers.content[0]],
      totalElements: 1,
    });
    await api.apply(adminPage);

    await usersPage.searchInput.fill('admin');
    await expect(usersPage.userRows).toHaveCount(1);
  });

  test('should filter users by role', async ({ adminPage }) => {
    const api = new MockApi();
    api.get(/\/api\/v1\/admin\/users/, {
      ...mockAdminUsers,
      content: [mockAdminUsers.content[0]],
      totalElements: 1,
    });
    await api.apply(adminPage);

    await usersPage.roleFilter.selectOption('ADMIN');
    await expect(usersPage.userRows).toHaveCount(1);
  });

  test('should open user drawer on row click', async () => {
    await usersPage.userRows.first().click();
    await expect(usersPage.drawer).toBeVisible();
  });

  test('should toggle user role', async ({ adminPage }) => {
    const api = new MockApi();
    api.patch(/\/api\/v1\/admin\/users\/[^/]+\/role/, {
      ...mockAdminUsers.content[1],
      role: 'ADMIN',
    });
    await api.apply(adminPage);

    await usersPage.roleToggleButtons.nth(1).click();
  });
});
