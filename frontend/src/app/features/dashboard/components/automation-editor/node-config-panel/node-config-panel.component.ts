import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, Signal, WritableSignal, computed, effect, inject, signal } from '@angular/core';
import { IPoint } from '@foblex/2d';
import { I18nService } from '../../../../../core/services/i18n.service';
import { IconComponent } from '../../../../../shared/components/icon/icon.component';
import { VariableComboboxComponent } from '../variable-combobox/variable-combobox.component';
import { NodeInfoModalComponent } from '../node-info-modal/node-info-modal.component';
import { WorkspaceService } from '../../../../../core/services/workspace.service';
import { WebhookEndpointService } from '../../../../../core/services/webhook-endpoint.service';
import { AutomationService } from '../../../../../core/services/automation.service';
import { KnowledgeBaseService } from '../../../../../core/services/knowledge-base.service';
import { OrganizationService } from '../../../../../core/services/organization.service';
import { ViewportService } from '../../../../../core/services/viewport.service';
import { VariableGraphService } from '../variable-graph.service';
import {
  Automation,
  AutomationDetail,
  InputNodeConfig,
  OutputNodeConfig,
  IntegrationCallNodeConfig,
  NodeType,
  FilterNodeConfig,
  FilterCheck,
  ExtractNodeConfig,
  CategorizeNodeConfig,
  TriggerNodeConfig,
  TriggerMode,
  DelayNodeConfig,
  LabelNodeConfig,
  EmailActionNodeConfig,
  EmailActionMode,
  RemoveLabelNodeConfig,
  WebhookNodeConfig,
  WebhookResponseSchema,
  CRON_PRESETS,
  VariableGroup,
  EmailContentSource,
  getNodeColor,
  getNodeIcon,
} from '../../../../../models/automation.model';
import { OrgMember } from '../../../../../models/organization.model';
import { Category } from '../../../../../models/category.model';
import { Template } from '../../../../../models/template.model';
import { ParameterSet } from '../../../../../models/parameter-set.model';
import { KnowledgeBase } from '../../../../../models/knowledge-base.model';
import { EmailAccount } from '../../../../../models/email-account.model';
import { Secret } from '../../../../../models/secret.model';
import { WebhookAuthMode, WebhookEndpoint } from '../../../../../models/webhook-endpoint.model';
import { v } from '../../../../../shared/utils/event.util';
import { humanizeError } from '../../../../../shared/utils/error.util';
import {
  parseCronTime,
  parseCronDay,
  parseCronDayOfMonth,
  parseTimeParts,
  buildDailyCron,
  buildWeeklyCron,
  buildMonthlyCron,
} from '../cron.util';
import {
  NodeConfigView,
  WeekDayOption,
  nodeTypeLabel,
  nodeDescription,
  triggerMode as triggerModeUtil,
  scheduleDisplayText,
  categoryEntries,
  labelCategoryName,
  labelCategoryColor,
} from '../node-config.util';

interface FlowNode {
  id: string;
  nodeType: NodeType;
  label: string;
  position: IPoint;
  config: string;
}

interface FlowEdge {
  id: string;
  outputId: string;
  inputId: string;
}

/**
 * Node config side-panel for the automation editor. Extracted verbatim from
 * {@code AutomationEditorComponent}; the parent passes its signals by reference
 * so every moved method body and template expression behaves identically.
 */
@Component({
  selector: 'app-node-config-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, VariableComboboxComponent, NodeInfoModalComponent],
  templateUrl: './node-config-panel.component.html',
})
export class NodeConfigPanelComponent {
  protected i18n = inject(I18nService);
  protected workspaceService = inject(WorkspaceService);
  private webhookEndpointService = inject(WebhookEndpointService);
  private automationService = inject(AutomationService);
  private knowledgeBaseService = inject(KnowledgeBaseService);
  private organizationService = inject(OrganizationService);
  private viewport = inject(ViewportService);
  private variableGraph = inject(VariableGraphService);

  // Parent signals passed by reference — read/write stays byte-for-byte identical.
  @Input({ required: true }) nodes!: WritableSignal<FlowNode[]>;
  @Input({ required: true }) edges!: WritableSignal<FlowEdge[]>;
  @Input({ required: true }) selectedNodeId!: WritableSignal<string | null>;
  @Input({ required: true }) categories!: Signal<Category[]>;
  @Input({ required: true }) parameterSets!: Signal<ParameterSet[]>;
  @Input({ required: true }) templates!: Signal<Template[]>;
  @Input({ required: true }) secrets!: Signal<Secret[]>;
  @Input({ required: true }) sendableAccounts!: Signal<EmailAccount[]>;
  @Input({ required: true }) automation!: WritableSignal<AutomationDetail | null>;

  @Output() close = new EventEmitter<void>();
  @Output() compose = new EventEmitter<{ nodeId: string; kind: 'email_action' | 'send_email' }>();
  @Output() error = new EventEmitter<string>();

  readonly cronPresets = CRON_PRESETS;

  /**
   * Whether the active-org role may edit automations AND the viewport allows editing. When false
   * (viewers, or any role on a phone where the editor is view-only), the panel body is made
   * {@code inert} so the node config is inspectable but not editable; close/help still work.
   */
  readonly canEdit = computed(() => this.organizationService.can('AUTOMATION_EDIT') && !this.viewport.isMobile());

  /** Whether the in-editor node help modal is open. */
  protected readonly infoOpen = signal(false);

  readonly weekDays: WeekDayOption[] = [
    { value: 1, labelKey: 'auto_schedule_mon' },
    { value: 2, labelKey: 'auto_schedule_tue' },
    { value: 3, labelKey: 'auto_schedule_wed' },
    { value: 4, labelKey: 'auto_schedule_thu' },
    { value: 5, labelKey: 'auto_schedule_fri' },
    { value: 6, labelKey: 'auto_schedule_sat' },
    { value: 0, labelKey: 'auto_schedule_sun' },
  ];

  readonly monthDays = Array.from({ length: 28 }, (_, i) => i + 1);

  // Inbound webhook trigger endpoint (loaded when a WEBHOOK trigger node is selected)
  webhookEndpoint = signal<WebhookEndpoint | null>(null);
  generatedSecret = signal<string | null>(null);

  // Integrations available for INTEGRATION_CALL nodes (loaded lazily on first use)
  integrations = signal<Automation[]>([]);
  private integrationsLoaded = false;

  // Knowledge bases available for VECTOR_SEARCH nodes (loaded lazily on first use)
  knowledgeBases = signal<KnowledgeBase[]>([]);
  private kbsLoaded = false;

  // Active org's members for the NOTIFY node's recipient picker (loaded lazily on first use)
  orgMembers = signal<OrgMember[]>([]);
  private orgMembersLoaded = false;

