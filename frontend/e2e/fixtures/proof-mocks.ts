/**
 * Shared mock setup + surface list for the "proof" specs (screenshot gallery, a11y scan,
 * visual regression). Registers realistic feature data for every dashboard surface and a
 * catch-all so no request hangs. Specific regexes precede plain-string (substring) matches.
 */
import { MockApi } from './mock-api.fixture';
import { mockUser } from '../mocks/user.mocks';
import { mockUsageResponse } from '../mocks/plan.mocks';
import { mockEmailAccounts } from '../mocks/email-account.mocks';
import { mockFolders } from '../mocks/folder.mocks';
import { mockEmailPage } from '../mocks/email.mocks';
import { mockCategories } from '../mocks/category.mocks';
import { mockTemplates } from '../mocks/template.mocks';
import { mockAutomations } from '../mocks/automation.mocks';
import { mockKnowledgeBases, mockKbParameterSets } from '../mocks/knowledge-base.mocks';
import { mockSecrets } from '../mocks/secret.mocks';
import { mockOrganizations, mockOrgDetail } from '../mocks/organization.mocks';
import { mockDiscoverListings, mockLibrary } from '../mocks/marketplace.mocks';
import { mockConversations } from '../mocks/ai-chat.mocks';

const EMPTY_PAGE = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20, first: true, last: true };

export function applyProofMocks(api: MockApi): void {
  api
    .get('/api/v1/users/me/usage', mockUsageResponse)
    .get('/api/v1/users/me', mockUser)
    .get('/api/v1/admin/me', { email: 'user@example.com', role: 'USER', staffRole: null, permissions: [] })
    .get(/\/api\/v1\/email-accounts\/\d+\/emails/, mockEmailPage)
    .get(/\/api\/v1\/email-accounts\/\d+\/folders/, mockFolders)
    .get('/api/v1/email-accounts', mockEmailAccounts)
    .get('/api/v1/categories', mockCategories)
    .get('/api/v1/templates', mockTemplates)
    .get('/api/v1/parameter-sets', mockKbParameterSets)
    .get('/api/v1/knowledge-bases', mockKnowledgeBases)
    .get('/api/v1/secrets', mockSecrets)
    .get('/api/v1/automations/integrations', mockAutomations)
    .get('/api/v1/automations', mockAutomations)
    .get('/api/v1/organizations/current', mockOrgDetail)
    .get('/api/v1/organizations', mockOrganizations)
    .get(/\/api\/v1\/ai\/conversations/, mockConversations)
    .handle('GET', /\/marketplace\/library/, async (r) =>
      r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockLibrary) }))
    .handle('GET', /\/marketplace\/listings/, async (r) =>
      r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockDiscoverListings) }))
    .get('/api/v1/audit-log', EMPTY_PAGE)
    .get('/api/v1/activity', EMPTY_PAGE)
    .get('/api/v1/pending-actions', EMPTY_PAGE)
    .get(/\/api\/v1\//, []);
}

export interface Surface { name: string; path: string; }

export const DASHBOARD_SURFACES: Surface[] = [
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
