/** Summary representation of an automation workflow with status and node/edge counts. */
export interface Automation {
  id: string;
  name: string;
  description: string | null;
  type: AutomationType;
  kind: AutomationKind;
  status: AutomationStatus;
  /** TRIGGER node mode; present on list responses, used to gate the manual "Run now" action. */
  triggerMode?: TriggerMode;
  color: string;
  nodeCount: number;
  edgeCount: number;
  totalExecutions: number;
  successCount: number;
  failedCount: number;
  locked: boolean;
  lastRunAt: string | null;
  createdAt: string;
  updatedAt: string;
  testModeStats: TestModeStats | null;
}

/** Full automation entity including flow data, nodes, and edges for the editor. */
export interface AutomationDetail {
  id: string;
  name: string;
  description: string | null;
  type: AutomationType;
  kind: AutomationKind;
  status: AutomationStatus;
  color: string;
  flowData: string | null;
  nodes: AutomationNodeDto[];
  edges: AutomationEdgeDto[];
  constants: AutomationConstant[];
  lastRunAt: string | null;
  locked: boolean;
  createdAt: string;
  updatedAt: string;
}

/** A user-defined automation constant, referenced in templates as {{const.NAME}}. */
/** The value type of an automation constant. */
export type ConstantType = 'text' | 'number' | 'boolean' | 'url' | 'secret';

export interface AutomationConstant {
  name: string;
  value: string;
  type: ConstantType;
  /** Optional human description of what the constant is for. */
  description?: string;
  /** Response-only: true when a secret has a stored value (plaintext is never returned). */
  hasValue?: boolean;
}

/** Request payload for persisting constants (description may be null to clear it). */
export interface AutomationConstantInput {
  name: string;
  value: string;
  type: ConstantType;
  description?: string | null;
}

/**
 * Presentation metadata (icon name + theme-variable accent colour) per constant type.
 * Single source of truth shared by the automation editor and the marketplace configure surface.
 */
export const CONSTANT_TYPE_META: Record<ConstantType, { icon: string; color: string }> = {
  text:    { icon: 'type',        color: 'var(--accent)' },
  number:  { icon: 'hash',        color: 'var(--warning)' },
  boolean: { icon: 'toggleRight', color: 'var(--success)' },
  url:     { icon: 'link',        color: 'var(--accent)' },
  secret:  { icon: 'key',         color: 'var(--danger)' },
};

/** Data transfer object for an automation node with position and configuration. */
export interface AutomationNodeDto {
  id: string;
  nodeType: NodeType;
  label: string | null;
  positionX: number;
  positionY: number;
  config: string;
  /** Friendly per-automation key used in node-scoped variable namespaces (e.g. http_1). */
  nodeKey?: string | null;
}

/** Data transfer object for a directed edge connecting two automation nodes. */
export interface AutomationEdgeDto {
  id: string;
  sourceNodeId: string;
  sourceHandle: string;
  targetNodeId: string;
  targetHandle: string;
}

/** Portable automation representation used for JSON export and import. */
export interface AutomationExport {
  name: string;
  description: string | null;
  color: string;
  status: string;
  nodes: AutomationNodeExport[];
  edges: AutomationEdgeExport[];
  flowData: string | null;
  constants: AutomationConstant[];
}

/** Exported node with a temporary ID for cross-referencing edges during import. */
export interface AutomationNodeExport {
  tempId: string;
  nodeType: string;
  label: string | null;
  positionX: number;
  positionY: number;
  config: string;
}

/** Exported edge referencing source and target nodes by temporary IDs. */
export interface AutomationEdgeExport {
  sourceTempId: string;
  sourceHandle: string;
  targetTempId: string;
  targetHandle: string;
}

/** Request payload for creating or updating an automation workflow. */
export interface AutomationRequest {
  name: string;
  description?: string | null;
  color?: string;
  kind?: AutomationKind;
}

/** Request payload for saving the visual flow editor state (nodes, edges, viewport). */
export interface FlowUpdateRequest {
  nodes: FlowNodeRequest[];
  edges: FlowEdgeRequest[];
  viewport?: string;
}

/** Request representation of a node within a flow update. */
export interface FlowNodeRequest {
  id: string;
  nodeType: NodeType;
  label: string | null;
  positionX: number;
  positionY: number;
  config: string;
  nodeKey?: string | null;
}

