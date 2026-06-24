import { AfterViewInit, ChangeDetectionStrategy, Component, computed, effect, ElementRef, HostListener, inject, signal, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import {
  FFlowModule,
  FFlowComponent,
  FCanvasComponent,
  FCanvasChangeEvent,
  FCreateConnectionEvent,
  FMoveNodesEvent,
  FSelectionChangeEvent,
  EFConnectionType,
  EFMarkerType,
} from '@foblex/flow';
import { IPoint } from '@foblex/2d';
import { I18nService } from '../../../../core/services/i18n.service';
import { AutomationService } from '../../../../core/services/automation.service';
import { TemplateService } from '../../../../core/services/template.service';
import { CategoryService } from '../../../../core/services/category.service';
import { ParameterSetService } from '../../../../core/services/parameter-set.service';
import { OrganizationService } from '../../../../core/services/organization.service';
import { ViewportService } from '../../../../core/services/viewport.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import {
  AutomationDetail,
  AutomationConstant,
  FlowUpdateRequest,
  NodeType,
  NODE_PALETTE,
  FilterNodeConfig,
  FilterCheck,
  ExtractNodeConfig,
  ExtractionEntry,
  CategorizeNodeConfig,
  TriggerNodeConfig,
  TriggerMode,
  DelayNodeConfig,
  LabelNodeConfig,
  EmailActionNodeConfig,
  RemoveLabelNodeConfig,
  WebhookNodeConfig,
  CRON_PRESETS,
  PALETTE_GROUPS,
  INTEGRATION_PALETTE_GROUPS,
  NodePaletteItem,
} from '../../../../models/automation.model';
import { Category } from '../../../../models/category.model';
import { Template } from '../../../../models/template.model';
import { ParameterSet } from '../../../../models/parameter-set.model';
import { WorkspaceService } from '../../../../core/services/workspace.service';
import { AutomationTestPanelComponent } from '../automation-test-panel/automation-test-panel.component';
import { TestModePanelComponent } from '../test-mode-panel/test-mode-panel.component';
import { SecretService } from '../../../../core/services/secret.service';
import { AiChatService } from '../../../../core/services/ai-chat.service';
import { Secret } from '../../../../models/secret.model';
import { VariableGroup, getNodeColor, getNodeIcon, getNodeLabelKey, NODE_DEFAULT_CONFIG } from '../../../../models/automation.model';
import { v } from '../../../../shared/utils/event.util';
import { humanizeError } from '../../../../shared/utils/error.util';
import { VariableGraphService } from './variable-graph.service';
import { AutomationLintService, LintIssue, LintSeverity } from './automation-lint.service';
import { ConstantsModalComponent } from './constants-modal/constants-modal.component';
import { NodeConfigPanelComponent } from './node-config-panel/node-config-panel.component';
import { ManualRunDialogComponent } from '../../../../shared/components/manual-run-dialog/manual-run-dialog.component';
import {
  nodeTypeLabel,
  nodeDescription,
  triggerMode as triggerModeUtil,
  scheduleDisplayText,
  categoryEntries,
  delayMinutes,
  labelCategoryName,
  removeLabelCategoryName,
  webhookMethod,
  filterChecks,
  extractions as extractionsUtil,
} from './node-config.util';

interface FlowNode {
  id: string;
  nodeType: NodeType;
  label: string;
  position: IPoint;
  config: string;
  nodeKey?: string | null;
}

interface FlowEdge {
  id: string;
  outputId: string;
  inputId: string;
}

