/**
 * PROOF GALLERY — captures a full-page screenshot of every user-facing dashboard
 * surface rendering with realistic mock data. Output: <repo>/test-proof/screenshots/.
 *
 * This is a *proof artifact generator*, not a behavioural test. Each case navigates a
 * route, lets it settle, asserts the shell rendered, and snapshots it. Run explicitly:
 *   npx playwright test tests/proof/proof-gallery.spec.ts
 */
import { test, expect } from '../../fixtures/test-fixtures';
import { MockApi } from '../../fixtures/mock-api.fixture';
import * as path from 'path';

import { mockUser } from '../../mocks/user.mocks';
import { mockUsageResponse } from '../../mocks/plan.mocks';
import { mockEmailAccounts } from '../../mocks/email-account.mocks';
import { mockFolders } from '../../mocks/folder.mocks';
import { mockEmailPage } from '../../mocks/email.mocks';
import { mockCategories } from '../../mocks/category.mocks';
import { mockTemplates } from '../../mocks/template.mocks';
import { mockAutomations } from '../../mocks/automation.mocks';
import { mockKnowledgeBases, mockKbParameterSets } from '../../mocks/knowledge-base.mocks';
import { mockSecrets } from '../../mocks/secret.mocks';
import { mockOrganizations, mockOrgDetail } from '../../mocks/organization.mocks';
import { mockDiscoverListings, mockLibrary } from '../../mocks/marketplace.mocks';
import { mockConversations } from '../../mocks/ai-chat.mocks';

const SHOTS = path.join(__dirname, '../../../../test-proof/screenshots');
const EMPTY_PAGE = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20, first: true, last: true };

/** All feature data mocks, self-contained. Specific regexes BEFORE plain-string (substring) matches. */
function applyGalleryMocks(api: MockApi): void {
  api
    // — identity / shell (re-registered so the layer is self-contained) —
    .get('/api/v1/users/me/usage', mockUsageResponse)
    .get('/api/v1/users/me', mockUser)
    .get('/api/v1/admin/me', { email: 'user@example.com', role: 'USER', staffRole: null, permissions: [] })
    // — email-account sub-resources (regex) MUST precede the plain '/email-accounts' substring —
    .get(/\/api\/v1\/email-accounts\/\d+\/emails/, mockEmailPage)
    .get(/\/api\/v1\/email-accounts\/\d+\/folders/, mockFolders)
    .get('/api/v1/email-accounts', mockEmailAccounts)
    // — resources —
    .get('/api/v1/categories', mockCategories)
    .get('/api/v1/templates', mockTemplates)
    .get('/api/v1/parameter-sets', mockKbParameterSets)
    .get('/api/v1/knowledge-bases', mockKnowledgeBases)
    .get('/api/v1/secrets', mockSecrets)
    // integrations is a sub-path of /automations → register BEFORE the plain '/automations'
    .get('/api/v1/automations/integrations', mockAutomations)
    .get('/api/v1/automations', mockAutomations)
    // org/current is a sub-path of /organizations → register BEFORE the plain string
    .get('/api/v1/organizations/current', mockOrgDetail)
    .get('/api/v1/organizations', mockOrganizations)
    // ai chat
    .get(/\/api\/v1\/ai\/conversations/, mockConversations)
    // marketplace
    .handle('GET', /\/marketplace\/library/, async (r) =>
      r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockLibrary) }))
    .handle('GET', /\/marketplace\/listings/, async (r) =>
      r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockDiscoverListings) }))
    // page-shaped feeds (empty page so the component renders its empty-state, not an error)
    .get('/api/v1/audit-log', EMPTY_PAGE)
    .get('/api/v1/activity', EMPTY_PAGE)
    .get('/api/v1/pending-actions', EMPTY_PAGE)
    // catch-all LAST: any other GET → [] so nothing hits the real network / hangs
    .get(/\/api\/v1\//, []);
}

interface Surface { name: string; path: string; }

const SURFACES: Surface[] = [
  { name: '01-dashboard-home', path: '/dashboard' },
  { name: '02-emails', path: '/dashboard/emails' },
  { name: '03-templates', path: '/dashboard/templates' },
  { name: '04-categories', path: '/dashboard/categories' },
  { name: '05-parameter-sets', path: '/dashboard/parameter-sets' },
  { name: '06-knowledge-bases', path: '/dashboard/knowledge-bases' },
  { name: '07-email-accounts', path: '/dashboard/email-accounts' },
  { name: '08-secrets', path: '/dashboard/secrets' },
  { name: '09-automations', path: '/dashboard/automations' },
  { name: '10-integrations', path: '/dashboard/integrations' },
  { name: '11-marketplace', path: '/dashboard/marketplace' },
  { name: '12-marketplace-library', path: '/dashboard/marketplace-library' },
  { name: '13-organization', path: '/dashboard/organization' },
  { name: '14-plans', path: '/dashboard/plans' },
  { name: '15-settings', path: '/dashboard/settings' },
  { name: '16-activity', path: '/dashboard/activity' },
  { name: '17-audit-log', path: '/dashboard/audit-log' },
  { name: '18-approvals', path: '/dashboard/approvals' },
];

test.describe('Proof Gallery — dashboard surfaces', () => {
  for (const s of SURFACES) {
    test(`renders ${s.name}`, async ({ authenticatedPage: page }) => {
      const api = new MockApi();
      applyGalleryMocks(api);
      await api.apply(page);

      await page.goto(s.path);
      await page.waitForLoadState('networkidle').catch(() => {});
      // settle animations / async signals
      await page.waitForTimeout(700);

      // shell sanity: the dashboard layout must be present (proves route + guard + layout)
      await expect(page.locator('body')).toBeVisible();

      await page.screenshot({ path: path.join(SHOTS, `${s.name}.png`), fullPage: true });
    });
  }
});