/** Request representation of an edge within a flow update. */
export interface FlowEdgeRequest {
  id: string;
  sourceNodeId: string;
  sourceHandle: string;
  targetNodeId: string;
  targetHandle: string;
}

/** Record of a single automation execution run with status and error details. */
export interface AutomationExecution {
  id: string;
  status: ExecutionStatus;
  triggeredAt: string;
  completedAt: string | null;
  processedCount: number;
  errorLog: string | null;
}

/** The category of automation workflow. */
export type AutomationType = 'EMAIL';
/** Whether a flow is a trigger-driven automation or a callable, trigger-less integration. */
export type AutomationKind = 'AUTOMATION' | 'INTEGRATION';
/** Lifecycle status of an automation workflow. */
export type AutomationStatus = 'ACTIVE' | 'TESTING' | 'PAUSED';
/** Discriminator for the type of node in an automation flow. */
export type NodeType = 'TRIGGER' | 'FILTER' | 'EXTRACT' | 'CATEGORIZE' | 'DELAY' | 'LABEL' | 'EMAIL_ACTION' | 'REMOVE_LABEL' | 'WEBHOOK' | 'SEND_EMAIL' | 'INPUT' | 'OUTPUT' | 'INTEGRATION_CALL' | 'VECTOR_SEARCH' | 'NOTIFY';
/** Status of an automation execution run. */
export type ExecutionStatus = 'RUNNING' | 'SUCCESS' | 'FAILED';

/** Generic key-value configuration map for an automation node. */
export interface NodeConfig {
  [key: string]: unknown;
}

/** Configuration for a FILTER node containing multi-check DNF conditions. */
export interface FilterNodeConfig {
  checks: FilterCheck[];
}

/** A single check within a FILTER node (one output branch). */
export interface FilterCheck {
  label: string;
  groups: FilterConditionGroup[];
}

/** A group of AND-combined conditions (groups are OR-combined). */
export interface FilterConditionGroup {
  conditions: FilterCondition[];
}

/** A single condition within a filter group, evaluated against variables. */
export interface FilterCondition {
  field: string;           // "email.from", "extraction_0.amount", "category.id"
  operator: string;        // "EQUALS", "CONTAINS", "GREATER_THAN", etc.
  value: string;
}

/** Variable system types for upstream node outputs. */
export interface VariableInfo {
  key: string;             // "email.from"
  label: string;           // "E-Mail Absender"
  type: 'text' | 'number' | 'boolean' | 'date';
  group: string;           // "email", "extraction_0", "http_node5"
}

/** Discriminator for EMAIL_ACTION node modes. */
export type EmailActionMode = 'REPLY' | 'FORWARD' | 'MOVE_FOLDER';

/** Source of an email's subject/body: a saved template or a manually written draft. */
export type EmailContentSource = 'VORLAGE' | 'MANUAL';

/** Configuration for a unified EMAIL_ACTION node. */
export interface EmailActionNodeConfig {
  actionMode: EmailActionMode;
  // REPLY mode
  contentSource?: EmailContentSource;
  templateId?: string;     // when contentSource === 'VORLAGE'
  subject?: string;        // when contentSource === 'MANUAL'
  body?: string;           // when contentSource === 'MANUAL'
  // FORWARD mode
  toAddress?: string;
  // MOVE_FOLDER mode
  folder?: string;
}

export interface SendEmailNodeConfig {
  /** Account used as the sender. Required when the automation has no email trigger (e.g. webhook). */
  senderAccountId?: string;
  to: string;
  cc: string;
  bcc: string;
  /** Whether subject/body come from a saved template or a manual draft. */
  contentSource?: EmailContentSource;
  /** Template used when contentSource === 'VORLAGE'. */
  templateId?: string;
  subject: string;
  body: string;
}


/** Configuration for a REMOVE_LABEL node. */
export interface RemoveLabelNodeConfig {
  categoryId: string;
}