  // Filter check collapsible cards
  openCheckCards = signal<Set<number>>(new Set());

  // Webhook collapsible sections
  webhookOpenSections = signal<Set<string>>(new Set(['request']));

  varsGroupOpen: Record<string, boolean> = {};

  readonly getNodeColor = getNodeColor;
  readonly getNodeIcon = getNodeIcon;
  readonly v = v;

  selectedNode = computed(() => {
    const id = this.selectedNodeId();
    return id ? this.nodes().find(n => n.id === id) ?? null : null;
  });

  selectedNodeConfig = computed(() => {
    const node = this.selectedNode();
    if (!node) return null;
    return this.parseConfig<any>(node, {});
  });

  constructor() {
    // Load the inbound webhook endpoint whenever a WEBHOOK trigger node is selected.
    effect(() => {
      const node = this.selectedNode();
      let endpointId: string | undefined;
      if (node && node.nodeType === 'TRIGGER') {
        const cfg = this.parseConfig<TriggerNodeConfig>(node, {} as TriggerNodeConfig);
        if (cfg.triggerMode === 'WEBHOOK') endpointId = cfg.webhookEndpointId;
      }
      this.loadWebhookEndpoint(endpointId);
      // Lazily load the integration catalogue the first time a call node is opened.
      if (node && node.nodeType === 'INTEGRATION_CALL') this.ensureIntegrationsLoaded();
      // Lazily load knowledge bases the first time a vector-search node is opened.
      if (node && node.nodeType === 'VECTOR_SEARCH') this.ensureKbsLoaded();
      // Lazily load the org's members the first time a notify node is opened (recipient picker).
      if (node && node.nodeType === 'NOTIFY') this.ensureOrgMembersLoaded();
    });

    // Backfill a VECTOR_SEARCH node's cached match-field names once KBs + parameter sets are loaded
    // (e.g. a node created before caching, or opened before the KB list finished loading), so the
    // variable graph can expose vectorsearch_<id>.match.<field> downstream.
    effect(() => {
      const node = this.selectedNode();
      if (!node || node.nodeType !== 'VECTOR_SEARCH') return;
      const kbs = this.knowledgeBases();        // tracked → re-runs when the KB list loads
      const psets = this.parameterSets();
      const cfg = this.parseConfig<Record<string, unknown>>(node, {});
      const kbId = cfg['knowledgeBaseId'] as string | undefined;
      if (!kbId || Array.isArray(cfg['matchFields'])) return;
      const kb = kbs.find(k => k.id === kbId);
      const ps = kb ? psets.find(p => p.id === kb.parameterSetId) : null;
      if (ps) {
        cfg['matchFields'] = ps.parameters.map(p => p.name);
        if (!cfg['knowledgeBaseName']) cfg['knowledgeBaseName'] = kb?.name ?? '';
        this.updateNodeConfig(node.id, cfg);
      }
    });
  }

  // ── Config helpers ─────────────────────────
  updateNodeConfig(nodeId: string, config: Record<string, unknown> | TriggerNodeConfig | FilterNodeConfig | ExtractNodeConfig | CategorizeNodeConfig | DelayNodeConfig | LabelNodeConfig | EmailActionNodeConfig | RemoveLabelNodeConfig | WebhookNodeConfig): void {
    this.nodes.update(list =>
      list.map(n => n.id === nodeId ? { ...n, config: JSON.stringify(config) } : n)
    );
  }

  updateNodeLabel(nodeId: string, label: string): void {
    this.nodes.update(list =>
      list.map(n => n.id === nodeId ? { ...n, label } : n)
    );
  }

  /** Loads the org's knowledge bases once, for the VECTOR_SEARCH node's KB picker. */
  private ensureKbsLoaded(): void {
    if (this.kbsLoaded) return;
    this.kbsLoaded = true;
    this.knowledgeBaseService.list().subscribe({
      next: kbs => this.knowledgeBases.set(kbs),
      error: () => { this.kbsLoaded = false; },
    });
  }

  /** Writes a single field of the selected node's config (used by the VECTOR_SEARCH form). */
  setVsField(nodeId: string, field: string, value: unknown): void {
    const config = this.parseConfigById<Record<string, unknown>>(nodeId, {});
    config[field] = value;
    this.updateNodeConfig(nodeId, config);
  }

  /**
   * Selects the KB for a VECTOR_SEARCH node and snapshots its parameter-set field names into
   * {@code matchFields} so the variable graph can expose {@code vectorsearch_<id>.match.<field>}
   * downstream (mirrors INTEGRATION_CALL caching outputFields). Recomputed on every KB change.
   */
  selectVsKb(nodeId: string, kbId: string): void {
    const config = this.parseConfigById<Record<string, unknown>>(nodeId, {});
    config['knowledgeBaseId'] = kbId;
    const kb = this.knowledgeBases().find(k => k.id === kbId);
    const ps = kb ? this.parameterSets().find(p => p.id === kb.parameterSetId) : null;
    config['matchFields'] = ps ? ps.parameters.map(p => p.name) : [];
    config['knowledgeBaseName'] = kb?.name ?? '';
    this.updateNodeConfig(nodeId, config);
  }

  /** Loads the active org's members once, for the NOTIFY node's recipient (USER) picker. */
  private ensureOrgMembersLoaded(): void {
    if (this.orgMembersLoaded) return;
    this.orgMembersLoaded = true;
    this.organizationService.current().subscribe({
      next: detail => this.orgMembers.set(detail.members),
      error: () => { this.orgMembersLoaded = false; },
    });
  }

  /**
   * Writes a single field of a NOTIFY node's config. When the recipient type leaves USER, the
   * cached {@code recipientUserId} is cleared so the saved config stays consistent with the backend
   * contract (recipientUserId is only meaningful for recipientType === 'USER').
   */
  setNotifyField(nodeId: string, field: string, value: unknown): void {
    const config = this.parseConfigById<Record<string, unknown>>(nodeId, {});
    config[field] = value;
    if (field === 'recipientType' && value !== 'USER') delete config['recipientUserId'];
    this.updateNodeConfig(nodeId, config);
  }

  /** Parses a node's config JSON, returning {@link fallback} when the node is absent or the JSON is invalid. */
  private parseConfig<T>(node: FlowNode | undefined, fallback: T): T {
    if (!node) return fallback;
    try { return JSON.parse(node.config || '{}') as T; } catch { return fallback; }
  }

  /** Finds a node by id and parses its config JSON, returning {@link fallback} when absent or invalid. */
  private parseConfigById<T>(nodeId: string, fallback: T): T {
    return this.parseConfig(this.nodes().find(n => n.id === nodeId), fallback);
  }