/** Visual node-based automation editor powered by Foblex Flow with drag-and-drop nodes, connections, and a config side panel. */
@Component({
  selector: 'app-automation-editor',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FFlowModule, IconComponent, AutomationTestPanelComponent, TestModePanelComponent, ConstantsModalComponent, NodeConfigPanelComponent, ManualRunDialogComponent],
  templateUrl: './automation-editor.component.html',
  styleUrls: [
    './automation-editor.component.scss',
    './editor-chrome.scss',
    './editor-nodes.scss',
  ],
})
export class AutomationEditorComponent implements AfterViewInit {
  protected i18n = inject(I18nService);
  private automationService = inject(AutomationService);
  private templateService = inject(TemplateService);
  private categoryService = inject(CategoryService);
  private parameterSetService = inject(ParameterSetService);
  protected workspaceService = inject(WorkspaceService);
  private secretService = inject(SecretService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private elRef = inject(ElementRef);
  private variableGraph = inject(VariableGraphService);
  private lintService = inject(AutomationLintService);
  protected aiChat = inject(AiChatService);
  private org = inject(OrganizationService);
  protected viewport = inject(ViewportService);

  /**
   * Whether the active-org role may BUILD automations (Editor+). Members/Viewers get a read-only
   * editor: no save/lock/AI, no node palette/add/delete, config panel disabled. Backend re-checks.
   */
  readonly canEdit = computed(() => this.org.can('AUTOMATION_EDIT'));

  /**
   * Whether the role may take a flow LIVE / run it (Admin+). Editors build but cannot activate —
   * so the status menu and the manual-run button are gated on this, not on {@link canEdit}.
   */
  readonly canActivate = computed(() => this.org.can('AUTOMATION_ACTIVATE'));

  /**
   * Phones (<md) get a view-only editor regardless of role: pan/zoom + tap-to-inspect a node's
   * config (read-only), but no palette, drag, connect, add/delete or save/run. Touch-drag node
   * editing on a phone is intentionally unsupported.
   */
  readonly viewOnly = computed(() => this.viewport.isMobile());

  /** Whether the user may manipulate the canvas right now — has the role AND is not on a phone. */
  readonly editable = computed(() => this.canEdit() && !this.viewOnly());

  @ViewChild(FFlowComponent) fFlow!: FFlowComponent;
  @ViewChild(FCanvasComponent) fCanvas!: FCanvasComponent;
  @ViewChild(AutomationTestPanelComponent) testPanel?: AutomationTestPanelComponent;

  readonly palette = NODE_PALETTE;
  readonly paletteGroups = PALETTE_GROUPS;
  readonly cronPresets = CRON_PRESETS;
  readonly EFConnectionType = EFConnectionType;
  readonly EFMarkerType = EFMarkerType;

  readonly weekDays = [
    { value: 1, labelKey: 'auto_schedule_mon' },
    { value: 2, labelKey: 'auto_schedule_tue' },
    { value: 3, labelKey: 'auto_schedule_wed' },
    { value: 4, labelKey: 'auto_schedule_thu' },
    { value: 5, labelKey: 'auto_schedule_fri' },
    { value: 6, labelKey: 'auto_schedule_sat' },
    { value: 0, labelKey: 'auto_schedule_sun' },
  ];

  readonly monthDays = Array.from({ length: 28 }, (_, i) => i + 1);

  // Tool library rail (Bausteine)
  railCollapsed = signal(false);
  librarySearch = signal('');
  libTip = signal<{ name: string; sub: string; top: number; left: number; nc: string } | null>(null);

  automationId = signal('');
  automation = signal<AutomationDetail | null>(null);
  nodes = signal<FlowNode[]>([]);
  edges = signal<FlowEdge[]>([]);
  templates = signal<Template[]>([]);
  /** Accounts that can send mail (SMTP/write enabled) — used as SEND_EMAIL sender options. */
  sendableAccounts = computed(() => this.workspaceService.accounts().filter(a => a.writeEnabled));
  categories = signal<Category[]>([]);
  parameterSets = signal<ParameterSet[]>([]);
  secrets = signal<Secret[]>([]);

  // Manual email compose modal (shared by EMAIL_ACTION/REPLY and SEND_EMAIL nodes)
  composeOpen = signal(false);
  composeNodeId = signal<string | null>(null);
  composeNodeKind = signal<'email_action' | 'send_email'>('email_action');
  composeDraft = signal<{ subject: string; body: string }>({ subject: '', body: '' });
  composeFocus = signal<'subject' | 'body'>('body');


  // Constants / variables modal — state/logic lives in ConstantsModalComponent
  constantsOpen = signal(false);

  // Manual-run dialog (MANUAL trigger) — collects trigger.* parameter values then fires the run.
  manualRunOpen = signal(false);

  selectedNodeId = signal<string | null>(null);
  selectedConnectionIds = signal<string[]>([]);
  configPanelOpen = signal(false);
  saving = signal(false);
  error = signal('');
  zoomLevel = signal(100);
  tipsOpen = signal(false);
  legendOpen = signal(true);
  editorTab = signal<'canvas' | 'tests' | 'simulations'>('canvas');

  testPanelNodes = computed(() => this.nodes().map(n => {
    const info: Record<string, unknown> = { id: n.id, nodeType: n.nodeType, label: n.label };
    if (n.nodeType === 'EXTRACT') {
      try {
        const cfg = JSON.parse(n.config || '{}');
        info['parameterSets'] = (cfg.extractions || []).map((entry: { parameterSetId: string; label: string }) => {
          const ps = this.parameterSets().find(p => p.id === entry.parameterSetId);
          return {
            id: entry.parameterSetId,
            name: ps?.name || entry.label,
            parameters: ps ? ps.parameters.map(p => p.name) : [],
          };
        });
      } catch { /* ignore */ }
    } else if (n.nodeType === 'CATEGORIZE') {
      try {
        const cfg = JSON.parse(n.config || '{}');
        info['categories'] = (cfg.categoryIds || []).map((id: string) => {
          const cat = this.categories().find(c => c.id === id);
          return { name: cat?.name || id, color: cat?.color || 'var(--fg-muted)' };
        });
      } catch { /* ignore */ }
    } else if (n.nodeType === 'LABEL' || n.nodeType === 'REMOVE_LABEL') {
      try {
        const cfg = JSON.parse(n.config || '{}');
        if (cfg.categoryId) {
          const cat = this.categories().find(c => c.id === cfg.categoryId);
          info['categoryName'] = cat?.name || cfg.categoryId;
          info['categoryColor'] = cat?.color || 'var(--fg-muted)';
        }
      } catch { /* ignore */ }
    } else if (n.nodeType === 'WEBHOOK') {
      try {
        const cfg = JSON.parse(n.config || '{}');
        info['webhookMethod'] = cfg.method || 'POST';
        info['webhookUrl'] = cfg.url || '';
      } catch { /* ignore */ }
    } else if (n.nodeType === 'INPUT') {
      try {
        const cfg = JSON.parse(n.config || '{}');
        const ps = this.parameterSets().find(p => p.id === cfg.parameterSetId);
        info['fields'] = ps ? ps.parameters.map(p => p.name) : [];
      } catch { /* ignore */ }
    } else if (n.nodeType === 'OUTPUT') {
      try {
        const cfg = JSON.parse(n.config || '{}');
        const mappingKeys = Object.keys(cfg.outputMappings || {});
        if (mappingKeys.length > 0) {
          info['fields'] = mappingKeys;
        } else {
          const ps = this.parameterSets().find(p => p.id === cfg.parameterSetId);
          info['fields'] = ps ? ps.parameters.map(p => p.name) : [];
        }
      } catch { /* ignore */ }
    } else if (n.nodeType === 'INTEGRATION_CALL') {
      try {
        const cfg = JSON.parse(n.config || '{}');
        info['outputFields'] = cfg.outputFields || [];
      } catch { /* ignore */ }
    } else if (n.nodeType === 'VECTOR_SEARCH') {
      // Judge outputs are always present; the matched entry's match.<field> names come from the
      // KB's parameter set, cached into the node config when the KB is picked.
      try {
        const cfg = JSON.parse(n.config || '{}');
        const matchFields: string[] = Array.isArray(cfg.matchFields) ? cfg.matchFields : [];
        info['fields'] = ['confidence', 'reason', ...matchFields.map((f: string) => `match.${f}`)];
      } catch {
        info['fields'] = ['confidence', 'reason'];
      }
    } else if (n.nodeType === 'NOTIFY') {
      // NOTIFY always injects notify_<id>.sent + notify_<id>.recipientCount downstream.
      info['fields'] = ['sent', 'recipientCount'];
    }
    return info as { id: string; nodeType: string; label: string };
  }));

  /** The automation's TRIGGER mode (EMAIL/WEBHOOK/CRON/MANUAL) — drives the Tests setup input panel. */
  triggerKind = computed<TriggerMode>(() => {
    const trigger = this.nodes().find(n => n.nodeType === 'TRIGGER');
    if (!trigger) return 'EMAIL';
    try {
      return (JSON.parse(trigger.config || '{}').triggerMode as TriggerMode) || 'EMAIL';
    } catch { return 'EMAIL'; }
  });

  // ── Live lint (mirrors the backend AutomationValidator) ─────────────
  /** All semantic problems in the current flow (errors + warnings), recomputed reactively. */
  lintIssues = computed<LintIssue[]>(() => this.lintService.lint(
    this.automation()?.kind ?? 'AUTOMATION',
    this.nodes(), this.edges(),
    this.automation()?.constants ?? [], this.parameterSets(),
  ));
  /** Lint issues grouped by node id (flow-level issues are keyed under '__flow__'). */
  issuesByNode = computed(() => {
    const map = new Map<string, LintIssue[]>();
    for (const issue of this.lintIssues()) {
      const key = issue.nodeId ?? '__flow__';
      (map.get(key) ?? map.set(key, []).get(key)!).push(issue);
    }
    return map;
  });
  lintErrorCount = computed(() => this.lintIssues().filter(i => i.severity === 'error').length);
  lintWarningCount = computed(() => this.lintIssues().filter(i => i.severity === 'warning').length);
  hasBlockingErrors = computed(() => this.lintErrorCount() > 0);
  problemsOpen = signal(false);

  /** Highest-severity badge for a node ('error' wins over 'warning'), or null when clean. */
  nodeSeverity(nodeId: string): LintSeverity | null {
    const list = this.issuesByNode().get(nodeId);
    if (!list || list.length === 0) return null;
    return list.some(i => i.severity === 'error') ? 'error' : 'warning';
  }

  /** Resolves a node's label for the Problems panel (flow-level issues show a generic label). */
  issueNodeLabel(nodeId: string | null): string {
    if (!nodeId) return this.i18n.t('auto_lint_flow_level');
    return this.nodes().find(n => n.id === nodeId)?.label ?? nodeId;
  }

  /** Selects + opens the config panel for the node an issue belongs to. */
  focusIssue(issue: LintIssue): void {
    if (issue.nodeId) this.selectNode(issue.nodeId);
  }

  // Canvas context menu
  ctxMenuOpen = signal(false);
  ctxMenuPos = signal<{ x: number; y: number }>({ x: 0, y: 0 });
  private ctxCanvasPos: IPoint = { x: 0, y: 0 };

  // Node context menu
  nodeCtxMenuOpen = signal(false);
  nodeCtxMenuPos = signal<{ x: number; y: number }>({ x: 0, y: 0 });
  nodeCtxTarget = signal<string | null>(null);

  // Middle-mouse pan
  panning = signal(false);
  private panStart: IPoint = { x: 0, y: 0 };
  private canvasPosStart: IPoint = { x: 0, y: 0 };
  private currentCanvasPos: IPoint = { x: 0, y: 0 };
  private panSetPosition: IPoint = { x: 0, y: 0 }; // tracks actual transform.position (excludes scaledPosition)

  /** True when editing an integration (trigger-less, INPUT/OUTPUT entry points instead of TRIGGER). */
  isIntegration = computed(() => this.automation()?.kind === 'INTEGRATION');

  selectedNode = computed(() => {
    const id = this.selectedNodeId();
    return id ? this.nodes().find(n => n.id === id) ?? null : null;
  });

  selectedNodeConfig = computed(() => {
    const node = this.selectedNode();
    if (!node) return null;
    return this.parseConfig<any>(node, {});
  });

  private nextNodeId = 1;

  constructor() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.automationId.set(id);
      this.loadAutomation(id);
    }
    this.loadTemplates();
    this.loadCategories();
    this.loadParameterSets();
    this.loadSecrets();