/** Configuration for a WEBHOOK node. */
export interface WebhookNodeConfig {
  url: string;
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  headers?: WebhookHeader[];
  body?: string;
  authType: 'NONE' | 'BEARER' | 'BASIC' | 'API_KEY';
  authToken?: string;
  authUsername?: string;
  authPassword?: string;
  authHeaderName?: string;
  authSecretId?: string;
  timeout?: number;
  retryCount?: number;
  retryDelayMs?: number;
  retryOn?: string[];
  expectedResponse?: WebhookResponseField[];
  responseSchemas?: WebhookResponseSchema[];
}

/** A single expected JSON field from a webhook API response. */
export interface WebhookResponseField {
  key: string;
  label: string;
}

/** Maps an HTTP status condition to a ParameterSet for webhook response field resolution. */
export interface WebhookResponseSchema {
  name: string;
  condition: string;
  parameterSetId: string;
}

/** A single insertable variable reference. */
export interface VariableItem {
  key: string;
  label: string;
  description?: string;
}

/** Group of variables with collapsible state support. */
export interface VariableGroup {
  label: string;
  icon: string;
  color: string;
  variables: VariableItem[];
}

/** A single HTTP header key-value pair for webhook configuration. */
export interface WebhookHeader {
  key: string;
  value: string;
}

export const NODE_PALETTE: NodePaletteItem[] = [
  { type: 'TRIGGER', labelKey: 'auto_node_trigger', subKey: 'auto_node_sub_trigger', icon: 'zap', color: '#10b981', descKey: 'auto_node_desc_trigger' },
  { type: 'FILTER', labelKey: 'auto_node_filter', subKey: 'auto_node_sub_filter', icon: 'filter', color: '#3b82f6', descKey: 'auto_node_desc_filter' },
  { type: 'CATEGORIZE', labelKey: 'auto_node_categorize', subKey: 'auto_node_sub_categorize', icon: 'tag', color: '#ec4899', descKey: 'auto_node_desc_categorize' },
  { type: 'EXTRACT', labelKey: 'auto_node_extract', subKey: 'auto_node_sub_extract', icon: 'sparkle', color: '#8b5cf6', descKey: 'auto_node_desc_extract' },
  { type: 'LABEL', labelKey: 'auto_node_label_type', subKey: 'auto_node_sub_label', icon: 'tag', color: '#14b8a6', descKey: 'auto_node_desc_label' },
  { type: 'REMOVE_LABEL', labelKey: 'auto_node_remove_label', subKey: 'auto_node_sub_remove_label', icon: 'tagX', color: '#ef4444', descKey: 'auto_node_desc_remove_label' },
  { type: 'EMAIL_ACTION', labelKey: 'auto_node_email_action', subKey: 'auto_node_sub_email_action', icon: 'mail', color: '#f59e0b', descKey: 'auto_node_desc_email_action' },
  { type: 'WEBHOOK', labelKey: 'auto_node_webhook', subKey: 'auto_node_sub_webhook', icon: 'globe', color: '#06b6d4', descKey: 'auto_node_desc_webhook' },
  { type: 'SEND_EMAIL', labelKey: 'auto_node_send_email', subKey: 'auto_node_sub_send_email', icon: 'send', color: '#6366f1', descKey: 'auto_node_desc_send_email' },
  { type: 'DELAY', labelKey: 'auto_node_delay', subKey: 'auto_node_sub_delay', icon: 'clock', color: '#64748b', descKey: 'auto_node_desc_delay' },
  { type: 'INTEGRATION_CALL', labelKey: 'auto_node_integration_call', subKey: 'auto_node_sub_integration_call', icon: 'cube', color: '#a855f7', descKey: 'auto_node_desc_integration_call' },
  { type: 'INPUT', labelKey: 'auto_node_input', subKey: 'auto_node_sub_input', icon: 'signin', color: '#10b981', descKey: 'auto_node_desc_input' },
  { type: 'OUTPUT', labelKey: 'auto_node_output', subKey: 'auto_node_sub_output', icon: 'signout', color: '#f59e0b', descKey: 'auto_node_desc_output' },
  { type: 'VECTOR_SEARCH', labelKey: 'auto_node_vector_search', subKey: 'auto_node_sub_vector_search', icon: 'sparkle', color: '#0ea5e9', descKey: 'auto_node_desc_vector_search' },
  { type: 'NOTIFY', labelKey: 'auto_node_notify', subKey: 'auto_node_sub_notify', icon: 'bell', color: '#6d28d9', descKey: 'auto_node_desc_notify' },
];