  // ── Shared read-only getters (delegate to node-config.util) ──
  getNodeTypeLabel(type: NodeType): string {
    return nodeTypeLabel(this.i18n, type);
  }

  getNodeDescription(type: NodeType): string {
    return nodeDescription(this.i18n, type);
  }

  getTriggerMode(node: NodeConfigView): TriggerMode {
    return triggerModeUtil(node);
  }

  getScheduleDisplayText(node: NodeConfigView): string {
    return scheduleDisplayText(this.i18n, this.weekDays, node);
  }

  getCategoryEntries(node: NodeConfigView): { categoryId: string; label: string; color: string }[] {
    return categoryEntries(this.categories(), node);
  }

  getLabelCategoryName(node: NodeConfigView): string {
    return labelCategoryName(this.categories(), node);
  }

  getLabelCategoryColor(node: NodeConfigView): string {
    return labelCategoryColor(this.categories(), node);
  }

  // ── Supervised execution mode (action nodes) ──────────────────
  /** Action node types that support a supervised execution mode (mirrors backend NodeType.ACTION_TYPES). */
  private readonly actionNodeTypes: NodeType[] = ['EMAIL_ACTION', 'SEND_EMAIL', 'WEBHOOK', 'LABEL', 'REMOVE_LABEL', 'INTEGRATION_CALL'];

  isActionNode(type: NodeType): boolean {
    return this.actionNodeTypes.includes(type);
  }

  getExecutionMode(node: NodeConfigView): 'AUTO' | 'REVIEW' | 'OFF' {
    try {
      const m = (JSON.parse(node.config || '{}') as { executionMode?: string }).executionMode;
      return m === 'REVIEW' || m === 'OFF' ? m : 'AUTO';
    } catch {
      return 'AUTO';
    }
  }

  setExecutionMode(nodeId: string, mode: 'AUTO' | 'REVIEW' | 'OFF'): void {
    const config = this.parseConfigById<Record<string, unknown>>(nodeId, {});
    config['executionMode'] = mode;
    this.updateNodeConfig(nodeId, config);
  }

  /** #3b: confidence threshold (0-100) above which a REVIEW action auto-runs; 0 = always review. */
  getReviewThreshold(node: NodeConfigView): number {
    try {
      const t = (JSON.parse(node.config || '{}') as { reviewThreshold?: number }).reviewThreshold;
      return typeof t === 'number' ? t : 0;
    } catch {
      return 0;
    }
  }

  setReviewThreshold(nodeId: string, value: number): void {
    const config = this.parseConfigById<Record<string, unknown>>(nodeId, {});
    config['reviewThreshold'] = value;
    this.updateNodeConfig(nodeId, config);
  }

  // ── Filter condition helpers (multi-check format) ────────────────
  addFilterCheck(nodeId: string): void {
    const config = this.getFilterConfig(nodeId);
    if (!config) return;
    if (!config.checks) config.checks = [];
    const idx = config.checks.length + 1;
    config.checks.push({ label: `Check ${idx}`, groups: [{ conditions: [{ field: 'email.from', operator: 'CONTAINS', value: '' }] }] });
    this.updateNodeConfig(nodeId, config);
  }

  removeFilterCheck(nodeId: string, checkIndex: number): void {
    const config = this.getFilterConfig(nodeId);
    if (!config || !config.checks) return;
    config.checks.splice(checkIndex, 1);
    this.updateNodeConfig(nodeId, config);
  }

  updateFilterCheckLabel(nodeId: string, checkIndex: number, label: string): void {
    const config = this.getFilterConfig(nodeId);
    if (!config || !config.checks?.[checkIndex]) return;
    config.checks[checkIndex].label = label;
    this.updateNodeConfig(nodeId, config);
  }

  updateFilterField(nodeId: string, checkIndex: number, gi: number, ci: number, field: string): void {
    const config = this.getFilterConfig(nodeId);
    if (!config || !config.checks?.[checkIndex]) return;
    config.checks[checkIndex].groups[gi].conditions[ci].field = field;
    this.updateNodeConfig(nodeId, config);
  }

  updateFilterOperator(nodeId: string, checkIndex: number, gi: number, ci: number, operator: string): void {
    const config = this.getFilterConfig(nodeId);
    if (!config || !config.checks?.[checkIndex]) return;
    config.checks[checkIndex].groups[gi].conditions[ci].operator = operator;
    this.updateNodeConfig(nodeId, config);
  }

  updateFilterValue(nodeId: string, checkIndex: number, gi: number, ci: number, value: string): void {
    const config = this.getFilterConfig(nodeId);
    if (!config || !config.checks?.[checkIndex]) return;
    config.checks[checkIndex].groups[gi].conditions[ci].value = value;
    this.updateNodeConfig(nodeId, config);
  }

  addFilterCondition(nodeId: string, checkIndex: number, gi: number): void {
    const config = this.getFilterConfig(nodeId);
    if (!config || !config.checks?.[checkIndex]) return;
    config.checks[checkIndex].groups[gi].conditions.push({ field: 'email.from', operator: 'CONTAINS', value: '' });
    this.updateNodeConfig(nodeId, config);
  }

  removeFilterCondition(nodeId: string, checkIndex: number, gi: number, ci: number): void {
    const config = this.getFilterConfig(nodeId);
    if (!config || !config.checks?.[checkIndex] || config.checks[checkIndex].groups[gi].conditions.length <= 1) return;
    config.checks[checkIndex].groups[gi].conditions.splice(ci, 1);
    this.updateNodeConfig(nodeId, config);
  }

  addFilterGroup(nodeId: string, checkIndex: number): void {
    const config = this.getFilterConfig(nodeId);
    if (!config || !config.checks?.[checkIndex]) return;
    config.checks[checkIndex].groups.push({ conditions: [{ field: 'email.from', operator: 'CONTAINS', value: '' }] });
    this.updateNodeConfig(nodeId, config);
  }

  removeFilterGroup(nodeId: string, checkIndex: number, gi: number): void {
    const config = this.getFilterConfig(nodeId);
    if (!config || !config.checks?.[checkIndex] || config.checks[checkIndex].groups.length <= 1) return;
    config.checks[checkIndex].groups.splice(gi, 1);
    this.updateNodeConfig(nodeId, config);
  }

