import { test, expect } from '../../fixtures/test-fixtures';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { OrgSwitcherPage } from '../../pages';
import { mockOrganizations, mockOrgDetail, mockInvitations, mockAcceptedOrg } from '../../mocks';

/**
 * Pending-invitation flow in the org switcher (new accept/decline lifecycle). Inviting no longer
 * makes someone a member instantly: the invitee sees the invite here and accepts/declines it.
 */
test.describe('Organization / Invitations (org switcher)', () => {
  /** Registers org + invitation endpoints (specific patterns before generic — MockApi matches FIFO). */
  async function applyMocks(page: import('@playwright/test').Page, invitations = mockInvitations) {
    const api = new MockApi();
    api
      .post(/\/api\/v1\/organizations\/invitations\/[^/]+\/accept/, mockAcceptedOrg)
      .post(/\/api\/v1\/organizations\/invitations\/[^/]+\/decline/, {})
      .get('/api/v1/organizations/invitations', invitations)
      .get('/api/v1/organizations/current', mockOrgDetail)
      .get('/api/v1/organizations', mockOrganizations);
    await api.apply(page);
  }

  test('shows a badge with the pending-invitation count', async ({ authenticatedPage: page }) => {
    await applyMocks(page);
    const switcher = new OrgSwitcherPage(page);
    await page.goto('/dashboard');

    await expect(switcher.badge).toHaveText('2');
  });

  test('lists pending invitations with org name and inviter', async ({ authenticatedPage: page }) => {
    await applyMocks(page);
    const switcher = new OrgSwitcherPage(page);
    await page.goto('/dashboard');
    await switcher.open();

    await expect(switcher.invitations).toHaveCount(2);
    await expect(switcher.invitation(0)).toContainText('Beta Team');
    await expect(switcher.invitation(0)).toContainText('Olivia Owner');
    await expect(switcher.invitation(1)).toContainText('Gamma LLC');
  });

  test('declining removes the invitation and decrements the badge', async ({ authenticatedPage: page }) => {
    await applyMocks(page);
    const switcher = new OrgSwitcherPage(page);
    await page.goto('/dashboard');
    await switcher.open();
    await expect(switcher.invitations).toHaveCount(2);

    const declineReq = page.waitForRequest(
      r => /\/organizations\/invitations\/org-invite-2\/decline/.test(r.url()) && r.method() === 'POST',
    );
    await switcher.declineBtn(1).click();
    await declineReq;

    await expect(switcher.invitations).toHaveCount(1);
    await expect(switcher.invitations).toContainText('Beta Team');
    await expect(switcher.badge).toHaveText('1');
  });

  test('accepting an invitation calls the accept endpoint', async ({ authenticatedPage: page }) => {
    await applyMocks(page);
    const switcher = new OrgSwitcherPage(page);
    await page.goto('/dashboard');
    await switcher.open();

    const acceptReq = page.waitForRequest(
      r => /\/organizations\/invitations\/org-invite-1\/accept/.test(r.url()) && r.method() === 'POST',
    );
    await switcher.acceptBtn(0).click();
    await acceptReq; // success → service switches into the joined org (page reload)
  });

  test('no pending invitations → no badge', async ({ authenticatedPage: page }) => {
    await applyMocks(page, []);
    const switcher = new OrgSwitcherPage(page);
    await page.goto('/dashboard');

    await expect(switcher.badge).toHaveCount(0);
  });
});