/** Metadata for a node type entry in the editor's drag-and-drop palette. */
export interface NodePaletteItem {
  type: NodeType;
  labelKey: string;
  subKey?: string;
  icon: string;
  color: string;
  descKey: string;
}

/** A group of palette items shown as a dropdown in the toolbar. */
export interface PaletteGroup {
  labelKey: string;
  icon: string;
  items: NodePaletteItem[];
}

export const PALETTE_GROUPS: PaletteGroup[] = [
  {
    labelKey: 'auto_palette_triggers',
    icon: 'zap',
    items: NODE_PALETTE.filter(p => p.type === 'TRIGGER'),
  },
  {
    labelKey: 'auto_palette_processing',
    icon: 'filter',
    items: NODE_PALETTE.filter(p => ['FILTER', 'CATEGORIZE'].includes(p.type)),
  },
  {
    labelKey: 'auto_palette_data',
    icon: 'sparkle',
    items: NODE_PALETTE.filter(p => ['EXTRACT', 'LABEL', 'REMOVE_LABEL', 'VECTOR_SEARCH'].includes(p.type)),
  },
  {
    labelKey: 'auto_palette_actions',
    icon: 'bolt',
    items: NODE_PALETTE.filter(p => ['EMAIL_ACTION', 'WEBHOOK', 'SEND_EMAIL', 'NOTIFY', 'INTEGRATION_CALL'].includes(p.type)),
  },
  {
    labelKey: 'auto_palette_control',
    icon: 'clock',
    items: NODE_PALETTE.filter(p => p.type === 'DELAY'),
  },
];

/**
 * Palette groups shown only inside the integration editor: the INPUT entry point and the optional
 * OUTPUT return point. Processing/data/action groups from {@link PALETTE_GROUPS} are reused, while the
 * trigger group is hidden (integrations are trigger-less).
 */
export const INTEGRATION_PALETTE_GROUPS: PaletteGroup[] = [
  {
    labelKey: 'auto_palette_io',
    icon: 'signin',
    items: NODE_PALETTE.filter(p => ['INPUT', 'OUTPUT'].includes(p.type)),
  },
  ...PALETTE_GROUPS.filter(g => g.labelKey !== 'auto_palette_triggers').map(g => ({
    ...g,
    items: g.items.filter(p => p.type !== 'INTEGRATION_CALL'),
  })),
];

/** Trigger mode discriminator. */
export type TriggerMode = 'EMAIL' | 'WEBHOOK' | 'CRON' | 'MANUAL';

/** Configuration for a TRIGGER node (unified: Email, Webhook, Cron, Manual modes). */
export interface TriggerNodeConfig {
  triggerMode: TriggerMode;
  // EMAIL mode (also used by WEBHOOK + MANUAL modes for downstream account scoping)
  accountIds?: string[];
  // WEBHOOK mode (INBOUND receiver): set by the backend on save, used to build the public URL
  webhookEndpointId?: string;
  webhookToken?: string;
  parameterSetId?: string;
  // CRON mode
  scheduleType?: 'INTERVAL' | 'CRON';
  intervalMinutes?: number;
  cronExpression?: string;
  preset?: string;
}

/** Configuration for an EXTRACT node defining which parameter sets to extract from emails. */
export interface ExtractNodeConfig {
  extractions: ExtractionEntry[];
  sourceVariables?: string[];
}

/** A single extraction target linking a parameter set to a display label. */
export interface ExtractionEntry {
  parameterSetId: string;
  label: string;
}

/** Configuration for a CATEGORIZE node with category IDs and confidence threshold. */
export interface CategorizeNodeConfig {
  categoryIds: string[];
  threshold: number;
  sourceVariables?: string[];
}

/** Predefined cron presets for the TRIGGER node's CRON mode. */
export interface CronPreset {
  key: string;
  labelKey: string;
  scheduleType: 'INTERVAL' | 'CRON';
  intervalMinutes?: number;
  cronExpression?: string;
}