  /** Returns available variable fields for filter dropdowns based on upstream nodes. */
  getFilterVariableFields(nodeId: string): { key: string; label: string }[] {
    const fields: { key: string; label: string }[] = [];
    const upstream = this.variableGraph.getUpstreamNodes(nodeId, this.nodes(), this.edges());

    // Email fields — only if upstream TRIGGER(EMAIL) exists
    if (this.variableGraph.hasUpstreamEmailTrigger(upstream)) {
      fields.push(
        { key: 'email.from', label: this.i18n.t('filter_field_from_address') },
        { key: 'email.fromName', label: this.i18n.t('filter_field_from_name') },
        { key: 'email.to', label: this.i18n.t('filter_field_to_address') },
        { key: 'email.cc', label: this.i18n.t('filter_field_cc_address') },
        { key: 'email.subject', label: this.i18n.t('filter_field_subject') },
        { key: 'email.body', label: this.i18n.t('filter_field_body') },
        { key: 'email.hasAttachments', label: this.i18n.t('filter_field_has_attachments') },
        { key: 'email.folder', label: this.i18n.t('filter_field_folder') },
      );
    }

    // Add upstream extraction variables
    for (const n of upstream) {
      if (n.nodeType === 'EXTRACT') {
        try {
          const cfg: ExtractNodeConfig = JSON.parse(n.config || '{}');
          (cfg.extractions || []).forEach((entry, i) => {
            const ps = this.parameterSets().find(p => p.id === entry.parameterSetId);
            if (ps) {
              ps.parameters.forEach(param => {
                fields.push({ key: `extraction_${i}.${param.name}`, label: `${ps.name}: ${param.name}` });
              });
            }
          });
        } catch { /* ignore */ }
      } else if (n.nodeType === 'CATEGORIZE') {
        fields.push({ key: 'category.name', label: this.i18n.t('auto_categorize_category_name') });
        fields.push({ key: 'category.id', label: this.i18n.t('auto_categorize_category_id') });
      }
    }

    return fields;
  }

  /** Returns operators valid for a given variable field key. */
  getOperatorsForVariableField(fieldKey: string): string[] {
    if (fieldKey.startsWith('email.hasAttachments')) {
      return ['IS_TRUE', 'IS_FALSE'];
    }
    if (fieldKey.startsWith('extraction_')) {
      return ['EQUALS', 'NOT_EQUALS', 'CONTAINS', 'NOT_CONTAINS', 'GREATER_THAN', 'LESS_THAN', 'IS_TRUE', 'IS_FALSE'];
    }
    return ['EQUALS', 'NOT_EQUALS', 'CONTAINS', 'NOT_CONTAINS', 'STARTS_WITH', 'ENDS_WITH'];
  }

  // ── Email action config helpers ──────────────────
  getEmailActionMode(node: FlowNode): EmailActionMode {
    return this.parseConfig<EmailActionNodeConfig>(node, { actionMode: 'REPLY' }).actionMode || 'REPLY';
  }

  setEmailActionMode(nodeId: string, mode: EmailActionMode): void {
    const config = this.parseConfigById<EmailActionNodeConfig>(nodeId, { actionMode: 'REPLY' });
    config.actionMode = mode;
    if (mode === 'REPLY' && config.templateId === undefined) { config.templateId = ''; }
    if (mode === 'FORWARD' && !config.toAddress) { config.toAddress = ''; }
    if (mode === 'MOVE_FOLDER' && !config.folder) { config.folder = ''; }
    this.updateNodeConfig(nodeId, config);
  }

  updateEmailActionField(nodeId: string, field: string, value: string): void {
    const config = this.parseConfigById<EmailActionNodeConfig>(nodeId, { actionMode: 'REPLY' });
    (config as unknown as Record<string, unknown>)[field] = value || undefined;
    this.updateNodeConfig(nodeId, config);
  }

  // ── Content source (Vorlage / Manuell) + compose modal ──────────
  getContentSource(node: FlowNode): EmailContentSource {
    const src = this.parseConfig<Record<string, unknown>>(node, {})['contentSource'];
    return src === 'MANUAL' ? 'MANUAL' : 'VORLAGE';
  }

  setContentSource(nodeId: string, source: EmailContentSource): void {
    const config = this.parseConfigById<Record<string, unknown>>(nodeId, {});
    config['contentSource'] = source;
    if (source === 'MANUAL') { config['templateId'] = ''; }
    this.updateNodeConfig(nodeId, config);
  }

  getManualSubject(node: FlowNode): string {
    return this.parseConfig<Record<string, unknown>>(node, {})['subject'] as string || '';
  }

  getManualBody(node: FlowNode): string {
    return this.parseConfig<Record<string, unknown>>(node, {})['body'] as string || '';
  }

  // ── Webhook config helpers ──────────────────
  private getWebhookConfig(nodeId: string): WebhookNodeConfig {
    return this.parseConfigById<WebhookNodeConfig>(nodeId, { url: '', method: 'POST', authType: 'NONE' });
  }

  updateWebhookUrl(nodeId: string, url: string): void {
    const config = this.getWebhookConfig(nodeId);
    config.url = url;
    this.updateNodeConfig(nodeId, config);
  }

  updateWebhookMethod(nodeId: string, method: string): void {
    const config = this.getWebhookConfig(nodeId);
    config.method = method as WebhookNodeConfig['method'];
    this.updateNodeConfig(nodeId, config);
  }

  updateWebhookAuthType(nodeId: string, authType: string): void {
    const config = this.getWebhookConfig(nodeId);
    config.authType = authType as WebhookNodeConfig['authType'];
    this.updateNodeConfig(nodeId, config);
  }

  updateWebhookBody(nodeId: string, body: string): void {
    const config = this.getWebhookConfig(nodeId);
    config.body = body;
    this.updateNodeConfig(nodeId, config);
  }

  updateWebhookField(nodeId: string, field: string, value: string | number): void {
    const config = this.getWebhookConfig(nodeId);
    (config as unknown as Record<string, unknown>)[field] = value;
    this.updateNodeConfig(nodeId, config);
  }

  addWebhookHeader(nodeId: string): void {
    const config = this.getWebhookConfig(nodeId);
    if (!config.headers) config.headers = [];
    config.headers.push({ key: '', value: '' });
    this.updateNodeConfig(nodeId, config);
  }

  updateWebhookHeader(nodeId: string, index: number, field: 'key' | 'value', val: string): void {
    const config = this.getWebhookConfig(nodeId);
    if (!config.headers || !config.headers[index]) return;
    config.headers[index][field] = val;
    this.updateNodeConfig(nodeId, config);
  }

  removeWebhookHeader(nodeId: string, index: number): void {
    const config = this.getWebhookConfig(nodeId);
    if (!config.headers) return;
    config.headers.splice(index, 1);
    this.updateNodeConfig(nodeId, config);
  }

  // ── Remove label config helpers ──────────────────
  updateRemoveLabelCategory(nodeId: string, categoryId: string): void {
    this.updateNodeConfig(nodeId, { categoryId: categoryId || undefined });
  }

  // ── SEND_EMAIL config helpers ──────────────────
  updateSendEmailField(nodeId: string, field: string, value: string): void {
    const config = this.parseConfigById<Record<string, unknown>>(nodeId, {});
    config[field] = value;
    this.updateNodeConfig(nodeId, config);
  }

