import { test, expect } from '../../fixtures/test-fixtures';
import { AdminUsersPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';

// Users support tooling wired into the detail modal: reset password, sessions, staff notes.
// The adminPage fixture identity is SUPER_ADMIN (has USER_CREDENTIAL_RESET / USER_MANAGE / USER_VIEW,
// email admin@example.com), so all gating is open and note n1 (authored by admin@example.com) is deletable.
test.describe('Admin User Support Tooling', () => {
  let usersPage: AdminUsersPage;
  // ConfirmDialogService renders an in-app modal; its confirm button is `.btn-confirm`.
  const confirmBtn = (p: import('@playwright/test').Page) => p.locator('.btn-confirm');

  test.beforeEach(async ({ adminPage }) => {
    usersPage = new AdminUsersPage(adminPage);
    await adminPage.goto('/dashboard/admin/users');
    await usersPage.openUser(0);
  });

  test('reset-password action shows a confirm then a success flash', async ({ adminPage }) => {
    const api = new MockApi();
    api.post(/\/api\/v1\/admin\/users\/[^/]+\/reset-password/, {}, 204);
    await api.apply(adminPage);

    await usersPage.resetPasswordButton.click();
    await confirmBtn(adminPage).click();

    await expect(usersPage.actionFlash).toBeVisible();
  });

  test('Sessions & Security tab shows count and revokes', async ({ adminPage }) => {
    const api = new MockApi();
    api.post(/\/api\/v1\/admin\/users\/[^/]+\/revoke-sessions/, { activeSessions: 0 });
    await api.apply(adminPage);

    await usersPage.tab('Sitzungen & Sicherheit').click();
    await expect(usersPage.sessionsCount).toHaveText('2');

    await usersPage.revokeSessionsButton.click();
    await confirmBtn(adminPage).click();
    await expect(usersPage.sessionsCount).toHaveText('0');
  });

  test('Notes tab lists notes and can add one', async ({ adminPage }) => {
    const api = new MockApi();
    api.post(/\/api\/v1\/admin\/users\/[^/]+\/notes/, {
      id: 'n-new', body: 'A fresh note', authorName: 'Admin User', authorEmail: 'admin@example.com', createdAt: '2026-06-16T10:00:00Z',
    }, 201);
    await api.apply(adminPage);

    await usersPage.tab('Notizen').click();
    await expect(usersPage.staffNotes).toBeVisible();
    await expect(usersPage.noteItems).toHaveCount(2);

    await usersPage.noteInput.fill('A fresh note');
    await usersPage.noteAddButton.click();
    await expect(usersPage.noteItems).toHaveCount(3);
  });

  test('Notes tab can delete an authored note', async ({ adminPage }) => {
    const api = new MockApi();
    api.delete(/\/api\/v1\/admin\/users\/[^/]+\/notes\/[^/]+/, {}, 204);
    await api.apply(adminPage);

    await usersPage.tab('Notizen').click();
    await expect(usersPage.noteItems).toHaveCount(2);

    await usersPage.noteDeleteButtons.first().click();
    await confirmBtn(adminPage).click();
    await expect(usersPage.noteItems).toHaveCount(1);
  });
});
