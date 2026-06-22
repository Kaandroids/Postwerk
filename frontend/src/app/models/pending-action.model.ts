/** Supervised mode (#3a): an action parked for human approval. */
export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXPIRED';

export interface PendingAction {
  id: string;
  automationId: string;
  emailId: string | null;
  nodeId: string;
  /** Backend NodeType name, e.g. EMAIL_ACTION, SEND_EMAIL, WEBHOOK. */
  nodeType: string;
  nodeLabel: string | null;
  /** Resolved payload — exactly what will happen (rendered subject/body, recipient, folder, url, …). */
  actionDetail: Record<string, unknown>;
  status: ApprovalStatus;
  createdAt: string;
  decidedAt: string | null;
  decisionNote: string | null;
  /** The AI categorization that drove this action, if any (#3c). */
  triggerCategory: { id: string | null; name: string | null; confidence: number | null } | null;
}

export interface PendingActionPage {
  content: PendingAction[];
  totalElements: number;
  totalPages: number;
  number: number;
}