  // ── Extract helpers ─────────────────────────
  addExtraction(nodeId: string, parameterSetId: string): void {
    if (!parameterSetId) return;
    const ps = this.parameterSets().find(p => p.id === parameterSetId);
    if (!ps) return;
    const config = this.parseConfigById<ExtractNodeConfig>(nodeId, { extractions: [] });
    if (!config.extractions) config.extractions = [];
    // Prevent duplicate parameter sets
    if (config.extractions.some(e => e.parameterSetId === parameterSetId)) return;
    config.extractions.push({ parameterSetId, label: ps.name });
    this.updateNodeConfig(nodeId, config);
  }

  removeExtraction(nodeId: string, index: number): void {
    const node = this.nodes().find(n => n.id === nodeId);
    if (!node) return;
    let config: ExtractNodeConfig;
    try { config = JSON.parse(node.config || '{}'); } catch { return; }
    if (!config.extractions) return;
    config.extractions.splice(index, 1);
    this.updateNodeConfig(nodeId, config);
  }

  // ── Categorize helpers ─────────────────────────
  addCategory(nodeId: string, categoryId: string): void {
    if (!categoryId) return;
    const config = this.parseConfigById<CategorizeNodeConfig>(nodeId, { categoryIds: [], threshold: 70 });
    if (!config.categoryIds) config.categoryIds = [];
    if (config.categoryIds.includes(categoryId)) return;
    config.categoryIds.push(categoryId);
    this.updateNodeConfig(nodeId, config);
  }

  removeCategory(nodeId: string, index: number): void {
    const node = this.nodes().find(n => n.id === nodeId);
    if (!node) return;
    let config: CategorizeNodeConfig;
    try { config = JSON.parse(node.config || '{}'); } catch { return; }
    if (!config.categoryIds) return;
    config.categoryIds.splice(index, 1);
    this.updateNodeConfig(nodeId, config);
  }

  updateCategorizeThreshold(nodeId: string, threshold: number): void {
    const config = this.parseConfigById<CategorizeNodeConfig>(nodeId, { categoryIds: [], threshold: 70 });
    config.threshold = threshold;
    this.updateNodeConfig(nodeId, config);
  }

  getCategorizeThreshold(node: FlowNode): number {
    return this.parseConfig<CategorizeNodeConfig>(node, {} as CategorizeNodeConfig).threshold ?? 70;
  }

  // ── FORWARD attachments (none / all original / current FOREACH item) ──────
  /** The configured FORWARD attachment source key (back-compat: legacy includeAttachments=true → all). */
  getForwardAttachmentSource(node: FlowNode): string {
    const cfg = this.parseConfig<Record<string, unknown>>(node, {});
    if ('attachmentSource' in cfg) return (cfg['attachmentSource'] as string) || '';
    return cfg['includeAttachments'] === true ? 'email.attachments' : '';
  }

  setForwardAttachmentSource(nodeId: string, value: string): void {
    const config = this.parseConfigById<Record<string, unknown>>(nodeId, {});
    config['attachmentSource'] = value;
    delete config['includeAttachments']; // superseded by attachmentSource
    this.updateNodeConfig(nodeId, config);
  }

  /** Attachment sources a FORWARD can use: all of the email's attachments, or a FOREACH item (the current one). */
  getAttachmentSourceOptions(nodeId: string): { key: string; label: string }[] {
    const upstream = this.variableGraph.getUpstreamNodes(nodeId, this.nodes(), this.edges());
    const opts: { key: string; label: string }[] = [];
    if (this.variableGraph.hasUpstreamEmailTrigger(upstream)) {
      opts.push({ key: 'email.attachments', label: this.i18n.t('auto_var_email_attachments') });
    }
    for (const n of upstream) {
      if (n.nodeType !== 'FOREACH') continue;
      try {
        const cfg = JSON.parse(n.config || '{}') as { sourceVariable?: string; itemAlias?: string };
        if (cfg.sourceVariable === 'email.attachments') {
          const alias = cfg.itemAlias || 'item';
          opts.push({ key: alias, label: `${this.i18n.t('auto_var_attachment_current')} (${alias})` });
        }
      } catch { /* ignore */ }
    }
    return opts;
  }

  // ── FOREACH config (source list variable + item alias) ──────
  getForeachSource(node: FlowNode): string {
    return (this.parseConfig<Record<string, unknown>>(node, {})['sourceVariable'] as string) || '';
  }

  getForeachAlias(node: FlowNode): string {
    return (this.parseConfig<Record<string, unknown>>(node, {})['itemAlias'] as string) || 'item';
  }

  setForeachField(nodeId: string, field: 'sourceVariable' | 'itemAlias', value: string): void {
    const config = this.parseConfigById<Record<string, unknown>>(nodeId, {});
    config[field] = value;
    this.updateNodeConfig(nodeId, config);
  }

  // ── Source variable helpers (EXTRACT & CATEGORIZE) ──────
  getSourceVariables(node: FlowNode): string[] {
    return this.parseConfig<Record<string, unknown>>(node, {})['sourceVariables'] as string[] || [];
  }

  addSourceVariable(nodeId: string, variable: string): void {
    if (!variable) return;
    const config = this.parseConfigById<any>(nodeId, {});
    if (!config.sourceVariables) config.sourceVariables = [];
    if (config.sourceVariables.includes(variable)) return;
    config.sourceVariables.push(variable);
    this.updateNodeConfig(nodeId, config);
  }

  removeSourceVariable(nodeId: string, index: number): void {
    const node = this.nodes().find(n => n.id === nodeId);
    if (!node) return;
    let config: any;
    try { config = JSON.parse(node.config || '{}'); } catch { return; }
    if (!config.sourceVariables) return;
    config.sourceVariables.splice(index, 1);
    this.updateNodeConfig(nodeId, config);
  }

  getSourceVariableOptions(nodeId: string): { key: string; label: string }[] {
    const groups = this.getAvailableVariables(nodeId);
    const options: { key: string; label: string }[] = [];
    for (const group of groups) {
      for (const v of group.variables) {
        options.push({ key: v.key, label: v.label });
      }
    }
    return options;
  }

  getSourceVariableLabel(nodeId: string, key: string): string {
    const groups = this.getAvailableVariables(nodeId);
    for (const group of groups) {
      for (const v of group.variables) {
        if (v.key === key) return v.label;
      }
    }
    return key;
  }

  /** True when a selected source variable no longer resolves to an available variable (dangling reference). */
  isSourceVariableDangling(nodeId: string, key: string): boolean {
    if (!key) return false;
    const groups = this.getAvailableVariables(nodeId);
    return !groups.some(g => g.variables.some(v => v.key === key));
  }

