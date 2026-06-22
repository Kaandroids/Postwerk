/** Production activity feed (#3d): a live automation run and its steps. */
export interface ActivityStep {
  nodeType: string;
  nodeLabel: string | null;
  resultStatus: string;
  summary: string;
}

export interface ActivityEntry {
  traceId: string;
  automationId: string;
  automationName: string;
  automationColor: string | null;
  emailSubject: string | null;
  emailFrom: string | null;
  /** SUCCESS | FAILED | RUNNING */
  status: string;
  startedAt: string;
  completedAt: string | null;
  errorMessage: string | null;
  steps: ActivityStep[];
}

export interface ActivityPage {
  content: ActivityEntry[];
  totalElements: number;
  totalPages: number;
  number: number;
}
