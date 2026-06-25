import { Injectable, inject } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { ApiService } from './api.service';
import {
  Automation,
  AutomationConstantInput,
  AutomationDetail,
  AutomationExecution,
  AutomationExport,
  AutomationRequest,
  AutomationStatus,
  FlowUpdateRequest,
  TestModeResult,
  TestModeStats,
} from '../../models/automation.model';
import { ImportResult } from '../../models/import-result.model';

/**
 * Provides CRUD operations for email automations, including flow updates,
 * execution history retrieval, manual triggering, and import/export capabilities.
 */
@Injectable({ providedIn: 'root' })
export class AutomationService {
  private readonly api = inject(ApiService);
  private readonly basePath = '/automations';

  list() {
    return this.api.get<Automation[]>(this.basePath);
  }

  /** Lists the user's integrations (trigger-less reusable flows, kind=INTEGRATION). */
  listIntegrations() {
    return this.api.get<Automation[]>(`${this.basePath}/integrations`);
  }

  get(id: string) {
    return this.api.get<AutomationDetail>(`${this.basePath}/${id}`);
  }

  create(request: AutomationRequest) {
    return this.api.post<Automation>(this.basePath, request);
  }

  update(id: string, request: AutomationRequest) {
    return this.api.put<Automation>(`${this.basePath}/${id}`, request);
  }

  delete(id: string) {
    return this.api.delete<void>(`${this.basePath}/${id}`);
  }

  toggleLock(id: string) {
    return this.api.patch<Automation>(`${this.basePath}/${id}/lock`, {});
  }

  updateStatus(id: string, status: AutomationStatus) {
    return this.api.patch<Automation>(`${this.basePath}/${id}/status`, { status });
  }

  updateFlow(id: string, request: FlowUpdateRequest) {
    return this.api.put<AutomationDetail>(`${this.basePath}/${id}/flow`, request);
  }

  updateConstants(id: string, constants: AutomationConstantInput[]) {
    return this.api.put<AutomationDetail>(`${this.basePath}/${id}/constants`, { constants });
  }

  getExecutions(id: string, page = 0, size = 20) {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.api.get<{ content: AutomationExecution[]; totalElements: number }>(
      `${this.basePath}/${id}/executions`,
      { params }
    );
  }

  /**
   * Fires the automation's MANUAL trigger. {@link parameters} are user-entered values keyed by the
   * trigger's parameter-set field names; they are seeded as {@code trigger.*} variables for the run.
   */
  runManually(id: string, parameters?: Record<string, unknown>) {
    return this.api.post<void>(`${this.basePath}/${id}/run`, { parameters: parameters ?? null });
  }

  export() {
    return this.api.get<AutomationExport[]>(`${this.basePath}/export`);
  }

  import(data: AutomationExport[]) {
    return this.api.post<ImportResult>(`${this.basePath}/import`, data);
  }

  // ─── Test Mode ─────────────────────────────────────────

  getTestModeResults(id: string, feedback?: string, page = 0, size = 20) {
    let params = new HttpParams().set('page', page).set('size', size);
    if (feedback) params = params.set('feedback', feedback);
    return this.api.get<{ content: TestModeResult[]; totalElements: number }>(
      `${this.basePath}/${id}/test-mode/results`,
      { params }
    );
  }

  submitTestModeFeedback(id: string, resultId: string, feedback: 'CORRECT' | 'INCORRECT', note?: string) {
    return this.api.patch<TestModeResult>(
      `${this.basePath}/${id}/test-mode/results/${resultId}/feedback`,
      { feedback, note: note || null }
    );
  }

  getTestModeStats(id: string) {
    return this.api.get<TestModeStats>(`${this.basePath}/${id}/test-mode/stats`);
  }

  clearTestModeResults(id: string) {
    return this.api.delete<void>(`${this.basePath}/${id}/test-mode/results`);
  }

  /** Runs one already-synced email through an automation in dry-run and returns the simulated result. */
  simulateEmail(automationId: string, emailId: string) {
    return this.api.post<TestModeResult>(
      `${this.basePath}/${automationId}/test-mode/simulate/${emailId}`,
      {}
    );
  }

  /** Deletes a single test-mode result so it no longer counts toward the accuracy statistics. */
  deleteTestModeResult(automationId: string, resultId: string) {
    return this.api.delete<void>(`${this.basePath}/${automationId}/test-mode/results/${resultId}`);
  }
}