  // ── Trigger helpers ──────────────────────────
  toggleTriggerAccount(nodeId: string, accountId: string): void {
    const config = this.parseConfigById<TriggerNodeConfig>(nodeId, { triggerMode: 'EMAIL', accountIds: [] });
    if (!config.accountIds) config.accountIds = [];
    const idx = config.accountIds.indexOf(accountId);
    if (idx >= 0) {
      config.accountIds.splice(idx, 1);
    } else {
      config.accountIds.push(accountId);
    }
    this.updateNodeConfig(nodeId, config);
  }

  // ── Trigger mode helpers ──────────────────────
  setTriggerMode(nodeId: string, mode: TriggerMode): void {
    const config = this.parseConfigById<TriggerNodeConfig>(nodeId, { triggerMode: 'EMAIL' });
    config.triggerMode = mode;
    if (mode === 'EMAIL' && !config.accountIds) config.accountIds = [];
    if (mode === 'CRON' && !config.scheduleType) {
      config.scheduleType = 'INTERVAL';
      config.intervalMinutes = 60;
      config.preset = 'hourly';
    }
    if (mode === 'WEBHOOK' && !config.accountIds) config.accountIds = [];
    if (mode === 'MANUAL' && !config.accountIds) config.accountIds = [];
    this.updateNodeConfig(nodeId, config);
  }

  updateTriggerWebhookField(nodeId: string, field: string, value: string | number): void {
    const config = this.parseConfigById<TriggerNodeConfig>(nodeId, { triggerMode: 'WEBHOOK' });
    (config as unknown as Record<string, unknown>)[field] = value;
    this.updateNodeConfig(nodeId, config);
  }

  // ── Inbound webhook endpoint ─────────────────
  private loadWebhookEndpoint(endpointId: string | undefined): void {
    if (!endpointId) {
      this.webhookEndpoint.set(null);
      this.generatedSecret.set(null);
      return;
    }
    if (this.webhookEndpoint()?.id === endpointId) return;
    this.generatedSecret.set(null);
    this.webhookEndpointService.get(endpointId).subscribe({
      next: (ep) => this.webhookEndpoint.set(ep),
      error: () => this.webhookEndpoint.set(null),
    });
  }

  copyWebhookUrl(url: string): void {
    navigator.clipboard?.writeText(url);
  }

  regenerateWebhookToken(): void {
    const ep = this.webhookEndpoint();
    if (!ep) return;
    this.webhookEndpointService.regenerateToken(ep.id).subscribe({
      next: (updated) => {
        this.webhookEndpoint.set(updated);
        this.syncWebhookTokenIntoNode(updated);
      },
      error: (err) => this.error.emit(humanizeError(err, 'Regenerate failed')),
    });
  }

  onWebhookAuthModeChange(mode: string): void {
    const ep = this.webhookEndpoint();
    if (!ep) return;
    this.webhookEndpointService.setAuth(ep.id, {
      authMode: mode as WebhookAuthMode,
      authHeaderName: ep.authHeaderName,
    }).subscribe({
      next: (updated) => this.webhookEndpoint.set(updated),
      error: (err) => this.error.emit(humanizeError(err, 'Auth update failed')),
    });
  }

  onWebhookHeaderNameChange(headerName: string): void {
    const ep = this.webhookEndpoint();
    if (!ep) return;
    this.webhookEndpointService.setAuth(ep.id, {
      authMode: 'API_KEY',
      authHeaderName: headerName,
    }).subscribe({
      next: (updated) => this.webhookEndpoint.set(updated),
      error: (err) => this.error.emit(humanizeError(err, 'Auth update failed')),
    });
  }

  generateWebhookSecret(): void {
    const ep = this.webhookEndpoint();
    if (!ep) return;
    this.webhookEndpointService.generateSecret(ep.id).subscribe({
      next: (res) => {
        this.generatedSecret.set(res.secret);
        this.webhookEndpoint.set({ ...ep, hasSecret: true });
      },
      error: (err) => this.error.emit(humanizeError(err, 'Secret generation failed')),
    });
  }

  /** Writes a rotated token back into the selected node's config so the URL stays in sync without a full save. */
  private syncWebhookTokenIntoNode(ep: WebhookEndpoint): void {
    const node = this.selectedNode();
    if (!node) return;
    const config = this.parseConfig<TriggerNodeConfig>(node, {} as TriggerNodeConfig);
    config.webhookEndpointId = ep.id;
    config.webhookToken = ep.token;
    this.updateNodeConfig(node.id, config);
  }

  // ── Schedule helpers ─────────────────────────
  getScheduleTime(node: FlowNode): string {
    return parseCronTime(this.parseConfig<TriggerNodeConfig>(node, {} as TriggerNodeConfig).cronExpression);
  }

  getScheduleDay(node: FlowNode): number {
    return parseCronDay(this.parseConfig<TriggerNodeConfig>(node, {} as TriggerNodeConfig).cronExpression);
  }

  updateScheduleTime(nodeId: string, time: string): void {
    const config = this.parseConfigById<TriggerNodeConfig>(nodeId, { triggerMode: 'CRON', scheduleType: 'CRON', preset: 'daily' });
    const [h, m] = parseTimeParts(time);
    const day = parseCronDay(config.cronExpression);
    config.cronExpression = config.preset === 'weekly'
      ? buildWeeklyCron(h, m, day)
      : buildDailyCron(h, m);
    this.updateNodeConfig(nodeId, config);
  }

  updateScheduleDay(nodeId: string, day: number): void {
    const config = this.parseConfigById<TriggerNodeConfig>(nodeId, { triggerMode: 'CRON', scheduleType: 'CRON', preset: 'weekly' });
    const [h, m] = parseTimeParts(parseCronTime(config.cronExpression));
    config.cronExpression = buildWeeklyCron(h, m, day);
    this.updateNodeConfig(nodeId, config);
  }

  getScheduleDayOfMonth(node: FlowNode): number {
    return parseCronDayOfMonth(this.parseConfig<TriggerNodeConfig>(node, {} as TriggerNodeConfig).cronExpression);
  }

  updateScheduleDayOfMonth(nodeId: string, dayOfMonth: number): void {
    const config = this.parseConfigById<TriggerNodeConfig>(nodeId, { triggerMode: 'CRON', scheduleType: 'CRON', preset: 'monthly' });
    const [h, m] = parseTimeParts(parseCronTime(config.cronExpression));
    config.cronExpression = buildMonthlyCron(h, m, dayOfMonth);
    this.updateNodeConfig(nodeId, config);
  }

