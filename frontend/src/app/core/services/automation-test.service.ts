import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import {
  AutomationTestCase,
  AutomationTestResult,
  RunAllTestsResponse,
  TestEmailInput,
  TestAssertion,
} from '../../models/automation-test.model';

export interface AutomationTestCaseRequest {
  name: string;
  description: string | null;
  emailInput: TestEmailInput;
  assertions: TestAssertion[];
}

/** Manages automation test case CRUD and dry-run execution via the automation testing API. */
@Injectable({ providedIn: 'root' })
export class AutomationTestService {
  private api = inject(ApiService);

  getTests(automationId: string): Observable<AutomationTestCase[]> {
    return this.api.get<AutomationTestCase[]>(`/automations/${automationId}/tests`);
  }

  createTest(automationId: string, req: AutomationTestCaseRequest): Observable<AutomationTestCase> {
    return this.api.post<AutomationTestCase>(`/automations/${automationId}/tests`, req);
  }

  updateTest(automationId: string, testId: string, req: AutomationTestCaseRequest): Observable<AutomationTestCase> {
    return this.api.put<AutomationTestCase>(`/automations/${automationId}/tests/${testId}`, req);
  }

  deleteTest(automationId: string, testId: string): Observable<void> {
    return this.api.delete<void>(`/automations/${automationId}/tests/${testId}`);
  }

  getLatestResult(automationId: string, testId: string): Observable<AutomationTestResult> {
    return this.api.get<AutomationTestResult>(`/automations/${automationId}/tests/${testId}/latest-result`);
  }

  runTest(automationId: string, testId: string): Observable<AutomationTestResult> {
    return this.api.post<AutomationTestResult>(`/automations/${automationId}/tests/${testId}/run`, {});
  }

  runAllTests(automationId: string): Observable<RunAllTestsResponse> {
    return this.api.post<RunAllTestsResponse>(`/automations/${automationId}/tests/run-all`, {});
  }
}