export const CRON_PRESETS: CronPreset[] = [
  { key: 'every5', labelKey: 'auto_schedule_every5', scheduleType: 'INTERVAL', intervalMinutes: 5 },
  { key: 'every15', labelKey: 'auto_schedule_every15', scheduleType: 'INTERVAL', intervalMinutes: 15 },
  { key: 'every30', labelKey: 'auto_schedule_every30', scheduleType: 'INTERVAL', intervalMinutes: 30 },
  { key: 'hourly', labelKey: 'auto_schedule_hourly', scheduleType: 'INTERVAL', intervalMinutes: 60 },
  { key: 'daily', labelKey: 'auto_schedule_daily', scheduleType: 'CRON', cronExpression: '0 9 * * *' },
  { key: 'weekly', labelKey: 'auto_schedule_weekly', scheduleType: 'CRON', cronExpression: '0 9 * * 1' },
  { key: 'monthly', labelKey: 'auto_schedule_monthly', scheduleType: 'CRON', cronExpression: '0 9 1 * *' },
  { key: 'custom', labelKey: 'auto_schedule_custom', scheduleType: 'CRON' },
];

/** Configuration for a DELAY node specifying wait time. */
export interface DelayNodeConfig {
  delayMinutes: number;
}

/** Configuration for a LABEL node specifying the category to assign. */
export interface LabelNodeConfig {
  categoryId?: string;
}

export const DEFAULT_COLORS = [
  '#3b82f6', '#8b5cf6', '#f59e0b', '#10b981',
  '#ef4444', '#ec4899', '#06b6d4', '#64748b',
];

/** Derives node color from NODE_PALETTE. Single source of truth for node type colors. */
export function getNodeColor(type: string): string {
  return NODE_PALETTE.find(p => p.type === type)?.color ?? 'var(--fg-muted)';
}

/** Derives node icon name from NODE_PALETTE. Single source of truth for node type icons. */
export function getNodeIcon(type: string): string {
  return NODE_PALETTE.find(p => p.type === type)?.icon ?? 'circle';
}

/** Derives node description i18n key from NODE_PALETTE. */
export function getNodeDescKey(type: string): string {
  return NODE_PALETTE.find(p => p.type === type)?.descKey ?? '';
}

/** Derives node label i18n key from NODE_PALETTE. Single source of truth for node labels. */
export function getNodeLabelKey(type: string): string {
  return NODE_PALETTE.find(p => p.type === type)?.labelKey ?? '';
}

/**
 * Default config object for each node type, used when creating a fresh node.
 * Single source of truth — consumed by the editor's add-node flows.
 */
export const NODE_DEFAULT_CONFIG: Record<NodeType, Record<string, unknown>> = {
  TRIGGER: { triggerMode: 'EMAIL', accountIds: [] },
  FILTER: { checks: [{ label: 'Check 1', groups: [{ conditions: [{ field: 'email.from', operator: 'CONTAINS', value: '' }] }] }] },
  EXTRACT: { extractions: [] },
  CATEGORIZE: { categoryIds: [], threshold: 70 },
  DELAY: { delayMinutes: 30 },
  LABEL: { categoryId: '' },
  EMAIL_ACTION: { actionMode: 'REPLY', contentSource: 'VORLAGE', templateId: '' },
  REMOVE_LABEL: { categoryId: '' },
  WEBHOOK: { url: '', method: 'POST', authType: 'NONE', headers: [], body: '', timeout: 30, responseSchemas: [{ name: 'Success', condition: '2xx', parameterSetId: '' }, { name: 'Unmatched', condition: 'unmatched', parameterSetId: '' }] },
  SEND_EMAIL: { senderAccountId: '', to: '', cc: '', bcc: '', contentSource: 'VORLAGE', templateId: '', subject: '', body: '' },
  INPUT: { parameterSetId: '' },
  OUTPUT: { parameterSetId: '', outputMappings: {} },
  INTEGRATION_CALL: { integrationId: '', inputMappings: {}, instanceSettings: {} },
  VECTOR_SEARCH: { knowledgeBaseId: '', queryVariable: '', topK: 5, confidenceThreshold: 90 },
  NOTIFY: { recipientType: 'USER', contentSource: 'MANUAL', title: '', message: '', severity: 'INFO', category: 'SYSTEM' },
};

/** Configuration for an INPUT node — references the ParameterSet defining the integration's input shape. */
export interface InputNodeConfig {
  parameterSetId: string;
  [key: string]: unknown;
}