  updateScheduleTimeMonthly(nodeId: string, time: string): void {
    const config = this.parseConfigById<TriggerNodeConfig>(nodeId, { triggerMode: 'CRON', scheduleType: 'CRON', preset: 'monthly' });
    const [h, m] = parseTimeParts(time);
    const dom = parseCronDayOfMonth(config.cronExpression);
    config.cronExpression = buildMonthlyCron(h, m, dom);
    this.updateNodeConfig(nodeId, config);
  }

  applySchedulePreset(nodeId: string, presetKey: string): void {
    const preset = CRON_PRESETS.find(p => p.key === presetKey);
    if (!preset) return;
    const config: TriggerNodeConfig = {
      triggerMode: 'CRON',
      scheduleType: preset.scheduleType,
      preset: presetKey,
      intervalMinutes: preset.intervalMinutes,
      cronExpression: preset.cronExpression,
    };
    this.updateNodeConfig(nodeId, config);
  }

  updateScheduleCron(nodeId: string, cronExpression: string): void {
    const config = this.parseConfigById<TriggerNodeConfig>(nodeId, { triggerMode: 'CRON', scheduleType: 'CRON', preset: 'custom' });
    config.cronExpression = cronExpression;
    this.updateNodeConfig(nodeId, config);
  }

  // ── Delay helpers ──────────────────────────────
  updateDelayMinutes(nodeId: string, delayMinutes: number): void {
    this.updateNodeConfig(nodeId, { delayMinutes });
  }

  // ── Label helpers ──────────────────────────────
  updateLabelCategory(nodeId: string, categoryId: string): void {
    this.updateNodeConfig(nodeId, { categoryId: categoryId || undefined });
  }

  private getFilterConfig(nodeId: string): FilterNodeConfig | null {
    const node = this.nodes().find(n => n.id === nodeId);
    if (!node) return null;
    try {
      return JSON.parse(node.config || '{}');
    } catch {
      return { checks: [{ label: 'Check 1', groups: [{ conditions: [{ field: 'email.from', operator: 'CONTAINS', value: '' }] }] }] };
    }
  }

  // ── Retry config helpers ──────────────────────────────
  toggleRetryCondition(nodeId: string, condition: string): void {
    const config = this.getWebhookConfig(nodeId);
    if (!config.retryOn) config.retryOn = [];
    const idx = config.retryOn.indexOf(condition);
    if (idx >= 0) {
      config.retryOn.splice(idx, 1);
    } else {
      config.retryOn.push(condition);
    }
    this.updateNodeConfig(nodeId, config);
  }

  updateWebhookRetryCount(nodeId: string, count: number): void {
    const config = this.getWebhookConfig(nodeId);
    config.retryCount = Math.max(0, Math.min(3, count));
    this.updateNodeConfig(nodeId, config);
  }

  updateWebhookRetryDelay(nodeId: string, delay: number): void {
    const config = this.getWebhookConfig(nodeId);
    config.retryDelayMs = Math.max(1000, Math.min(30000, delay));
    this.updateNodeConfig(nodeId, config);
  }

  updateWebhookSecret(nodeId: string, secretId: string): void {
    const config = this.getWebhookConfig(nodeId);
    config.authSecretId = secretId || undefined;
    this.updateNodeConfig(nodeId, config);
  }

  // ── Response schema helpers ──────────────────────────
  addResponseSchema(nodeId: string): void {
    const config = this.getWebhookConfig(nodeId);
    if (!config.responseSchemas) config.responseSchemas = [];
    // Insert before the pinned "unmatched" entry so it stays last and existing resp_<i> indices hold.
    const newSchema: WebhookResponseSchema = { name: '', condition: '2xx', parameterSetId: '' };
    const unmatchedIdx = config.responseSchemas.findIndex(s => this.isUnmatchedCondition(s.condition));
    if (unmatchedIdx >= 0) {
      config.responseSchemas.splice(unmatchedIdx, 0, newSchema);
    } else {
      config.responseSchemas.push(newSchema);
    }
    this.updateNodeConfig(nodeId, config);
  }

  updateResponseSchemaCondition(nodeId: string, index: number, condition: string): void {
    const config = this.getWebhookConfig(nodeId);
    if (!config.responseSchemas?.[index]) return;
    config.responseSchemas[index].condition = condition;
    this.updateNodeConfig(nodeId, config);
  }

  updateResponseSchemaParameterSet(nodeId: string, index: number, psId: string): void {
    const config = this.getWebhookConfig(nodeId);
    if (!config.responseSchemas?.[index]) return;
    config.responseSchemas[index].parameterSetId = psId;
    this.updateNodeConfig(nodeId, config);
  }

  updateResponseSchemaName(nodeId: string, index: number, name: string): void {
    const config = this.getWebhookConfig(nodeId);
    if (!config.responseSchemas?.[index]) return;
    config.responseSchemas[index].name = name;
    this.updateNodeConfig(nodeId, config);
  }

  removeResponseSchema(nodeId: string, index: number): void {
    const config = this.getWebhookConfig(nodeId);
    if (!config.responseSchemas) return;
    config.responseSchemas.splice(index, 1);
    this.updateNodeConfig(nodeId, config);
  }

  // ── Unmatched (pinned catch-all) branch helpers ──────
  /** True for the pinned catch-all schema (rendered separately, never as an editable branch row). */
  isUnmatchedCondition(condition: string | undefined): boolean {
    return (condition || '').trim().toLowerCase() === 'unmatched';
  }

  /** The parameterSetId of the selected webhook's pinned "unmatched" branch (or '' if none/unset). */
  unmatchedParameterSetId(): string {
    const schemas = (this.selectedNodeConfig()?.responseSchemas || []) as WebhookResponseSchema[];
    return schemas.find(s => this.isUnmatchedCondition(s.condition))?.parameterSetId || '';
  }

  /** Sets the parameter set on the pinned "unmatched" branch, creating the entry if it doesn't exist. */
  updateUnmatchedParameterSet(nodeId: string, psId: string): void {
    const config = this.getWebhookConfig(nodeId);
    if (!config.responseSchemas) config.responseSchemas = [];
    let entry = config.responseSchemas.find(s => this.isUnmatchedCondition(s.condition));
    if (!entry) {
      entry = { name: 'Unmatched', condition: 'unmatched', parameterSetId: '' };
      config.responseSchemas.push(entry);
    }
    entry.parameterSetId = psId;
    this.updateNodeConfig(nodeId, config);
  }

  // ── Collapsible UI state ───────────────────────────────
  toggleCheckCard(index: number): void {
    this.openCheckCards.update(s => {
      const copy = new Set(s);
      copy.has(index) ? copy.delete(index) : copy.add(index);
      return copy;
    });
  }

  isCheckCardOpen(index: number): boolean {
    return this.openCheckCards().has(index);
  }

  toggleWebhookSection(section: string): void {
    this.webhookOpenSections.update(s => {
      const copy = new Set(s);
      copy.has(section) ? copy.delete(section) : copy.add(section);
      return copy;
    });
  }

