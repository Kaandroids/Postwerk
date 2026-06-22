import { test, expect } from '../../fixtures/test-fixtures';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { OrgMembersPage } from '../../pages';
import {
  mockOrganizations,
  mockOrgDetail,
  mockPersonalOrgDetail,
  mockMailboxGrants,
} from '../../mocks';

test.describe('Organization / Team management', () => {
  let orgPage: OrgMembersPage;

  /** Registers the org endpoints (specific patterns first — MockApi matches FIFO). */
  async function applyOrgMocks(page: import('@playwright/test').Page, detail = mockOrgDetail) {
    const api = new MockApi();
    api
      .get('/mailbox-grants', mockMailboxGrants)
      .get('/api/v1/organizations/current', detail)
      .get('/api/v1/organizations', mockOrganizations);
    await api.apply(page);
  }

  test.beforeEach(async ({ authenticatedPage }) => {
    orgPage = new OrgMembersPage(authenticatedPage);
    await applyOrgMocks(authenticatedPage);
    await orgPage.navigate();
  });

  test('renders the member roster', async () => {
    await expect(orgPage.memberRows).toHaveCount(3);
    await expect(orgPage.row(0)).toContainText('Olivia Owner');
    await expect(orgPage.row(1)).toContainText('Mike Member');
    await expect(orgPage.row(2)).toContainText('Vera Viewer');
  });

  test('owner role is a badge, member role is an editable select', async () => {
    await expect(orgPage.row(0).locator('.om-role-badge[data-role="OWNER"]')).toBeVisible();
    await expect(orgPage.row(1).locator('[data-testid="org-member-role"]')).toBeVisible();
  });

  test('owner sees the invite form', async () => {
    await expect(orgPage.inviteEmail).toBeVisible();
    await expect(orgPage.inviteRole).toBeVisible();
    await expect(orgPage.inviteSubmit).toBeVisible();
  });

  test('owner sees the leave button on a team org', async () => {
    await expect(orgPage.leaveBtn).toBeVisible();
  });

  test('opens the mailbox-grant drawer for a member', async () => {
    await orgPage.openGrants(1);
    await expect(orgPage.grantRows).toHaveCount(2);
    await expect(orgPage.grantsDrawer).toContainText('support@acme.com');
    await expect(
      orgPage.grantRows.nth(0).locator('[data-testid="org-grant-read"]'),
    ).toBeChecked();
  });

  test('saves mailbox grants and closes the drawer', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api.put('/mailbox-grants', {});
    await api.apply(authenticatedPage);

    await orgPage.openGrants(1);
    await orgPage.grantRows.nth(1).locator('[data-testid="org-grant-read"]').check();
    await orgPage.grantsSave.click();

    await expect(orgPage.grantsDrawer).toBeHidden();
  });

  test('personal workspace shows the hint and hides the leave button', async ({ authenticatedPage }) => {
    await applyOrgMocks(authenticatedPage, mockPersonalOrgDetail);
    await orgPage.navigate();

    await expect(authenticatedPage.locator('[data-testid="org-personal-note"]')).toBeVisible();
    await expect(orgPage.leaveBtn).toHaveCount(0);
    await expect(orgPage.memberRows).toHaveCount(1);
  });
});
