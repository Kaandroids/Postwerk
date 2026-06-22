import { Page } from './page.model';

/** Metadata for a file attached to an email message. */
export interface Attachment {
  name: string;
  size: string;
  contentType: string;
}

/** Lightweight category reference assigned to an email. */
export interface EmailCategory {
  id: string;
  name: string;
  color: string;
}

/** Execution trace for a single automation node processed against an email. */
export interface EmailNodeTrace {
  id: string;
  nodeId: string;
  nodeType: string;
  nodeLabel: string;
  executionOrder: number;
  resultStatus: string;
  resultDetail: any;
  executedAt: string;
}

/** Complete trace of an automation execution against an email, including all node traces. */
export interface EmailAutomationTrace {
  id: string;
  automationId: string;
  automationName: string;
  automationColor: string;
  startedAt: string;
  completedAt: string;
  status: string;
  /** True when this trace was produced by a TESTING (Simulationsmodus) dry-run, not a live run. */
  simulation: boolean;
  nodeTraces: EmailNodeTrace[];
}

/** Compact email representation used in inbox list views with summary fields. */
export interface EmailListItem {
  id: string;
  messageId: string;
  folder: string;
  fromAddress: string;
  fromPersonal: string;
  toAddresses: string;
  ccAddresses: string;
  bccAddresses: string;
  subject: string;
  snippet: string;
  receivedAt: string;
  isRead: boolean;
  isStarred: boolean;
  hasAttachments: boolean;
  attachments: string | null;
  sizeBytes: number | null;
  categories: EmailCategory[];
  automationTraceCount: number;
  approvalStatus: string | null;
  processed: boolean;
}

/** Full email entity including body content, attachments, and automation traces. */
export interface Email {
  id: string;
  messageId: string;
  folder: string;
  fromAddress: string;
  fromPersonal: string;
  toAddresses: string;
  ccAddresses: string;
  bccAddresses: string;
  subject: string;
  bodyText: string | null;
  bodyHtml: string | null;
  snippet: string;
  receivedAt: string;
  isRead: boolean;
  isStarred: boolean;
  hasAttachments: boolean;
  attachments: string | null;
  sizeBytes: number | null;
  categories: EmailCategory[];
  automationTraceCount: number;
  approvalStatus: string | null;
  processed: boolean;
  automationTraces: EmailAutomationTrace[];
}

/** Paginated response of email list items with navigation metadata. */
export interface EmailPage extends Page<EmailListItem> {
  first: boolean;
  last: boolean;
}

/** Result of an IMAP email synchronization operation. */
export interface EmailSyncResult {
  newEmailCount: number;
  syncedAt: string;
}

/** Request payload for composing/sending an email. */
export interface ComposeEmail {
  to: string;
  cc: string;
  bcc: string;
  subject: string;
  body: string;
  inReplyTo: string;
  replyToEmailId: string;
  isDraft: boolean;
}

/** Response after sending or saving a draft. */
export interface ComposeEmailResponse {
  id: string | null;
  messageId: string | null;
  folder: string;
  toAddresses: string;
  ccAddresses: string;
  bccAddresses: string;
  subject: string;
  bodyHtml: string;
  sentAt: string;
  isDraft: boolean;
  attachments: DraftAttachment[];
}

/** Metadata for a draft attachment. */
export interface DraftAttachment {
  id: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
}

/** Represents a remote IMAP folder with its role, message counts, and sync status. */
export interface ImapFolder {
  id: string;
  name: string;
  role: 'INBOX' | 'SENT' | 'SPAM' | 'TRASH' | 'DRAFTS' | 'OTHER';
  messageCount: number;
  unreadCount: number;
  lastSyncedAt: string | null;
}
