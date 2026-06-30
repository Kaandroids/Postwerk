export interface AutomationTestCase {
  id: string;
  name: string;
  description: string | null;
  emailInput: TestEmailInput;
  assertions: TestAssertion[];
  sortOrder: number;
  lastResult: TestResultSummary | null;
  createdAt: string;
}

export interface TestEmailInput {
  from: string;
  to: string;
  subject: string;
  body: string;
  receivedAt: string | null;
  inReplyTo: string | null;
  categoryIds: string[] | null;
  /** Inbound-webhook trigger payload (for WEBHOOK-mode triggers). */
  triggerPayload?: Record<string, unknown> | null;
  /** Seeded INPUT-node field values when testing an integration (kind=INTEGRATION). */
  inputFields?: Record<string, unknown> | null;
  /** Mock attachment metadata so a FOREACH over email.attachments can be exercised in a dry-run test. */
  attachments?: TestAttachment[] | null;
}

/** A mock attachment for a test case (metadata only — name/type/size). */
export interface TestAttachment {
  name: string;
  contentType: string;
  size: number;
}

export interface TestAssertion {
  nodeId: string;
  expectedStatus: string;
  field: string | null;
  expectedValue: string | null;
}

export interface TestResultSummary {
  status: 'PASSED' | 'FAILED' | 'ERROR';
  passedCount: number;
  totalCount: number;
  durationMs: number;
  executedAt: string;
}

export interface AutomationTestResult {
  id: string;
  testCaseId: string;
  testCaseName: string;
  status: 'PASSED' | 'FAILED' | 'ERROR';
  nodeResults: NodeTraceResult[];
  assertionResults: AssertionResult[];
  durationMs: number;
  errorMessage: string | null;
  executedAt: string;
}

export interface NodeTraceResult {
  nodeId: string;
  nodeType: string;
  nodeLabel: string;
  resultStatus: string;
  resultDetail: Record<string, unknown>;
}

export interface AssertionResult {
  assertionIndex: number;
  passed: boolean;
  expected: string;
  actual: string;
}

export interface RunAllTestsResponse {
  totalTests: number;
  passed: number;
  failed: number;
  errors: number;
  results: AutomationTestResult[];
}
