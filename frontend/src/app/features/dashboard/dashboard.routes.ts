import { Routes } from '@angular/router';
import { DashboardLayoutComponent } from './components/dashboard-layout/dashboard-layout.component';
import { adminGuard } from '../../core/guards/admin.guard';

export default [
  {
    path: '',
    component: DashboardLayoutComponent,
    children: [
      { path: '', loadComponent: () => import('./components/dashboard-home/dashboard-home.component').then(m => m.DashboardHomeComponent) },
      { path: 'emails', loadComponent: () => import('./components/emails-page/emails-page.component').then(m => m.EmailsPageComponent) },
      { path: 'templates', loadComponent: () => import('./components/templates-page/templates-page.component').then(m => m.TemplatesPageComponent) },
      { path: 'categories', loadComponent: () => import('./components/categories-page/categories-page.component').then(m => m.CategoriesPageComponent) },
      { path: 'parameter-sets', loadComponent: () => import('./components/parameter-sets-page/parameter-sets-page.component').then(m => m.ParameterSetsPageComponent) },
      { path: 'knowledge-bases', loadComponent: () => import('./components/knowledge-bases-page/knowledge-bases-page.component').then(m => m.KnowledgeBasesPageComponent) },
      { path: 'email-accounts', loadComponent: () => import('./components/email-accounts-page/email-accounts-page.component').then(m => m.EmailAccountsPageComponent) },
      { path: 'secrets', loadComponent: () => import('./components/secrets-page/secrets-page.component').then(m => m.SecretsPageComponent) },
      { path: 'plans', loadComponent: () => import('./components/plans-page/plans-page.component').then(m => m.PlansPageComponent) },
      { path: 'settings', loadComponent: () => import('./components/settings-page/settings-page.component').then(m => m.SettingsPageComponent) },
      { path: 'organization', loadComponent: () => import('./components/org-members/org-members.component').then(m => m.OrgMembersComponent) },
      { path: 'audit-log', loadComponent: () => import('./components/audit-log-page/audit-log-page.component').then(m => m.AuditLogPageComponent) },
      { path: 'automations', loadComponent: () => import('./components/automations-page/automations-page.component').then(m => m.AutomationsPageComponent) },
      { path: 'automations/:id/edit', loadComponent: () => import('./components/automation-editor/automation-editor.component').then(m => m.AutomationEditorComponent) },
      { path: 'integrations', loadComponent: () => import('./components/integrations-page/integrations-page.component').then(m => m.IntegrationsPageComponent) },
      { path: 'approvals', loadComponent: () => import('./components/approvals-page/approvals-page.component').then(m => m.ApprovalsPageComponent) },
      { path: 'activity', loadComponent: () => import('./components/activity-page/activity-page.component').then(m => m.ActivityPageComponent) },
      { path: 'analytics', loadComponent: () => import('./components/analytics-page/analytics-page.component').then(m => m.AnalyticsPageComponent) },
      { path: 'analytics/:id', loadComponent: () => import('./components/analytics-page/analytics-detail.component').then(m => m.AnalyticsDetailComponent) },
      // Marketplace routes
      { path: 'marketplace', loadComponent: () => import('../marketplace/marketplace-discover.component').then(m => m.MarketplaceDiscoverComponent) },
      { path: 'marketplace/detail/:id', loadComponent: () => import('../marketplace/marketplace-detail.component').then(m => m.MarketplaceDetailComponent) },
      { path: 'marketplace/publish', loadComponent: () => import('../marketplace/marketplace-publish.component').then(m => m.MarketplacePublishComponent) },
      { path: 'marketplace/configure/:id', loadComponent: () => import('../marketplace/marketplace-configure.component').then(m => m.MarketplaceConfigureComponent) },
      { path: 'marketplace-library', loadComponent: () => import('../marketplace/marketplace-library.component').then(m => m.MarketplaceLibraryComponent) },
      // Admin routes (platform staff). adminGuard checks "is staff" (GET /admin/me) AND the route's
      // `data.perm` capability — a staffer lacking it is bounced to the overview (backend also enforces).
      { path: 'admin', canActivate: [adminGuard], data: { perm: 'PLATFORM_DASHBOARD_VIEW' }, loadComponent: () => import('./components/admin-dashboard/admin-dashboard.component').then(m => m.AdminDashboardComponent) },
      { path: 'admin/users', canActivate: [adminGuard], data: { perm: 'USER_VIEW' }, loadComponent: () => import('./components/admin-users/admin-users.component').then(m => m.AdminUsersComponent) },
      { path: 'admin/organizations', canActivate: [adminGuard], data: { perm: 'ORG_VIEW' }, loadComponent: () => import('./components/admin-organizations/admin-organizations.component').then(m => m.AdminOrganizationsComponent) },
      { path: 'admin/ai-usage', canActivate: [adminGuard], data: { perm: 'AI_USAGE_VIEW' }, loadComponent: () => import('./components/admin-ai-usage/admin-ai-usage.component').then(m => m.AdminAiUsageComponent) },
      { path: 'admin/automations', canActivate: [adminGuard], data: { perm: 'AUTOMATION_OVERSIGHT_VIEW' }, loadComponent: () => import('./components/admin-automations/admin-automations.component').then(m => m.AdminAutomationsComponent) },
      { path: 'admin/audit-log', canActivate: [adminGuard], data: { perm: 'AUDIT_LOG_VIEW' }, loadComponent: () => import('./components/admin-audit-log/admin-audit-log.component').then(m => m.AdminAuditLogComponent) },
      { path: 'admin/plans', canActivate: [adminGuard], data: { perm: 'PLAN_VIEW' }, loadComponent: () => import('./components/admin-plans/admin-plans.component').then(m => m.AdminPlansComponent) },
      { path: 'admin/pricing', canActivate: [adminGuard], data: { perm: 'PLAN_VIEW' }, loadComponent: () => import('./components/admin-pricing/admin-pricing.component').then(m => m.AdminPricingComponent) },
      { path: 'admin/plans-subscriptions', canActivate: [adminGuard], data: { perm: 'PLAN_VIEW' }, loadComponent: () => import('./components/admin-subscriptions/admin-subscriptions.component').then(m => m.AdminSubscriptionsComponent) },
      { path: 'admin/quota', canActivate: [adminGuard], data: { perm: 'AI_USAGE_VIEW' }, loadComponent: () => import('./components/admin-quota/admin-quota.component').then(m => m.AdminQuotaComponent) },
      { path: 'admin/email-health', canActivate: [adminGuard], data: { perm: 'INFRA_VIEW' }, loadComponent: () => import('./components/admin-email-health/admin-email-health.component').then(m => m.AdminEmailHealthComponent) },
      { path: 'admin/jobs', canActivate: [adminGuard], data: { perm: 'INFRA_VIEW' }, loadComponent: () => import('./components/admin-background-jobs/admin-background-jobs.component').then(m => m.AdminBackgroundJobsComponent) },
      { path: 'admin/system-health', canActivate: [adminGuard], data: { perm: 'INFRA_VIEW' }, loadComponent: () => import('./components/admin-system-health/admin-system-health.component').then(m => m.AdminSystemHealthComponent) },
      { path: 'admin/moderation', canActivate: [adminGuard], data: { perm: 'MARKETPLACE_MODERATE' }, loadComponent: () => import('./components/admin-marketplace/admin-marketplace.component').then(m => m.AdminMarketplaceComponent) },
      { path: 'admin/reviews', canActivate: [adminGuard], data: { perm: 'MARKETPLACE_MODERATE' }, loadComponent: () => import('./components/admin-marketplace/admin-marketplace.component').then(m => m.AdminMarketplaceComponent) },
      // Scaffolded admin destinations — "not built yet" placeholder until their backend + UI land.
      { path: 'admin/gdpr', canActivate: [adminGuard], data: { perm: 'COMPLIANCE_VIEW' }, loadComponent: () => import('./components/admin-gdpr/admin-gdpr.component').then(m => m.AdminGdprComponent) },
      { path: 'admin/flags', canActivate: [adminGuard], data: { perm: 'FEATURE_FLAG_MANAGE' }, loadComponent: () => import('./components/admin-feature-flags/admin-feature-flags.component').then(m => m.AdminFeatureFlagsComponent) },
      { path: 'admin/announcements', canActivate: [adminGuard], data: { perm: 'ANNOUNCEMENT_MANAGE' }, loadComponent: () => import('./components/admin-announcements/admin-announcements.component').then(m => m.AdminAnnouncementsComponent) },
      { path: 'admin/staff', canActivate: [adminGuard], data: { perm: 'STAFF_MANAGE' }, loadComponent: () => import('./components/admin-staff/admin-staff.component').then(m => m.AdminStaffComponent) },
    ],
  },
] satisfies Routes;