/** Configuration for an OUTPUT node — references the returned ParameterSet + per-field source expressions. */
export interface OutputNodeConfig {
  parameterSetId: string;
  outputMappings: Record<string, string>;
  [key: string]: unknown;
}

/** Configuration for an INTEGRATION_CALL node — the two-zone mapper invoking a reusable integration. */
export interface IntegrationCallNodeConfig {
  integrationId: string;
  /** Dynamic inputs: each integration input field mapped from a caller expression ({{...}}). */
  inputMappings: Record<string, string>;
  /** Instance settings: per-call internal constant overrides (Phase 1: stored, not injected). */
  instanceSettings: Record<string, string>;
  /** Cached input field names of the chosen integration (snapshotted when picked) for mapper rows. */
  inputFields?: string[];
  /** Cached output field names exposed downstream as integration_<nodeId>.<field>. */
  outputFields?: string[];
  [key: string]: unknown;
}

/** Configuration for a VECTOR_SEARCH node — knowledge-base semantic match with confidence routing. */
export interface VectorSearchNodeConfig {
  /** The knowledge base to search. */
  knowledgeBaseId: string;
  /** Query expression (free text + {{variable}} tokens) resolved at runtime, e.g. {{extraction_1.position}}. */
  queryVariable: string;
  /** Max candidates shortlisted for the judge (1–10). */
  topK: number;
  /** Minimum judge confidence (0–100) to take the success handle. */
  confidenceThreshold: number;
  /** Cached field names of the selected KB's parameter set, so the variable graph can expose
   *  {@code vectorsearch_<nodeId>.match.<field>} downstream (snapshotted when the KB is picked). */
  matchFields?: string[];
  /** Cached display name of the selected KB, for the node card's config summary. */
  knowledgeBaseName?: string;
  [key: string]: unknown;
}

/** Recipient resolution mode for a NOTIFY node. */
export type NotifyRecipientType = 'USER' | 'OWNER' | 'ADMINS';

/** Severity of an in-app notification raised by a NOTIFY node. */
export type NotifySeverity = 'INFO' | 'WARNING' | 'CRITICAL';

/** Category bucket of an in-app notification raised by a NOTIFY node. */
export type NotifyCategory = 'AUTOMATION' | 'APPROVAL' | 'QUOTA' | 'MAILBOX' | 'TEAM' | 'MARKETPLACE' | 'SYSTEM';

/** Configuration for a NOTIFY node — raises an in-app notification to org members. */
export interface NotifyNodeConfig {
  /** Who receives the notification. USER targets a specific org member via {@link recipientUserId}. */
  recipientType: NotifyRecipientType;
  /** Target org member id — only used when {@link recipientType} === 'USER'. */
  recipientUserId?: string;
  /** Content source: a saved template (VORLAGE) or inline title/message (MANUAL). Default MANUAL. */
  contentSource?: 'VORLAGE' | 'MANUAL';
  /** Template id whose subject→title and body→message are used — only when contentSource === 'VORLAGE'. */
  templateId?: string;
  /** Notification title (supports {{variable}} interpolation) — used in MANUAL mode. */
  title: string;
  /** Notification body (supports {{variable}} interpolation) — used in MANUAL mode. */
  message: string;
  /** Visual/importance level. */
  severity: NotifySeverity;
  /** Notification category bucket. */
  category: NotifyCategory;
  /** Optional deep link opened when the notification is clicked. */
  linkUrl?: string;
  [key: string]: unknown;
}

// ─── Test Mode ─────────────────────────────────────────────

/** Statistics for an automation in TESTING mode. */
export interface TestModeStats {
  total: number;
  correct: number;
  incorrect: number;
  pending: number;
  accuracyPercent: number;
}

/** A single simulated action from a test mode execution. */
export interface SimulatedAction {
  nodeType: string;
  nodeLabel: string;
  description: string;
}

/** A test mode result representing one email processed in dry-run. */
export interface TestModeResult {
  id: string;
  emailId: string;
  emailSubject: string | null;
  emailFrom: string | null;
  emailReceivedAt: string | null;
  traceId: string | null;
  simulatedActions: SimulatedAction[];
  feedback: 'CORRECT' | 'INCORRECT' | null;
  feedbackNote: string | null;
  createdAt: string;
  feedbackAt: string | null;
}