  isWebhookSectionOpen(section: string): boolean {
    return this.webhookOpenSections().has(section);
  }

  toggleVarsGroup(key: string): void {
    this.varsGroupOpen[key] = !this.varsGroupOpen[key];
  }

  isVarsGroupOpen(key: string): boolean {
    return this.varsGroupOpen[key] !== false; // default open
  }

  getAvailableVariables(nodeId: string): VariableGroup[] {
    return this.variableGraph.getAvailableVariables(
      nodeId, this.nodes(), this.edges(), this.automation()?.constants ?? [], this.parameterSets());
  }

  // ── INPUT / OUTPUT (integration entry & return) ─────────────
  /** Persists the parameter set that defines an INPUT/OUTPUT node's I/O shape. */
  updateNodeParameterSet(nodeId: string, parameterSetId: string): void {
    const config = this.parseConfigById<Record<string, unknown>>(nodeId, {});
    config['parameterSetId'] = parameterSetId;
    this.updateNodeConfig(nodeId, config);
  }

  getNodeParameterSetId(node: FlowNode): string {
    return this.parseConfig<InputNodeConfig>(node, { parameterSetId: '' }).parameterSetId || '';
  }

  /** Field names of the parameter set referenced by an INPUT/OUTPUT node (for mapper rows / previews). */
  getNodeParameterSetFields(node: FlowNode): string[] {
    const psId = this.getNodeParameterSetId(node);
    const ps = this.parameterSets().find(p => p.id === psId);
    return ps ? ps.parameters.map(p => p.name) : [];
  }

  // ── OUTPUT field mappings ───────────────────────────────────
  getOutputMapping(node: FlowNode, field: string): string {
    return this.parseConfig<OutputNodeConfig>(node, { parameterSetId: '', outputMappings: {} }).outputMappings?.[field] || '';
  }

  updateOutputMapping(nodeId: string, field: string, expression: string): void {
    const config = this.parseConfigById<OutputNodeConfig>(nodeId, { parameterSetId: '', outputMappings: {} });
    if (!config.outputMappings) config.outputMappings = {};
    config.outputMappings[field] = expression;
    this.updateNodeConfig(nodeId, config);
  }

  // ── INTEGRATION_CALL (two-zone mapper) ──────────────────────
  private ensureIntegrationsLoaded(): void {
    if (this.integrationsLoaded) return;
    this.integrationsLoaded = true;
    this.automationService.listIntegrations().subscribe({
      next: (list) => this.integrations.set(list),
      error: () => { this.integrationsLoaded = false; },
    });
  }

  getIntegrationId(node: FlowNode): string {
    return this.parseConfig<IntegrationCallNodeConfig>(node, { integrationId: '', inputMappings: {}, instanceSettings: {} }).integrationId || '';
  }

  /**
   * Picks the integration for a call node, then loads its detail to snapshot the input-field names
   * (from the INPUT node's parameter set) and the internal constant names (instance settings) so the
   * mapper rows render synchronously and {@code integration_<id>.*} output vars stay resolvable.
   */
  selectIntegration(nodeId: string, integrationId: string): void {
    const config = this.parseConfigById<IntegrationCallNodeConfig>(nodeId, { integrationId: '', inputMappings: {}, instanceSettings: {} });
    config.integrationId = integrationId;
    config.inputMappings = {};
    config.instanceSettings = {};
    config.inputFields = [];
    config.outputFields = [];
    this.updateNodeConfig(nodeId, config);
    if (!integrationId) return;
    this.automationService.get(integrationId).subscribe({
      next: (detail) => {
        const inputNode = detail.nodes.find(n => n.nodeType === 'INPUT');
        const outputNode = detail.nodes.find(n => n.nodeType === 'OUTPUT');
        const inputFields = this.parameterSetFieldsFromConfig(inputNode?.config);
        const outputFields = this.parameterSetFieldsFromConfig(outputNode?.config);
        const fresh = this.parseConfigById<IntegrationCallNodeConfig>(nodeId, { integrationId, inputMappings: {}, instanceSettings: {} });
        fresh.integrationId = integrationId;
        fresh.inputFields = inputFields;
        fresh.outputFields = outputFields;
        fresh.instanceSettings = {};
        for (const c of detail.constants ?? []) fresh.instanceSettings[c.name] = '';
        this.updateNodeConfig(nodeId, fresh);
      },
      error: (err) => this.error.emit(humanizeError(err, 'Integration load failed')),
    });
  }

  /** Resolves a parameter set's field names from an INPUT/OUTPUT node config blob (parameterSetId). */
  private parameterSetFieldsFromConfig(config: string | undefined): string[] {
    if (!config) return [];
    let psId = '';
    try { psId = (JSON.parse(config) as InputNodeConfig).parameterSetId || ''; } catch { return []; }
    const ps = this.parameterSets().find(p => p.id === psId);
    return ps ? ps.parameters.map(p => p.name) : [];
  }

  getCallInputFields(node: FlowNode): string[] {
    return this.parseConfig<IntegrationCallNodeConfig>(node, { integrationId: '', inputMappings: {}, instanceSettings: {} }).inputFields || [];
  }

  getCallInstanceSettingKeys(node: FlowNode): string[] {
    const settings = this.parseConfig<IntegrationCallNodeConfig>(node, { integrationId: '', inputMappings: {}, instanceSettings: {} }).instanceSettings || {};
    return Object.keys(settings);
  }

  getCallInputMapping(node: FlowNode, field: string): string {
    return this.parseConfig<IntegrationCallNodeConfig>(node, { integrationId: '', inputMappings: {}, instanceSettings: {} }).inputMappings?.[field] || '';
  }

  updateCallInputMapping(nodeId: string, field: string, expression: string): void {
    const config = this.parseConfigById<IntegrationCallNodeConfig>(nodeId, { integrationId: '', inputMappings: {}, instanceSettings: {} });
    if (!config.inputMappings) config.inputMappings = {};
    config.inputMappings[field] = expression;
    this.updateNodeConfig(nodeId, config);
  }

  getCallInstanceSetting(node: FlowNode, key: string): string {
    return this.parseConfig<IntegrationCallNodeConfig>(node, { integrationId: '', inputMappings: {}, instanceSettings: {} }).instanceSettings?.[key] || '';
  }

  updateCallInstanceSetting(nodeId: string, key: string, value: string): void {
    const config = this.parseConfigById<IntegrationCallNodeConfig>(nodeId, { integrationId: '', inputMappings: {}, instanceSettings: {} });
    if (!config.instanceSettings) config.instanceSettings = {};
    config.instanceSettings[key] = value;
    this.updateNodeConfig(nodeId, config);
  }
}
