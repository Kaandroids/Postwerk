import { test as base, Page } from '@playwright/test';
import { setAuthTokens, setAdminAuthTokens, dismissCookieConsent } from './auth.fixture';
import { MockApi } from './mock-api.fixture';
import { mockUser } from '../mocks/user.mocks';
import { mockEmailAccounts } from '../mocks/email-account.mocks';
import { mockFolders } from '../mocks/folder.mocks';
import { mockUsageResponse } from '../mocks/plan.mocks';
import {
  mockAdminStats,
  mockAdminUsers,
  mockAiUsageStats,
  mockAiUsageByUser,
  mockAiUsageTimeline,
  mockAutomationStats,
  mockAutomationExecutions,
  mockAdminAuditLogs,
  mockAdminPlans,
  mockAdminPricing,
  mockStaffIdentity,
  mockAdminOrgs,
  mockAdminOrgDetail,
  mockAdminUserOrgs,
  mockAdminUserMailboxes,
  mockAdminOrgAutomations,
  mockAdminOrgMailboxes,
  mockStaffNotes,
  mockUserSessions,
  mockQuotaOverrides,
  mockQuotaKpis,
  mockEmailHealthMailboxes,
  mockEmailHealthKpis,
  mockEmailHealthClusters,
  mockEmailHealthDetail,
  mockSystemHealthSubsystems,
  mockSystemHealthKpis,
  mockSystemHealthEvents,
  mockMaintenanceMode,
  mockSystemHealthDetail,
  mockSubscriptions,
  mockSubscriptionKpis,
  mockSubscriptionDetail,
  mockPlanHistory,
  mockJobs,
  mockBackgroundJobsKpis,
  mockJobQueues,
  mockJobDetail,
  mockMarketplaceListings,
  mockMarketplaceReviews,
  mockMarketplaceKpis,
  mockMarketplaceListingDetail,
  mockGdprRequests,
  mockGdprKpis,
  mockGdprRetention,
  mockGdprDetail,
  mockAnnouncements,
  mockAnnouncementKpis,
  mockAnnouncementDetail,
  mockFlags,
  mockFlagKpis,
  mockFlagDetail,
  mockStaffRoster,
  mockStaffKpis,
  mockStaffRolesMatrix,
  mockStaffCandidates,
} from '../mocks/admin.mocks';

type Fixtures = {
  mockApi: MockApi;
  authenticatedPage: Page;
  adminPage: Page;
};