    // Reload this automation when the AI assistant reports it mutated automations, so AI edits made
    // from the in-editor chat appear live on the canvas without leaving the page.
    let lastAiSeq = this.aiChat.resourceMutation().seq;
    effect(() => {
      const m = this.aiChat.resourceMutation();
      if (m.seq === lastAiSeq) return;
      lastAiSeq = m.seq;
      const id = this.automationId();
      if (id && m.types.includes('automations') && !this.saving()) {
        this.loadAutomation(id);
      }
    });

    effect(() => {
      if (this.editorTab() === 'tests' && this.testPanel) {
        this.testPanel.loadTests();
      }
    });
  }

  // Canvas pan trigger (fCanvasMoveTrigger → true = allow a drag-pan). We allow it for every input:
  // left-click drag on empty canvas pans (Figma-style), and so does one-finger touch/pen drag.
  // Foblex only consults this for the LEFT button / touch (middle-button down is filtered upstream
  // by its own isMouseLeftButton() check), so the middle-mouse pan in onCanvasMouseDown never clashes.
  // Why: Mac trackpads / Magic Mouse have no middle button, so left-drag is the only pan they can do.
  canvasMoveTrigger = (_event: MouseEvent | TouchEvent): boolean => true;

  // fWheelTrigger → Foblex only zooms on the wheel when this returns true. We restrict zoom to a
  // pinch gesture (macOS trackpad pinch fires a wheel event with ctrlKey) or Ctrl/Cmd+wheel; a plain
  // wheel / two-finger trackpad scroll is left to onCanvasWheel, which pans (the Mac-native feel).
  zoomOnPinch = (event: MouseEvent | TouchEvent | WheelEvent): boolean => event.ctrlKey || event.metaKey;

  /**
   * Single-tap on a node. On touch viewports (tablet + phone) a tap opens the config panel —
   * desktop keeps the double-click affordance so a single click can still select/drag.
   */
  onNodeClick(nodeId: string): void {
    if (this.viewport.isTabletDown()) this.selectNode(nodeId);
  }

  ngAfterViewInit(): void {
  }

  goBack(): void {
    this.router.navigate([this.isIntegration() ? '/dashboard/integrations' : '/dashboard/automations']);
  }

  // ── Node operations ────────────────────────
  /** Creates a fresh node of the given type at a position and appends it to the canvas. */
  private createNode(type: NodeType, position: IPoint): void {
    this.nodes.update(list => [...list, {
      id: `node-${this.nextNodeId++}`,
      nodeType: type,
      label: this.i18n.t(getNodeLabelKey(type)),
      position,
      config: JSON.stringify(NODE_DEFAULT_CONFIG[type]),
    }]);
  }

  addNode(type: NodeType): void {
    // Integrations allow at most one INPUT and one OUTPUT node.
    if ((type === 'INPUT' || type === 'OUTPUT') && this.nodes().some(n => n.nodeType === type)) return;
    this.createNode(type, { x: 200 + Math.random() * 200, y: 100 + Math.random() * 200 });
  }

  deleteNode(nodeId: string): void {
    this.nodes.update(list => list.filter(n => n.id !== nodeId));
    this.edges.update(list => list.filter(e =>
      !e.outputId.startsWith(nodeId) && !e.inputId.startsWith(nodeId)
    ));
    if (this.selectedNodeId() === nodeId) {
      this.selectedNodeId.set(null);
      this.configPanelOpen.set(false);
    }
  }

  selectNode(nodeId: string): void {
    this.selectedNodeId.set(nodeId);
    this.configPanelOpen.set(true);
  }

  closePanel(): void {
    this.selectedNodeId.set(null);
    this.configPanelOpen.set(false);
  }

  // ── Connection events ──────────────────────
  onConnectionCreated(event: FCreateConnectionEvent): void {
    if (!this.editable()) return;
    if (!event.targetId) return;
    if (!this.isConnectionValid(event.sourceId, event.targetId)) return;
    const id = `edge-${Date.now()}`;
    this.edges.update(list => [...list, {
      id,
      outputId: event.sourceId,
      inputId: event.targetId!,
    }]);
  }

  /** All connections are valid — unified port system (all circles). */
  private isConnectionValid(_sourceId: string, _targetId: string): boolean {
    return true;
  }

  onSelectionChanged(event: FSelectionChangeEvent): void {
    this.selectedConnectionIds.set(event.connectionIds);
  }

  @HostListener('window:keydown', ['$event'])
  onKeyDown(event: KeyboardEvent): void {
    if (!this.editable()) return;
    if (event.key === 'Delete' || event.key === 'Backspace') {
      const target = event.target as HTMLElement;
      if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.tagName === 'SELECT') return;
      const connIds = this.selectedConnectionIds();
      if (connIds.length > 0) {
        this.edges.update(list => list.filter(e => !connIds.includes(e.id)));
        this.selectedConnectionIds.set([]);
      }
    }
  }

  onNodeMoved(event: FMoveNodesEvent): void {
    if (!this.editable()) return;
    const updates = event.nodes;
    this.nodes.update(list =>
      list.map(n => {
        const moved = updates.find(u => u.id === n.id);
        return moved ? { ...n, position: moved.position } : n;
      })
    );
  }

  // ── Config panel updates ───────────────────
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

  /** Parses a node's config JSON, returning {@link fallback} when the node is absent or the JSON is invalid. */
  private parseConfig<T>(node: FlowNode | undefined, fallback: T): T {
    if (!node) return fallback;
    try { return JSON.parse(node.config || '{}') as T; } catch { return fallback; }
  }

  /** Finds a node by id and parses its config JSON, returning {@link fallback} when absent or invalid. */
  private parseConfigById<T>(nodeId: string, fallback: T): T {
    return this.parseConfig(this.nodes().find(n => n.id === nodeId), fallback);
  }

  // ── Filter condition helpers (multi-check format) ────────────────
  getFilterChecks(node: FlowNode): FilterCheck[] {
    return filterChecks(node);
  }

  // ── Email action config helpers ──────────────────
  getEmailActionIcon(node: FlowNode): string {
    const mode = this.parseConfig<EmailActionNodeConfig>(node, { actionMode: 'REPLY' }).actionMode || 'REPLY';
    return mode === 'FORWARD' ? 'forward' : mode === 'MOVE_FOLDER' ? 'archive' : 'reply';
  }

  getEmailActionSummary(node: FlowNode): string {
    const config = this.parseConfig<EmailActionNodeConfig>(node, { actionMode: 'REPLY' });
    switch (config.actionMode) {
      case 'FORWARD': return config.toAddress || '...';
      case 'MOVE_FOLDER': return config.folder || '...';
      default: return this.i18n.t('auto_node_reply');
    }
  }

  // ── Compose modal ──────────────────────────────────────────────
  openCompose(nodeId: string, kind: 'email_action' | 'send_email'): void {
    const node = this.nodes().find(n => n.id === nodeId);
    if (!node) return;
    let config: Record<string, unknown> = {};
    try { config = JSON.parse(node.config || '{}'); } catch { /* keep defaults */ }
    this.composeNodeId.set(nodeId);
    this.composeNodeKind.set(kind);
    this.composeDraft.set({
      subject: String(config['subject'] || ''),
      body: String(config['body'] || ''),
    });
    this.composeFocus.set('body');
    this.composeOpen.set(true);
  }

  updateComposeField(field: 'subject' | 'body', value: string): void {
    this.composeDraft.update(d => ({ ...d, [field]: value }));
  }

  insertComposeVariable(key: string): void {
    const token = '{{' + key + '}}';
    const field = this.composeFocus();
    this.composeDraft.update(d => {
      const current = d[field] || '';
      const sep = current && !current.endsWith(' ') && !current.endsWith('\n') ? ' ' : '';
      return { ...d, [field]: current + sep + token };
    });
  }

  applyCompose(): void {
    const nodeId = this.composeNodeId();
    if (!nodeId) { this.composeOpen.set(false); return; }
    const node = this.nodes().find(n => n.id === nodeId);
    if (!node) { this.composeOpen.set(false); return; }
    let config: Record<string, unknown> = {};
    try { config = JSON.parse(node.config || '{}'); } catch { /* keep defaults */ }
    const draft = this.composeDraft();
    config['subject'] = draft.subject;
    config['body'] = draft.body;
    config['contentSource'] = 'MANUAL';
    config['templateId'] = '';
    this.updateNodeConfig(nodeId, config);
    this.composeOpen.set(false);
    this.composeNodeId.set(null);
  }

  cancelCompose(): void {
    this.composeOpen.set(false);
    this.composeNodeId.set(null);
  }

  // ── Webhook config helpers ──────────────────
  getWebhookMethod(node: FlowNode): string {
    return webhookMethod(node);
  }

  getWebhookUrlPreview(node: FlowNode): string {
    const url = (this.parseConfig<Record<string, unknown>>(node, {})['url'] as string) || '';
    if (url.length > 30) return url.substring(0, 30) + '...';
    return url;
  }

  // ── Vector-search config helper ─────────────
  getVectorSearchKbName(node: FlowNode): string {
    return (this.parseConfig<Record<string, unknown>>(node, {})['knowledgeBaseName'] as string) || '';
  }

  // ── Notify config helper ─────────────
  /** Short label for a NOTIFY node card: its title, or the message, or empty. */
  getNotifySummary(node: FlowNode): string {
    const cfg = this.parseConfig<Record<string, unknown>>(node, {});
    return ((cfg['title'] as string) || (cfg['message'] as string) || '').trim();
  }

  // ── Remove label config helpers ──────────────────
  updateRemoveLabelCategory(nodeId: string, categoryId: string): void {
    this.updateNodeConfig(nodeId, { categoryId: categoryId || undefined });
  }

  // ── Context menu ─────────────────────────────
  onCanvasContextMenu(event: MouseEvent): void {
    // Only open if clicking on empty canvas (not on a node)
    const target = event.target as HTMLElement;
    if (target.closest('.ae-node') || target.closest('.ae-panel')) return;
    event.preventDefault();
    this.ctxMenuPos.set({ x: event.clientX, y: event.clientY });
    // Convert screen coords to canvas coords
    const wrap = (event.currentTarget as HTMLElement).getBoundingClientRect();
    const scale = this.zoomLevel() / 100;
    this.ctxCanvasPos = {
      x: (event.clientX - wrap.left - this.currentCanvasPos.x) / scale,
      y: (event.clientY - wrap.top - this.currentCanvasPos.y) / scale,
    };
    this.ctxMenuOpen.set(true);
  }

  addNodeAt(type: NodeType): void {
    this.ctxMenuOpen.set(false);
    // Integrations allow at most one INPUT and one OUTPUT node.
    if ((type === 'INPUT' || type === 'OUTPUT') && this.nodes().some(n => n.nodeType === type)) return;
    this.createNode(type, { x: this.ctxCanvasPos.x, y: this.ctxCanvasPos.y });
  }

  closeContextMenu(): void {
    this.ctxMenuOpen.set(false);
  }

  // ── Node context menu ───────────────────────
  onNodeContextMenu(event: MouseEvent, nodeId: string): void {
    event.preventDefault();
    event.stopPropagation();
    this.ctxMenuOpen.set(false);
    this.nodeCtxMenuPos.set({ x: event.clientX, y: event.clientY });
    this.nodeCtxTarget.set(nodeId);
    this.nodeCtxMenuOpen.set(true);
  }

  closeNodeContextMenu(): void {
    this.nodeCtxMenuOpen.set(false);
    this.nodeCtxTarget.set(null);
  }

  copyNode(nodeId: string): void {
    const node = this.nodes().find(n => n.id === nodeId);
    if (!node) return;
    const newId = `node-${this.nextNodeId++}`;
    this.nodes.update(list => [...list, {
      id: newId,
      nodeType: node.nodeType,
      label: node.label,
      position: { x: node.position.x + 40, y: node.position.y + 40 },
      config: node.config,
    }]);
    this.closeNodeContextMenu();
  }

  // ── Middle-mouse pan ────────────────────────
  onCanvasMouseDown(event: MouseEvent): void {
    if (event.button !== 1) return; // middle button only
    event.preventDefault();
    this.panning.set(true);
    this.panStart = { x: event.clientX, y: event.clientY };
    // Seed from the canvas' live transform so a middle-drag started after a native left-drag pan
    // (or a wheel pan) continues from where the canvas actually sits, instead of jumping.
    this.canvasPosStart = this.fCanvas ? { ...this.fCanvas.getPosition() } : { ...this.panSetPosition };
  }

  /**
   * Trackpad / mouse-wheel pan. Foblex's fZoom owns zoom (pinch / Ctrl+Cmd, gated via {@link zoomOnPinch});
   * a plain wheel reaches here and scrolls the canvas — what Mac users expect from a two-finger swipe.
   * Reads the live transform each event (mirrors Foblex's own DragCanvasHandler) so it stays in sync
   * with left-drag pans and zoom.
   */
  onCanvasWheel(event: WheelEvent): void {
    if (event.ctrlKey || event.metaKey || !this.fCanvas) return;
    event.preventDefault();
    const base = this.fCanvas.getPosition();
    const newPos = { x: base.x - event.deltaX, y: base.y - event.deltaY };
    this.panSetPosition = newPos;
    this.fCanvas._setPosition(newPos);
    this.fCanvas.redraw();
    this.fCanvas.emitCanvasChangeEvent();
  }

  @HostListener('window:mousemove', ['$event'])
  onWindowMouseMove(event: MouseEvent): void {
    if (!this.panning()) return;
    const dx = event.clientX - this.panStart.x;
    const dy = event.clientY - this.panStart.y;
    const newPos = { x: this.canvasPosStart.x + dx, y: this.canvasPosStart.y + dy };
    this.panSetPosition = newPos;
    if (this.fCanvas) {
      this.fCanvas._setPosition(newPos);
      this.fCanvas.redraw();
    }
  }

  @HostListener('window:mouseup', ['$event'])
  onWindowMouseUp(event: MouseEvent): void {
    if (event.button === 1 && this.panning()) {
      this.panning.set(false);
    }
  }

  // ── Save ───────────────────────────────────
  save(): void {
    this.saving.set(true);
    this.error.set('');

    const request: FlowUpdateRequest = {
      nodes: this.nodes().map(n => ({
        id: n.id,
        nodeType: n.nodeType,
        label: n.label,
        positionX: n.position.x,
        positionY: n.position.y,
        config: n.config,
        nodeKey: n.nodeKey,
      })),
      edges: this.edges().map(e => ({
        id: e.id,
        sourceNodeId: e.outputId.split('_')[0],
        sourceHandle: e.outputId.includes('_') ? e.outputId.split('_').slice(1).join('_') : 'output',
        targetNodeId: e.inputId.split('_')[0],
        targetHandle: e.inputId.includes('_') ? e.inputId.split('_').slice(1).join('_') : 'input',
      })),
    };

    this.automationService.updateFlow(this.automationId(), request).subscribe({
      next: (detail) => {
        this.saving.set(false);
        this.loadFromDetail(detail);
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(this.humanizeError(err, 'Save failed'));
      },
    });
  }

  /** Opens the manual-run dialog (the dialog collects parameter values and fires the run). */
  runManually(): void {
    this.manualRunOpen.set(true);
  }

  /**
   * Turns a backend error into a readable, localized message. Quota errors
   * (HTTP 429 with a structured limitType) become a friendly plan-specific
   * message; everything else falls back to the raw message or a default.
   */
  private humanizeError(err: { error?: { limitType?: string; planName?: string; maxAllowed?: number; currentUsage?: number; message?: string } }, fallback: string): string {
    const e = err?.error;
    if (e?.limitType) {
      const vars = {
        plan: e.planName ?? '',
        current: String(e.currentUsage ?? 0),
        max: String(e.maxAllowed ?? 0),
      };
      if (e.limitType === 'INBOUND_WEBHOOK' || e.limitType === 'WEBHOOK') {
        return this.i18n.t(e.maxAllowed === 0 ? 'quota_err_inbound_webhook_disabled' : 'quota_err_inbound_webhook', vars);
      }
      return this.i18n.t('quota_err_generic', vars);
    }
    return e?.message || fallback;
  }

  toggleLock(): void {
    this.automationService.toggleLock(this.automationId()).subscribe(updated => {
      const current = this.automation();
      if (current) {
        this.automation.set({ ...current, locked: updated.locked });
      }
    });
  }

  focusOnStart(): void {
    if (!this.fCanvas) return;
    this.fCanvas.resetScale();
    const startType: NodeType = this.isIntegration() ? 'INPUT' : 'TRIGGER';
    const trigger = this.nodes().find(n => n.nodeType === startType) || this.nodes()[0];
    if (trigger) {
      const pos = trigger.position;
      const newPos = { x: -pos.x + 200, y: -pos.y + 200 };
      this.fCanvas._setPosition(newPos);
      this.panSetPosition = newPos;
    }
    this.fCanvas.redrawWithAnimation();
    this.zoomLevel.set(100);
  }

  onCanvasChange(event: FCanvasChangeEvent): void {
    this.zoomLevel.set(Math.round(event.scale * 100));
    this.currentCanvasPos = event.position;
  }

  private canvasCenter(): IPoint {
    const wrap = this.elRef.nativeElement.querySelector('.ae-canvas-wrap') as HTMLElement | null;
    if (!wrap) return { x: 0, y: 0 };
    const r = wrap.getBoundingClientRect();
    return { x: r.width / 2, y: r.height / 2 };
  }

  private zoomBy(delta: number): void {
    if (!this.fCanvas) return;
    const current = this.fCanvas.getScale();
    const next = Math.min(3, Math.max(0.2, Math.round((current + delta) * 10) / 10));
    if (next === current) return;
    this.fCanvas.setScale(next, this.canvasCenter());
    this.fCanvas.redrawWithAnimation();
    this.zoomLevel.set(Math.round(next * 100));
  }

  zoomIn(): void { this.zoomBy(0.1); }
  zoomOut(): void { this.zoomBy(-0.1); }

  resetZoom(): void {
    if (!this.fCanvas) return;
    this.fCanvas.resetScaleAndCenter(true);
    this.zoomLevel.set(100);
  }

  toggleStatus(): void {
    const a = this.automation();
    if (!a) return;
    const newStatus = a.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE';
    this.automationService.updateStatus(a.id, newStatus).subscribe({
      next: () => this.loadAutomation(a.id),
      error: (err) => this.error.set(humanizeError(err, 'Error')),
    });
  }

  editorStatusMenuOpen = signal(false);

  setEditorStatus(status: 'ACTIVE' | 'TESTING' | 'PAUSED'): void {
    const a = this.automation();
    if (!a || a.status === status) return;
    this.editorStatusMenuOpen.set(false);
    // Mirror the backend activation gate so the user isn't surprised by a 400.
    if (status === 'ACTIVE' && this.hasBlockingErrors()) {
      this.error.set(this.i18n.t('auto_lint_fix_before_activate'));
      this.problemsOpen.set(true);
      return;
    }
    this.automationService.updateStatus(a.id, status).subscribe({
      next: () => {
        this.loadAutomation(a.id);
        if (status === 'TESTING') this.editorTab.set('simulations');
        else if (this.editorTab() === 'simulations') this.editorTab.set('canvas');
      },
      error: (err) => this.error.set(humanizeError(err, 'Error')),
    });
  }

  // ── Helpers ────────────────────────────────
  readonly getNodeColor = getNodeColor;

  getNodeTypeLabel(type: NodeType): string {
    return nodeTypeLabel(this.i18n, type);
  }

  readonly getNodeIcon = getNodeIcon;

  getNodeDescription(type: NodeType): string {
    return nodeDescription(this.i18n, type);
  }

  getOutputId(node: FlowNode, handle: string): string {
    return `${node.id}_${handle}`;
  }

  getInputId(node: FlowNode): string {
    return `${node.id}_input`;
  }

  readonly v = v;

  // ── SEND_EMAIL config helpers ──────────────────
  getSendEmailTo(node: FlowNode): string {
    return this.parseConfig<Record<string, unknown>>(node, {})['to'] as string || '';
  }

  getRemoveLabelCategoryId(node: FlowNode): string {
    return this.parseConfig<RemoveLabelNodeConfig>(node, { categoryId: '' }).categoryId || '';
  }

  getRemoveLabelCategoryName(node: FlowNode): string {
    return removeLabelCategoryName(this.categories(), node);
  }

  // ── Webhook response branch helpers (for node ports) ────
  /** The webhook's non-"unmatched" response branches with their array index → output handle resp_<index>.
   *  The "unmatched" catch-all is always rendered separately (like FILTER's fallback). */
  getWebhookBranches(node: FlowNode): { name: string; condition: string; index: number }[] {
    const config = this.parseConfig<WebhookNodeConfig>(node, {} as WebhookNodeConfig);
    const out: { name: string; condition: string; index: number }[] = [];
    (config.responseSchemas || []).forEach((s, i) => {
      if ((s.condition || '').trim().toLowerCase() !== 'unmatched') {
        out.push({ name: s.name || '', condition: s.condition, index: i });
      }
    });
    return out;
  }

  // ── Extract helpers ─────────────────────────
  getExtractions(node: FlowNode): ExtractionEntry[] {
    return extractionsUtil(node);
  }

  // ── Categorize helpers ─────────────────────────
  getCategoryEntries(node: FlowNode): { categoryId: string; label: string; color: string }[] {
    return categoryEntries(this.categories(), node);
  }

  // ── Trigger helpers ──────────────────────────
  getTriggerAccountIds(node: FlowNode): string[] {
    return this.parseConfig<TriggerNodeConfig>(node, {} as TriggerNodeConfig).accountIds || [];
  }

  getAccountName(accountId: string): string {
    const acc = this.workspaceService.accounts().find(a => a.id === accountId);
    return acc ? (acc.displayName || acc.email) : accountId;
  }

  // ── Trigger mode helpers ──────────────────────
  getTriggerMode(node: FlowNode): TriggerMode {
    return triggerModeUtil(node);
  }

  // ── Tool library rail (Bausteine) ────────────
  /** Palette groups filtered by the live search query (name + subtitle). */
  libraryGroups(): { labelKey: string; items: NodePaletteItem[] }[] {
    const q = this.librarySearch().trim().toLowerCase();
    const groups = this.isIntegration() ? INTEGRATION_PALETTE_GROUPS : this.paletteGroups;
    return groups
      .map(g => ({
        labelKey: g.labelKey,
        items: g.items.filter(it => {
          if (!q) return true;
          const name = this.i18n.t(it.labelKey).toLowerCase();
          const sub = it.subKey ? this.i18n.t(it.subKey).toLowerCase() : '';
          return (name + ' ' + sub).includes(q);
        }),
      }))
      .filter(g => g.items.length > 0);
  }

  libraryTotal(): number {
    return this.palette.length;
  }

  nodeSub(item: NodePaletteItem): string {
    return item.subKey ? this.i18n.t(item.subKey) : '';
  }

  toggleRail(): void {
    this.railCollapsed.update(v => !v);
    this.libTip.set(null);
  }

  showLibTip(event: MouseEvent, item: NodePaletteItem): void {
    if (!this.railCollapsed()) return;
    const r = (event.currentTarget as HTMLElement).getBoundingClientRect();
    this.libTip.set({
      name: this.i18n.t(item.labelKey),
      sub: this.nodeSub(item),
      top: r.top + r.height / 2,
      left: r.right + 12,
      nc: getNodeColor(item.type),
    });
  }

  hideLibTip(): void {
    this.libTip.set(null);
  }

  // ── Schedule helpers ─────────────────────────
  getScheduleType(node: FlowNode): string {
    return this.parseConfig<TriggerNodeConfig>(node, {} as TriggerNodeConfig).scheduleType || 'INTERVAL';
  }

  getSchedulePreset(node: FlowNode): string {
    return this.parseConfig<TriggerNodeConfig>(node, {} as TriggerNodeConfig).preset || 'hourly';
  }

  getScheduleDisplayText(node: FlowNode): string {
    return scheduleDisplayText(this.i18n, this.weekDays, node);
  }

  // ── Delay helpers ──────────────────────────────
  getDelayMinutes(node: FlowNode): number {
    return delayMinutes(node);
  }

  // ── Label helpers ──────────────────────────────
  getLabelCategoryId(node: FlowNode): string {
    return this.parseConfig<LabelNodeConfig>(node, {}).categoryId || '';
  }

  getLabelCategoryName(node: FlowNode): string {
    return labelCategoryName(this.categories(), node);
  }

  updateLabelCategory(nodeId: string, categoryId: string): void {
    this.updateNodeConfig(nodeId, { categoryId: categoryId || undefined });
  }

  private loadAutomation(id: string): void {
    this.automationService.get(id).subscribe({
      next: (detail) => this.loadFromDetail(detail),
      error: () => this.router.navigate(['/dashboard/automations']),
    });
  }

  private loadFromDetail(detail: AutomationDetail): void {
    this.automation.set(detail);

    const flowNodes: FlowNode[] = detail.nodes.map(n => {
      return { id: n.id, nodeType: n.nodeType, label: n.label || n.nodeType, position: { x: n.positionX, y: n.positionY }, config: n.config, nodeKey: n.nodeKey };
    });

    const flowEdges: FlowEdge[] = detail.edges.map(e => ({
      id: e.id,
      outputId: `${e.sourceNodeId}_${e.sourceHandle}`,
      inputId: `${e.targetNodeId}_${e.targetHandle}`,
    }));

    // Set nextNodeId higher than any existing
    this.nextNodeId = flowNodes.length + 1;

    // An integration must always have exactly one INPUT entry node — seed it if missing.
    if (detail.kind === 'INTEGRATION' && !flowNodes.some(n => n.nodeType === 'INPUT')) {
      flowNodes.push({
        id: `node-${this.nextNodeId++}`,
        nodeType: 'INPUT',
        label: this.i18n.t(getNodeLabelKey('INPUT')),
        position: { x: 120, y: 160 },
        config: JSON.stringify(NODE_DEFAULT_CONFIG['INPUT']),
      });
    }

    this.nodes.set(flowNodes);
    this.edges.set(flowEdges);
  }

  private loadCategories(): void {
    this.categoryService.list().subscribe({
      next: (list) => this.categories.set(list),
      error: () => {},
    });
  }

  private loadTemplates(): void {
    this.templateService.list().subscribe({
      next: (list) => this.templates.set(list),
      error: () => {},
    });
  }

  private loadParameterSets(): void {
    this.parameterSetService.list().subscribe({
      next: (list) => this.parameterSets.set(list),
      error: () => {},
    });
  }

  private loadSecrets(): void {
    this.secretService.list().subscribe({
      next: (list) => this.secrets.set(list),
      error: () => {},
    });
  }

  // ── Constants / variables modal ────────────────────────────
  openConstants(): void {
    this.constantsOpen.set(true);
  }

  /** Updates local automation state after the modal persists its constant list. */
  onConstantsChanged(list: AutomationConstant[]): void {
    const current = this.automation();
    if (current) this.automation.set({ ...current, constants: list });
  }

  /** Removes all references to a deleted constant from node configs (sourceVariables entries + {{const.NAME}} tokens). */
  stripConstantReferences(name: string): void {
    if (!name) return;
    const key = `const.${name}`;
    const token = new RegExp('\\{\\{\\s*const\\.' + name + '\\s*\\}\\}', 'g');
    this.nodes.update(list =>
      list.map(n => {
        const raw = n.config || '';
        if (!raw.includes(key)) return n;
        let cfg: Record<string, unknown>;
        try { cfg = JSON.parse(raw); } catch { return n; }
        if (Array.isArray(cfg['sourceVariables'])) {
          cfg['sourceVariables'] = (cfg['sourceVariables'] as string[]).filter(v => v !== key);
        }
        const cleaned = JSON.stringify(cfg).replace(token, '');
        return { ...n, config: cleaned };
      })
    );
  }

  getAvailableVariables(nodeId: string): VariableGroup[] {
    return this.variableGraph.getAvailableVariables(
      nodeId, this.nodes(), this.edges(), this.automation()?.constants ?? [], this.parameterSets());
  }
}
