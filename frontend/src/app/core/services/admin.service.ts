import { Injectable, inject } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { ApiService } from './api.service';
import {
  AdminStats, AdminUserPage, AdminUser,
  AiUsageStats, AiUsageByUser, TimelineDataPoint,
  AutomationStats, AutomationExecutionPage,
  AdminAuditLogPage, AdminUserOrg, AdminMailbox,
  PlanModel, PlanRequest, StaffNote, AdminUserSessions,
  ModelPricing, ModelPricingRequest
} from '../../models/admin.model';

/** Wraps all admin API endpoints for user management, plan CRUD, audit logs, and AI usage statistics. */
@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly api = inject(ApiService);

  // Stats
  getStats() {
    return this.api.get<AdminStats>('/admin/stats');
  }

  // Users
  getUsers(search = '', role?: string, status?: string, plan?: string, page = 0, size = 20) {
    let params = new HttpParams()
      .set('search', search)
      .set('page', page)
      .set('size', size);
    if (role) params = params.set('role', role);
    if (status) params = params.set('status', status);
    if (plan) params = params.set('plan', plan);
    return this.api.get<AdminUserPage>('/admin/users', { params });
  }

  getUser(id: string) {
    return this.api.get<AdminUser>(`/admin/users/${id}`);
  }

  updateRole(id: string, role: string) {
    return this.api.patch<AdminUser>(`/admin/users/${id}/role`, { role });
  }

  updateStaffRole(id: string, staffRole: string | null) {
    return this.api.patch<AdminUser>(`/admin/users/${id}/staff-role`, { staffRole });
  }

  assignPlan(id: string, planId: string) {
    return this.api.patch<AdminUser>(`/admin/users/${id}/plan`, { planId });
  }

  disableUser(id: string) {
    return this.api.patch<void>(`/admin/users/${id}/disable`, {});
  }

  /** Organizations the user belongs to (Users detail → Organizations tab). */
  getUserOrganizations(id: string) {
    return this.api.get<AdminUserOrg[]>(`/admin/users/${id}/organizations`);
  }

  /** Mailboxes (email accounts) the user owns (Users detail → Mailboxes tab). */
  getUserMailboxes(id: string) {
    return this.api.get<AdminMailbox[]>(`/admin/users/${id}/mailboxes`);
  }

  // ── Users support tooling (Users detail → Notes + Sessions & Security tabs) ──
  /** Internal staff notes about a user, newest-first. */
  getUserNotes(id: string) {
    return this.api.get<StaffNote[]>(`/admin/users/${id}/notes`);
  }

  /** Adds an internal staff note; returns the created note. */
  addUserNote(id: string, body: string) {
    return this.api.post<StaffNote>(`/admin/users/${id}/notes`, { body });
  }

  /** Deletes a staff note (allowed for the note's author or a USER_MANAGE holder). */
  deleteUserNote(userId: string, noteId: string) {
    return this.api.delete<void>(`/admin/users/${userId}/notes/${noteId}`);
  }

  /** Triggers a password-reset email for the user. */
  resetUserPassword(id: string) {
    return this.api.post<void>(`/admin/users/${id}/reset-password`, {});
  }

  /** Active session count for the user. */
  getUserSessions(id: string) {
    return this.api.get<AdminUserSessions>(`/admin/users/${id}/sessions`);
  }

  /** Revokes all active sessions for the user (count is always 0 afterward). */
  revokeUserSessions(id: string) {
    return this.api.post<AdminUserSessions>(`/admin/users/${id}/revoke-sessions`, {});
  }

  // AI Usage
  getAiUsageStats() {
    return this.api.get<AiUsageStats>('/admin/ai-usage');
  }

  getAiUsageByUser() {
    return this.api.get<AiUsageByUser[]>('/admin/ai-usage/by-user');
  }

  getAiUsageTimeline(period = 'daily') {
    return this.api.get<TimelineDataPoint[]>('/admin/ai-usage/timeline', {
      params: new HttpParams().set('period', period)
    });
  }

  // Automations
  getAutomationStats() {
    return this.api.get<AutomationStats>('/admin/automations/stats');
  }

  getAutomationExecutions(page = 0, size = 20) {
    return this.api.get<AutomationExecutionPage>('/admin/automations/executions', {
      params: new HttpParams().set('page', page).set('size', size)
    });
  }

  // Audit Log
  getAuditLog(userId?: string, action?: string, page = 0, size = 20, organizationId?: string) {
    let params = new HttpParams().set('page', page).set('size', size);
    if (userId) params = params.set('user', userId);
    if (action) params = params.set('action', action);
    if (organizationId) params = params.set('organizationId', organizationId);
    return this.api.get<AdminAuditLogPage>('/admin/audit-log', { params });
  }

  exportAuditLogCsv(userId?: string, action?: string) {
    let params = new HttpParams();
    if (userId) params = params.set('user', userId);
    if (action) params = params.set('action', action);
    return this.api.getBlob('/admin/audit-log/export', { params });
  }

  // Plans
  getPlans() {
    return this.api.get<PlanModel[]>('/admin/plans');
  }

  createPlan(request: PlanRequest) {
    return this.api.post<PlanModel>('/admin/plans', request);
  }

  updatePlan(id: string, request: PlanRequest) {
    return this.api.put<PlanModel>(`/admin/plans/${id}`, request);
  }

  deletePlan(id: string) {
    return this.api.delete<void>(`/admin/plans/${id}`);
  }

  // AI model pricing (USD per million tokens) — editable rates that feed cost tracking.
  getModelPricing() {
    return this.api.get<ModelPricing[]>('/admin/pricing/models');
  }

  createModelPricing(request: ModelPricingRequest) {
    return this.api.post<ModelPricing>('/admin/pricing/models', request);
  }

  updateModelPricing(id: string, request: ModelPricingRequest) {
    return this.api.put<ModelPricing>(`/admin/pricing/models/${id}`, request);
  }

  deleteModelPricing(id: string) {
    return this.api.delete<void>(`/admin/pricing/models/${id}`);
  }
}