export const test = base.extend<Fixtures>({
  mockApi: async ({}, use) => {
    await use(new MockApi());
  },

  authenticatedPage: async ({ page }, use) => {
    // Dismiss cookie consent and set auth tokens before any navigation
    await dismissCookieConsent(page);
    await setAuthTokens(page);

    // Set up default mocks every authenticated page needs
    const api = new MockApi();
    api
      .get('/api/v1/users/me/usage', mockUsageResponse)
      .get('/api/v1/users/me', mockUser)
      .get('/api/v1/email-accounts', mockEmailAccounts)
      .get(/\/api\/v1\/email-accounts\/\d+\/folders/, mockFolders)
      .get('/api/v1/secrets', [])
      // Regular users are not staff → admin-mode toggle stays hidden (topbar calls /admin/me on load).
      .get('/api/v1/admin/me', { email: 'user@example.com', role: 'USER', staffRole: null, permissions: [] });

    await api.apply(page);
    await use(page);
  },

  adminPage: async ({ page }, use) => {
    await dismissCookieConsent(page);
    await setAdminAuthTokens(page);

    const api = new MockApi();
    // Order matters: more specific patterns must come BEFORE less specific ones
    // because MockApi matches the first route that matches (FIFO)
    api
      .get('/api/v1/users/me/usage', mockUsageResponse)
      .get('/api/v1/users/me', mockUser)
      .get('/api/v1/email-accounts', mockEmailAccounts)
      .get(/\/api\/v1\/email-accounts\/\d+\/folders/, mockFolders)
      .get('/api/v1/admin/me', mockStaffIdentity)
      .get('/api/v1/admin/stats', mockAdminStats)
      // Specific org/user detail-tab endpoints BEFORE the generic detail regex (FIFO matching).
      .get(/\/api\/v1\/admin\/organizations\/[^/]+\/automations/, mockAdminOrgAutomations)
      .get(/\/api\/v1\/admin\/organizations\/[^/]+\/mailboxes/, mockAdminOrgMailboxes)
      .get(/\/api\/v1\/admin\/organizations\/[^/]+/, mockAdminOrgDetail)
      .get('/api/v1/admin/organizations', mockAdminOrgs)
      .get(/\/api\/v1\/admin\/users\/[^/]+\/organizations/, mockAdminUserOrgs)
      .get(/\/api\/v1\/admin\/users\/[^/]+\/mailboxes/, mockAdminUserMailboxes)
      .get(/\/api\/v1\/admin\/users\/[^/]+\/notes/, mockStaffNotes)
      .get(/\/api\/v1\/admin\/users\/[^/]+\/sessions/, mockUserSessions)
      .get(/\/api\/v1\/admin\/users\/[^/]+\//, mockAdminUsers.content[0])
      .get('/api/v1/admin/users', mockAdminUsers)
      .get('/api/v1/admin/ai-usage/by-user', mockAiUsageByUser)
      .get('/api/v1/admin/ai-usage/timeline', mockAiUsageTimeline)
      .get('/api/v1/admin/ai-usage', mockAiUsageStats)
      .get('/api/v1/admin/automations/stats', mockAutomationStats)
      .get('/api/v1/admin/automations/executions', mockAutomationExecutions)
      .get('/api/v1/admin/audit-log/export', {})
      .get('/api/v1/admin/audit-log', mockAdminAuditLogs)
      .get('/api/v1/admin/plans', mockAdminPlans)
      .get('/api/v1/admin/pricing/models', mockAdminPricing)
      // Quota overrides: /kpis is more specific, so it must come BEFORE the generic list regex.
      .get('/api/v1/admin/quota-overrides/kpis', mockQuotaKpis)
      .get(/\/api\/v1\/admin\/quota-overrides/, mockQuotaOverrides)
      // Email Health: exact /kpis + /clusters, then the detail regex (mailboxes/{id}) BEFORE the list.
      .get('/api/v1/admin/email-health/kpis', mockEmailHealthKpis)
      .get('/api/v1/admin/email-health/clusters', mockEmailHealthClusters)
      .get(/\/api\/v1\/admin\/email-health\/mailboxes\/[^/?]+/, mockEmailHealthDetail)
      .get(/\/api\/v1\/admin\/email-health\/mailboxes/, mockEmailHealthMailboxes)
      // System Health: exact /kpis + /events + /maintenance, then subsystem detail regex BEFORE list.
      .get('/api/v1/admin/system-health/kpis', mockSystemHealthKpis)
      .get('/api/v1/admin/system-health/events', mockSystemHealthEvents)
      .get('/api/v1/admin/system-health/maintenance', mockMaintenanceMode)
      .get(/\/api\/v1\/admin\/system-health\/subsystems\/[^/?]+/, mockSystemHealthDetail)
      .get(/\/api\/v1\/admin\/system-health\/subsystems/, mockSystemHealthSubsystems)
      // Subscriptions: exact /kpis, then plan-history regex, then detail regex, then the list.
      .get('/api/v1/admin/subscriptions/kpis', mockSubscriptionKpis)
      .get(/\/api\/v1\/admin\/subscriptions\/[^/]+\/plan-history/, mockPlanHistory)
      .get(/\/api\/v1\/admin\/subscriptions\/[^/?]+/, mockSubscriptionDetail)
      .get(/\/api\/v1\/admin\/subscriptions/, mockSubscriptions)
      // Background jobs: exact /kpis + /queues, then job detail regex BEFORE the jobs list.
      .get('/api/v1/admin/background-jobs/kpis', mockBackgroundJobsKpis)
      .get('/api/v1/admin/background-jobs/queues', mockJobQueues)
      .get(/\/api\/v1\/admin\/background-jobs\/jobs\/[^/?]+/, mockJobDetail)
      .get(/\/api\/v1\/admin\/background-jobs\/jobs/, mockJobs)
      // Marketplace Moderation: exact /kpis + /reviews, then listing detail regex BEFORE the list.
      .get('/api/v1/admin/marketplace/kpis', mockMarketplaceKpis)
      .get('/api/v1/admin/marketplace/reviews', mockMarketplaceReviews)
      .get(/\/api\/v1\/admin\/marketplace\/listings\/[^/?]+/, mockMarketplaceListingDetail)
      .get(/\/api\/v1\/admin\/marketplace\/listings/, mockMarketplaceListings)
      // Moderation mutations (return an updated entity so the optimistic UI has something to apply).
      .post(/\/api\/v1\/admin\/marketplace\/listings\/[^/]+\/takedown/, { ...mockMarketplaceListings.content[0], status: 'TAKEN_DOWN', takenDown: true })
      .post(/\/api\/v1\/admin\/marketplace\/listings\/[^/]+\/restore/, { ...mockMarketplaceListings.content[2], status: 'PAUSED', takenDown: false })
      .post(/\/api\/v1\/admin\/marketplace\/listings\/[^/]+\/feature/, { ...mockMarketplaceListings.content[1], featured: true })
      .post(/\/api\/v1\/admin\/marketplace\/listings\/[^/]+\/unfeature/, { ...mockMarketplaceListings.content[0], featured: false })
      .post(/\/api\/v1\/admin\/marketplace\/reviews\/[^/]+\/hide/, { ...mockMarketplaceReviews.content[0], hidden: true })
      .post(/\/api\/v1\/admin\/marketplace\/reviews\/[^/]+\/unhide/, { ...mockMarketplaceReviews.content[2], hidden: false })
      .delete(/\/api\/v1\/admin\/marketplace\/reviews\/[^/]+/, {})
      // GDPR / Data Requests: exact /kpis + /retention, then request detail regex BEFORE the list.
      .get('/api/v1/admin/gdpr/kpis', mockGdprKpis)
      .get('/api/v1/admin/gdpr/retention', mockGdprRetention)
      .get(/\/api\/v1\/admin\/gdpr\/requests\/[^/?]+/, mockGdprDetail)
      .get(/\/api\/v1\/admin\/gdpr\/requests/, mockGdprRequests)
      // Mutations: specific action regexes BEFORE the generic create string (FIFO).
      .post(/\/api\/v1\/admin\/gdpr\/requests\/[^/]+\/export/, { ...mockGdprRequests.content[0], status: 'IN_PROGRESS' })
      .post(/\/api\/v1\/admin\/gdpr\/requests\/[^/]+\/erase/, { ...mockGdprRequests.content[0], status: 'IN_PROGRESS' })
      .post(/\/api\/v1\/admin\/gdpr\/requests\/[^/]+\/reject/, { ...mockGdprRequests.content[0], status: 'REJECTED', closedAt: new Date().toISOString() })
      .post(/\/api\/v1\/admin\/gdpr\/requests\/[^/]+\/complete/, { ...mockGdprRequests.content[0], status: 'COMPLETED', closedAt: new Date().toISOString() })
      .post(/\/api\/v1\/admin\/gdpr\/requests/, { ...mockGdprRequests.content[2] })
      // Announcements: exact /kpis, then detail regex BEFORE the list string.
      .get('/api/v1/admin/announcements/kpis', mockAnnouncementKpis)
      .get(/\/api\/v1\/admin\/announcements\/[^/?]+/, mockAnnouncementDetail)
      .get('/api/v1/admin/announcements', mockAnnouncements)
      .post(/\/api\/v1\/admin\/announcements\/[^/]+\/(publish|end|archive|duplicate)/, { ...mockAnnouncements.content[0] })
      .put(/\/api\/v1\/admin\/announcements\/[^/]+/, { ...mockAnnouncements.content[0] })
      .post('/api/v1/admin/announcements', { ...mockAnnouncements.content[2] })
      // Feature flags: exact /kpis, then detail regex BEFORE the list string.
      .get('/api/v1/admin/feature-flags/kpis', mockFlagKpis)
      .get(/\/api\/v1\/admin\/feature-flags\/[^/?]+/, mockFlagDetail)
      .get('/api/v1/admin/feature-flags', mockFlags)
      .post(/\/api\/v1\/admin\/feature-flags\/[^/]+\/(enable|disable|kill|restore|archive|duplicate)/, { ...mockFlags.content[1] })
      .put(/\/api\/v1\/admin\/feature-flags\/[^/]+/, { ...mockFlags.content[1] })
      .post('/api/v1/admin/feature-flags', { ...mockFlags.content[3] })
      // Staff & Roles: exact /kpis + /roles + /candidates BEFORE the generic roster string.
      .get('/api/v1/admin/staff/kpis', mockStaffKpis)
      .get('/api/v1/admin/staff/roles', mockStaffRolesMatrix)
      .get('/api/v1/admin/staff/candidates', mockStaffCandidates)
      .get('/api/v1/admin/staff', mockStaffRoster)
      .post(/\/api\/v1\/admin\/staff\/[^/]+/, { ...mockStaffRoster.content[2], role: 'MODERATOR', tier: 'PRIVILEGED' })
      .delete(/\/api\/v1\/admin\/staff\/[^/]+/, { ...mockStaffRoster.content[2], role: null });

    await api.apply(page);
    await use(page);
  },
});

export { expect } from '@playwright/test';
