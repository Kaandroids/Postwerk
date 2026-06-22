import {
  AfterViewInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  HostListener,
  NgZone,
  OnDestroy,
  ViewChild,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import {
  FFlowModule,
  FFlowComponent,
  FCanvasComponent,
  FCanvasChangeEvent,
  FCreateConnectionEvent,
  FMoveNodesEvent,
  FSelectionChangeEvent,
  EFConnectionType,
} from '@foblex/flow';
import { IPoint } from '@foblex/2d';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { LangSwitcherComponent } from '../../../../shared/components/lang-switcher/lang-switcher.component';
import { ThemeToggleComponent } from '../../../../shared/components/theme-toggle/theme-toggle.component';
import { I18nService } from '../../../../core/services/i18n.service';
import { WizardService } from '../../services/wizard.service';
import { Category } from '../../../../models/category.model';
import {
  WizDemoRun,
  WizDemoStop,
  WizardFlowEdge,
} from '../../models/wizard.model';
import {
  NodeType,
  TriggerMode,
  FilterCheck,
  ExtractionEntry,
  getNodeColor,
  getNodeIcon,
  NODE_PALETTE,
  PALETTE_GROUPS,
  NodePaletteItem,
} from '../../../../models/automation.model';
import { v } from '../../../../shared/utils/event.util';
import {
  nodeTypeLabel,
  triggerMode as triggerModeUtil,
  scheduleDisplayText,
  categoryEntries,
  delayMinutes,
  labelCategoryName,
  removeLabelCategoryName,
  webhookMethod,
  filterChecks,
  extractions as extractionsUtil,
} from '../../../dashboard/components/automation-editor/node-config.util';

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
 * Interactive Foblex Flow canvas for the public onboarding wizard. Renders the
 * AI-built automation plan (from {@code WizardService.automationPlan}) using the
 * same node cards / ports / bezier connections as the dashboard automation
 * editor, with drag, zoom, pan and connection editing enabled — and layers the
 * wizard's build/run/ready animation timeline on top.
 *
 * <p>The animation overlay (packet, trail, stamps, reply bubble) lives as a
 * SIBLING of {@code <f-flow>} and is kept aligned with the Foblex canvas by
 * mirroring its transform: {@code matrix(scale,0,0,scale,x,y)} with
 * {@code transform-origin:0 0}, updated from every {@code fCanvasChange} event
 * (whose position/scale exactly match the canvas matrix). Node-card effects
 * (reveal, active glow, classify hit, extract reveal, done badge) are driven by
 * {@link WizardService} animation signals.</p>
 *
 * <p>Resource names (categories) are resolved from {@code toolCallHistory}
 * because the wizard is public and cannot call the authenticated resource APIs.</p>
 */
@Component({
  selector: 'app-wizard-flow-canvas',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FFlowModule, IconComponent, LangSwitcherComponent, ThemeToggleComponent],
  templateUrl: './wizard-flow-canvas.component.html',
  styleUrls: [
    './wizard-flow-canvas.component.scss',
    '../../../dashboard/components/automation-editor/editor-chrome.scss',
    '../../../dashboard/components/automation-editor/editor-nodes.scss',
  ],
})
export class WizardFlowCanvasComponent implements AfterViewInit, OnDestroy {
  protected readonly i18n = inject(I18nService);
  protected readonly wizard = inject(WizardService);
  private readonly elRef = inject(ElementRef);
  private readonly zone = inject(NgZone);
  private readonly cdr = inject(ChangeDetectorRef);

  @ViewChild(FFlowComponent) fFlow!: FFlowComponent;
  @ViewChild(FCanvasComponent) fCanvas!: FCanvasComponent;
  @ViewChild('packetEl') packetEl?: ElementRef<HTMLElement>;
  @ViewChild('trailEl') trailEl?: ElementRef<HTMLElement>;

  readonly EFConnectionType = EFConnectionType;

  readonly weekDays = [
    { value: 1, labelKey: 'auto_schedule_mon' },
    { value: 2, labelKey: 'auto_schedule_tue' },
    { value: 3, labelKey: 'auto_schedule_wed' },
    { value: 4, labelKey: 'auto_schedule_thu' },
    { value: 5, labelKey: 'auto_schedule_fri' },
    { value: 6, labelKey: 'auto_schedule_sat' },
    { value: 0, labelKey: 'auto_schedule_sun' },
  ];

  // ── Local interactive graph (seeded from the wizard plan) ────
  readonly nodes = signal<FlowNode[]>([]);
  readonly edges = signal<FlowEdge[]>([]);
  private seedSignature = '';

  selectedConnectionIds = signal<string[]>([]);
  zoomLevel = signal(100);
  legendOpen = signal(true);
  tipsOpen = signal(false);

  // ── Tool library rail + tabs (mirrors the editor chrome) ────
  readonly palette = NODE_PALETTE;
  readonly paletteGroups = PALETTE_GROUPS;
  railCollapsed = signal(true);
  librarySearch = signal('');
  libTip = signal<{ name: string; sub: string; top: number; left: number; nc: string } | null>(null);
  /** Canvas is the only active tab; tests/simulations are locked in the public wizard. */
  readonly editorTab = signal<'canvas'>('canvas');

  /** Transient "sign up to edit" hint shown when a guest tries to add a node. */
  readonly signupHint = signal(false);
  private signupHintTimer?: ReturnType<typeof setTimeout>;

  /** AI build narration (thinking indicator) — driven by the wizard timeline. */
  readonly narration = computed(() => {
    const key = this.wizard.narrationKey();
    return key ? this.i18n.t(key) : '';
  });
  readonly isReady = computed(() => this.wizard.narrationKey() === 'wiz_narr_ready');

  // ── Animation overlay transform (mirrors the Foblex canvas matrix) ──
  readonly overlayTransform = signal('matrix(1, 0, 0, 1, 0, 0)');

  // Middle-mouse pan
  panning = signal(false);
  private panStart: IPoint = { x: 0, y: 0 };
  private canvasPosStart: IPoint = { x: 0, y: 0 };
  private currentCanvasPos: IPoint = { x: 0, y: 0 };
  private panSetPosition: IPoint = { x: 0, y: 0 };

  // ── Animation lifecycle bookkeeping ──
  private timelineStarted = false;
  private cancelled = false;
  private timers: ReturnType<typeof setTimeout>[] = [];
  private animFrames: number[] = [];

  /** Plan edges (with source/target node ids + handles) used for graph logic. */
  private readonly planEdges = computed<WizardFlowEdge[]>(
    () => this.wizard.automationPlan()?.edges ?? [],
  );

  /** Automation display name resolved from the create_automation tool call. */
  readonly automationName = computed(() => {
    const calls = this.wizard.toolCallHistory();
    const createAuto = calls.find(c => c.tool === 'create_automation' && c.result);
    if (!createAuto?.result) return '';
    const res = createAuto.result as Record<string, unknown>;
    const d = (res['data'] as Record<string, unknown>) ?? res;
    return (d['name'] as string) ?? '';
  });

  /**
   * Category id → {name,color}, derived from {@code create_category} tool calls
   * (the wizard cannot read the authenticated category API). Exposed as a
   * {@link Category} list so the shared node-config utils can resolve names.
   */
  private readonly categoryList = computed<Category[]>(() => {
    const calls = this.wizard.toolCallHistory();
    const out: Category[] = [];
    for (const call of calls) {
      if (call.tool !== 'create_category' || !call.result) continue;
      const res = call.result as Record<string, unknown>;
      const d = (res['data'] as Record<string, unknown>) ?? res;
      const id = d['id'] as string;
      if (!id) continue;
      out.push({
        id,
        name: (d['name'] as string) || id,
        color: (d['color'] as string) || 'var(--fg-muted)',
      } as Category);
    }
    return out;
  });

  /**
   * Tool-call lookups (categories / parameter sets / templates) used by the
   * demo-run generator to resolve names, extract field counts and reply bodies.
   */
  private readonly lookups = computed(() => {
    const calls = this.wizard.toolCallHistory();
    const categories = new Map<string, { name: string; color: string }>();
    const paramSets = new Map<string, { name: string; parameters: { key: string; type: string }[] }>();
    const templates = new Map<string, { name: string; subject: string; body: string }>();

    for (const call of calls) {
      if (!call.result) continue;
      const res = call.result as Record<string, unknown>;
      const d = (res['data'] as Record<string, unknown>) ?? res;
      const id = d['id'] as string;
      if (!id) continue;

      switch (call.tool) {
        case 'create_category':
          categories.set(id, {
            name: (d['name'] as string) || '',
            color: (d['color'] as string) || 'var(--fg-muted)',
          });
          break;
        case 'create_parameter_set':
          paramSets.set(id, {
            name: (d['name'] as string) || '',
            parameters: (d['parameters'] as { key: string; type: string }[]) || [],
          });
          break;
        case 'create_template':
          templates.set(id, {
            name: (d['name'] as string) || '',
            subject: (d['subject'] as string) || '',
            body: (d['body'] as string) || '',
          });
          break;
      }
    }
    return { categories, paramSets, templates };
  });

  /** Floating reply bubble anchored to the active EMAIL_ACTION node (canvas coords). */
  readonly replyBubble = computed(() => {
    const text = this.wizard.replyText();
    const active = this.wizard.activeNodeId();
    if (!text || !active) return null;
    const node = this.nodes().find(n => n.id === active);
    if (!node) return null;
    return { text, x: node.position.x + 218, y: node.position.y + 6 };
  });

  constructor() {
    // Seed / re-seed the interactive graph whenever the plan's node/edge set
    // changes. Drag positions only mutate local state (no signature change), so
    // user edits are preserved across plan-content updates during building.
    effect(() => {
      const plan = this.wizard.automationPlan();
      const planNodes = plan?.nodes ?? [];
      const planEdges = plan?.edges ?? [];
      const sig =
        planNodes.map(n => n.id).join('|') + '##' + planEdges.map(e => e.id).join('|');
      if (sig === this.seedSignature) return;
      this.seedSignature = sig;

      this.nodes.set(planNodes.map(n => ({
        id: n.id,
        nodeType: n.nodeType as NodeType,
        label: n.label || n.nodeType,
        position: { x: n.positionX, y: n.positionY },
        config: n.config,
      })));
      this.edges.set(planEdges.map(e => ({
        id: e.id,
        outputId: `${e.sourceNodeId}_${e.sourceHandle}`,
        inputId: `${e.targetNodeId}_${e.targetHandle}`,
      })));
    });

    // Kick off the animation timeline once nodes first arrive + are laid out.
    effect(() => {
      const ns = this.nodes();
      if (ns.length > 0 && !this.timelineStarted) {
        this.timelineStarted = true;
        const t = setTimeout(() => {
          this.zone.run(() => {
            try { this.fCanvas?.fitToScreen({ x: 120, y: 150 }, false); } catch { /* noop */ }
            this.runTimeline();
          });
        }, 360);
        this.timers.push(t);
      }
    });
  }

  ngAfterViewInit(): void {
    // Foblex emits an initial fCanvasChange after layout; overlayTransform is
    // synced from there. Nothing else required here.
  }

  ngOnDestroy(): void {
    this.cancelled = true;
    this.timers.forEach(t => clearTimeout(t));
    if (this.signupHintTimer) clearTimeout(this.signupHintTimer);
    this.animFrames.forEach(id => cancelAnimationFrame(id));
  }

  // Disable left-click canvas panning — only middle mouse should pan
  disableCanvasDrag = (_event: MouseEvent | TouchEvent) => false;

  // ── Connection events ──────────────────────
  onConnectionCreated(event: FCreateConnectionEvent): void {
    if (!event.targetId) return;
    this.edges.update(list => [...list, {
      id: `edge-${Date.now()}`,
      outputId: event.sourceId,
      inputId: event.targetId!,
    }]);
  }

  onSelectionChanged(event: FSelectionChangeEvent): void {
    this.selectedConnectionIds.set(event.connectionIds);
  }

  onNodeMoved(event: FMoveNodesEvent): void {
    const updates = event.nodes;
    this.nodes.update(list =>
      list.map(n => {
        const moved = updates.find(u => u.id === n.id);
        return moved ? { ...n, position: moved.position } : n;
      })
    );
  }

  @HostListener('window:keydown', ['$event'])
  onKeyDown(event: KeyboardEvent): void {
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

  // ── Middle-mouse pan ────────────────────────
  onCanvasMouseDown(event: MouseEvent): void {
    if (event.button !== 1) return;
    event.preventDefault();
    this.panning.set(true);
    this.panStart = { x: event.clientX, y: event.clientY };
    this.canvasPosStart = { ...this.panSetPosition };
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

  // ── Zoom / focus ────────────────────────────
  onCanvasChange(event: FCanvasChangeEvent): void {
    this.zoomLevel.set(Math.round(event.scale * 100));
    this.currentCanvasPos = event.position;
    this.panSetPosition = { x: event.position.x, y: event.position.y };
    // Keep the animation overlay aligned with the canvas (same matrix).
    this.overlayTransform.set(
      `matrix(${event.scale}, 0, 0, ${event.scale}, ${event.position.x}, ${event.position.y})`,
    );
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

  focusOnStart(): void {
    if (!this.fCanvas) return;
    this.fCanvas.resetScale();
    const trigger = this.nodes().find(n => n.nodeType === 'TRIGGER') || this.nodes()[0];
    if (trigger) {
      const pos = trigger.position;
      const newPos = { x: -pos.x + 200, y: -pos.y + 200 };
      this.fCanvas._setPosition(newPos);
      this.panSetPosition = newPos;
    }
    this.fCanvas.redrawWithAnimation();
    this.zoomLevel.set(100);
  }

  // ── Read-only node getters (mirror the editor) ──────────────
  readonly getNodeColor = getNodeColor;
  readonly getNodeIcon = getNodeIcon;

  getNodeTypeLabel(type: NodeType): string {
    return nodeTypeLabel(this.i18n, type);
  }

  getOutputId(node: FlowNode, handle: string): string {
    return `${node.id}_${handle}`;
  }

  getInputId(node: FlowNode): string {
    return `${node.id}_input`;
  }

  getTriggerMode(node: FlowNode): TriggerMode {
    return triggerModeUtil(node);
  }

  getTriggerFolder(node: FlowNode): string {
    try {
      return (JSON.parse(node.config || '{}')['folder'] as string) || 'Inbox';
    } catch { return 'Inbox'; }
  }

  getScheduleDisplayText(node: FlowNode): string {
    return scheduleDisplayText(this.i18n, this.weekDays, node);
  }

  getFilterChecks(node: FlowNode): FilterCheck[] {
    return filterChecks(node);
  }

  getExtractions(node: FlowNode): ExtractionEntry[] {
    return extractionsUtil(node);
  }

  getCategoryEntries(node: FlowNode): { categoryId: string; label: string; color: string }[] {
    return categoryEntries(this.categoryList(), node);
  }

  getDelayMinutes(node: FlowNode): number {
    return delayMinutes(node);
  }

  getLabelCategoryName(node: FlowNode): string {
    return labelCategoryName(this.categoryList(), node);
  }

  getRemoveLabelCategoryName(node: FlowNode): string {
    return removeLabelCategoryName(this.categoryList(), node);
  }

  getWebhookMethod(node: FlowNode): string {
    return webhookMethod(node);
  }

  getWebhookUrlPreview(node: FlowNode): string {
    let url = '';
    try { url = (JSON.parse(node.config || '{}')['url'] as string) || ''; } catch { /* ignore */ }
    return url.length > 30 ? url.substring(0, 30) + '...' : url;
  }

  // ── Animation state getters (used by the template) ──────────
  isNodeRevealed(nodeId: string): boolean {
    return this.wizard.revealedNodes().has(nodeId);
  }

  isNodeActive(nodeId: string): boolean {
    return this.wizard.activeNodeId() === nodeId;
  }

  isNodeDone(nodeId: string): boolean {
    return this.wizard.doneNodes().has(nodeId);
  }

  /**
   * Plan edges are revealed progressively during the build animation (after the
   * source/target nodes appear); user-drawn edges (id {@code edge-*}) render
   * immediately. This keeps the "nodes first, then their connections" sequence.
   */
  isEdgeRendered(edge: FlowEdge): boolean {
    return this.wizard.drawnEdges().has(edge.id) || edge.id.startsWith('edge-');
  }

  // ── Tool library rail ───────────────────────
  libraryGroups(): { labelKey: string; items: NodePaletteItem[] }[] {
    const q = this.librarySearch().trim().toLowerCase();
    return this.paletteGroups
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

  /**
   * Guests cannot edit the demo automation — any edit affordance (adding a
   * library block, opening the locked Tests/Simulations tabs) surfaces a
   * transient "sign up to edit" hint instead of performing the action.
   */
  showSignupHint(): void {
    this.hideLibTip();
    this.signupHint.set(true);
    if (this.signupHintTimer) clearTimeout(this.signupHintTimer);
    this.signupHintTimer = setTimeout(() => {
      this.zone.run(() => { this.signupHint.set(false); this.cdr.markForCheck(); });
    }, 3200);
  }

  addNode(_type: NodeType): void {
    this.showSignupHint();
  }

  readonly v = v;

  // ─────────────────────────────────────────────────────────────
  //  Animation timeline (ported from the original wizard canvas)
  // ─────────────────────────────────────────────────────────────

  private sleep(ms: number): Promise<void> {
    return new Promise((resolve, reject) => {
      if (this.cancelled) { reject('cancelled'); return; }
      const t = setTimeout(() => {
        if (this.cancelled) { reject('cancelled'); return; }
        resolve();
      }, ms);
      this.timers.push(t);
    });
  }

  private placePacket(x: number, y: number): void {
    const el = this.packetEl?.nativeElement;
    if (el) { el.style.left = `${x}px`; el.style.top = `${y}px`; }
  }

  private placeTrail(x: number, y: number, opacity: number): void {
    const el = this.trailEl?.nativeElement;
    if (el) { el.style.left = `${x}px`; el.style.top = `${y}px`; el.style.opacity = `${opacity}`; }
  }

  /** Dock point above a node's header (canvas coordinates). */
  private dockOf(nodeId: string): { x: number; y: number } {
    const node = this.nodes().find(n => n.id === nodeId);
    if (!node) return { x: 0, y: 0 };
    return { x: node.position.x + 104, y: node.position.y - 34 };
  }

  private tween(
    from: { x: number; y: number },
    to: { x: number; y: number },
    dur: number,
    ease: (t: number) => number = t => t,
    onFrame?: (x: number, y: number, t: number) => void,
  ): Promise<void> {
    return new Promise((resolve, reject) => {
      if (this.cancelled) { reject('cancelled'); return; }
      const start = performance.now();
      const step = (now: number) => {
        if (this.cancelled) { reject('cancelled'); return; }
        const t = Math.min((now - start) / dur, 1);
        const e = ease(t);
        const x = from.x + (to.x - from.x) * e;
        const y = from.y + (to.y - from.y) * e;
        if (onFrame) onFrame(x, y, t);
        else this.placePacket(x, y);
        if (t < 1) {
          const id = requestAnimationFrame(step);
          this.animFrames.push(id);
        } else {
          resolve();
        }
      };
      const id = requestAnimationFrame(step);
      this.animFrames.push(id);
    });
  }

  private async travel(fromNodeId: string, toNodeId: string, dur = 820): Promise<void> {
    const a = this.dockOf(fromNodeId);
    const b = this.dockOf(toNodeId);
    const easeInOut = (t: number) => t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;
    await this.tween(a, b, dur, easeInOut, (x, y, t) => {
      this.placePacket(x, y);
      const tTrail = Math.max(t - 0.06, 0);
      const et = easeInOut(tTrail);
      const tx = a.x + (b.x - a.x) * et;
      const ty = a.y + (b.y - a.y) * et;
      this.placeTrail(tx, ty, t < 0.9 ? 0.7 : 0.7 * (1 - (t - 0.9) / 0.1));
    });
    this.placeTrail(0, 0, 0);
  }

  private async flyAway(fromNodeId: string): Promise<void> {
    const dock = this.dockOf(fromNodeId);
    const to = { x: dock.x + 26, y: dock.y - 196 };
    const easeIn = (t: number) => t * t;
    await this.tween(dock, to, 820, easeIn, (x, y, t) => {
      this.placePacket(x, y);
      const el = this.packetEl?.nativeElement;
      if (el) el.style.opacity = `${1 - t}`;
    });
  }

  // ── Build order (Kahn's topological sort, left-to-right) ──────
  private computeBuildOrder(): string[] {
    const ns = this.nodes();
    const es = this.planEdges();
    if (ns.length === 0) return [];

    const inDegree = new Map<string, number>();
    const adj = new Map<string, string[]>();
    ns.forEach(n => { inDegree.set(n.id, 0); adj.set(n.id, []); });
    es.forEach(e => {
      adj.get(e.sourceNodeId)?.push(e.targetNodeId);
      inDegree.set(e.targetNodeId, (inDegree.get(e.targetNodeId) || 0) + 1);
    });

    const queue: string[] = [];
    inDegree.forEach((deg, id) => { if (deg === 0) queue.push(id); });

    const posX = (id: string) => this.nodes().find(n => n.id === id)?.position.x ?? 0;
    const order: string[] = [];
    while (queue.length > 0) {
      queue.sort((a, b) => posX(a) - posX(b));
      const id = queue.shift()!;
      order.push(id);
      for (const next of adj.get(id) || []) {
        const deg = (inDegree.get(next) || 1) - 1;
        inDegree.set(next, deg);
        if (deg === 0) queue.push(next);
      }
    }
    return order;
  }

  // ── Demo run computation ──────────────────────────────────────
  private static readonly DEMO_SENDERS = [
    { from: 'M. Brandt', initials: 'MB', avatar: '#8b5cf6' },
    { from: 'L. Sommer', initials: 'LS', avatar: '#3e8e62' },
  ];

  private computeDemoRuns(): WizDemoRun[] {
    const ns = this.nodes();
    const es = this.planEdges();
    if (ns.length === 0) return [];

    const nodeMap = new Map(ns.map(n => [n.id, n]));
    const adj = new Map<string, { edgeId: string; targetNodeId: string }[]>();
    ns.forEach(n => adj.set(n.id, []));
    es.forEach(e => adj.get(e.sourceNodeId)?.push({ edgeId: e.id, targetNodeId: e.targetNodeId }));

    const inDegree = new Map<string, number>();
    ns.forEach(n => inDegree.set(n.id, 0));
    es.forEach(e => inDegree.set(e.targetNodeId, (inDegree.get(e.targetNodeId) || 0) + 1));
    const trigger = ns.find(n => (inDegree.get(n.id) || 0) === 0) || ns[0];

    const allPaths: { nodeIds: string[]; edgeIds: string[] }[] = [];
    const dfs = (nodeId: string, pathNodes: string[], pathEdges: string[]) => {
      pathNodes.push(nodeId);
      const neighbors = adj.get(nodeId) || [];
      if (neighbors.length === 0) {
        allPaths.push({ nodeIds: [...pathNodes], edgeIds: [...pathEdges] });
      } else {
        for (const { edgeId, targetNodeId } of neighbors) {
          pathEdges.push(edgeId);
          dfs(targetNodeId, pathNodes, pathEdges);
          pathEdges.pop();
        }
      }
      pathNodes.pop();
    };
    dfs(trigger.id, [], []);

    const paths = allPaths.slice(0, 2);
    const senders = WizardFlowCanvasComponent.DEMO_SENDERS;

    return paths.map((path, idx) => {
      const sender = senders[idx % senders.length];
      const stops = this.generateStops(path.nodeIds, nodeMap, idx);
      const subj = this.i18n.t(idx === 0 ? 'wiz_run_subj_1' : 'wiz_run_subj_2');
      return {
        id: `run-${idx}`,
        from: sender.from,
        subj,
        avatar: sender.avatar,
        initials: sender.initials,
        edgePath: path.edgeIds,
        stops,
      };
    });
  }

  private extractRowCount(node: FlowNode): number {
    const rows = this.getExtractions(node).length;
    return rows > 0 ? rows : 2;
  }

  private generateStops(
    pathNodeIds: string[],
    nodeMap: Map<string, FlowNode>,
    runIdx: number,
  ): WizDemoStop[] {
    const es = this.planEdges();
    const lk = this.lookups();
    const stops: WizDemoStop[] = [];
    const lastIdx = pathNodeIds.length - 1;
    const isLastAction = (i: number, ntype: string) =>
      i === lastIdx && ['EMAIL_ACTION', 'LABEL', 'WEBHOOK'].includes(ntype);

    for (let i = 0; i < pathNodeIds.length; i++) {
      const nodeId = pathNodeIds[i];
      const node = nodeMap.get(nodeId);
      if (!node) continue;

      let cfg: Record<string, unknown> = {};
      try { cfg = JSON.parse(node.config || '{}'); } catch { /* empty */ }

      const stop: WizDemoStop = { nodeId, caption: '', captionColor: '' };

      switch (node.nodeType) {
        case 'TRIGGER':
          stop.caption = this.i18n.t('wiz_run_arrive');
          stop.captionColor = 'var(--c-trigger)';
          break;

        case 'FILTER': {
          const checks = cfg['checks'] as { label: string }[] | undefined;
          if (checks && checks.length > 0) {
            const check = checks[runIdx % checks.length];
            stop.caption = this.i18n.t('wiz_run_filter_match', { name: check?.label || node.label });
          } else {
            stop.caption = this.i18n.t('wiz_run_filter_pass');
          }
          stop.captionColor = 'var(--c-filter)';
          break;
        }

        case 'CATEGORIZE': {
          const catIds = cfg['categoryIds'] as string[] | undefined;
          const hitIdx = runIdx % Math.max(catIds?.length || 1, 1);
          const hitId = catIds?.[hitIdx];
          const cat = hitId ? lk.categories.get(hitId) : null;
          const threshold = (cfg['threshold'] as number) || 70;
          const conf = `${threshold + Math.floor(Math.random() * 20)}%`;
          stop.caption = this.i18n.t('wiz_run_classify', { name: cat?.name || 'Category', conf });
          stop.captionColor = 'var(--c-classify)';
          stop.classify = true;
          stop.classifyHitId = hitId || undefined;
          break;
        }

        case 'EXTRACT': {
          const fieldCount = this.extractRowCount(node);
          stop.caption = this.i18n.t('wiz_run_extract', { count: `${fieldCount}` });
          stop.captionColor = 'var(--c-extract)';
          stop.extract = true;
          stop.extractFieldCount = fieldCount;

          // Side edge to a LABEL node not already on the main path.
          const outgoing = es.filter(e => e.sourceNodeId === nodeId);
          for (const edge of outgoing) {
            const tgt = nodeMap.get(edge.targetNodeId);
            if (tgt && tgt.nodeType === 'LABEL' && !pathNodeIds.includes(tgt.id)) {
              const labelName = (() => {
                try { return (JSON.parse(tgt.config || '{}')['labelName'] as string) || tgt.label; }
                catch { return tgt.label; }
              })();
              stop.sideEdge = edge.id;
              stop.sideNodeId = tgt.id;
              stop.sideCaption = this.i18n.t('wiz_run_label', { name: labelName });
              stop.sideCaptionColor = 'var(--c-action)';
              break;
            }
          }
          break;
        }

        case 'EMAIL_ACTION': {
          const actionMode = (cfg['actionMode'] as string) || 'REPLY';
          if (actionMode === 'REPLY') {
            const templateId = cfg['templateId'] as string | undefined;
            const tpl = templateId ? lk.templates.get(templateId) : null;
            stop.caption = this.i18n.t('wiz_run_reply');
            stop.captionColor = 'var(--c-action)';
            stop.reply = true;
            stop.replyFull = tpl?.body?.slice(0, 60) || 'Vielen Dank für Ihre Nachricht…';
            stop.stamp = { key: 'wiz_stamp_sent', color: 'var(--c-action)' };
            stop.badge = true;
            if (isLastAction(i, node.nodeType)) stop.fly = this.i18n.t('wiz_run_fly_sent');
          } else if (actionMode === 'FORWARD') {
            const addr = (cfg['toAddress'] as string) || 'team@…';
            stop.caption = this.i18n.t('wiz_run_forward', { addr });
            stop.captionColor = 'var(--c-action)';
            stop.stamp = { key: 'wiz_stamp_sent', color: 'var(--c-action)' };
            stop.badge = true;
            if (isLastAction(i, node.nodeType)) stop.fly = this.i18n.t('wiz_run_fly_sent');
          } else if (actionMode === 'MOVE_FOLDER') {
            const folder = (cfg['folder'] as string) || node.label;
            stop.caption = this.i18n.t('wiz_run_move', { folder });
            stop.captionColor = 'var(--c-action)';
            stop.badge = true;
            if (isLastAction(i, node.nodeType)) stop.fly = this.i18n.t('wiz_run_fly_done');
          }
          break;
        }

        case 'LABEL': {
          const labelName = (cfg['labelName'] as string) || node.label;
          stop.caption = this.i18n.t('wiz_run_label', { name: labelName });
          stop.captionColor = 'var(--c-action)';
          stop.badge = true;
          if (isLastAction(i, node.nodeType)) stop.fly = this.i18n.t('wiz_run_fly_done');
          break;
        }

        case 'WEBHOOK':
          stop.caption = this.i18n.t('wiz_run_webhook');
          stop.captionColor = 'var(--c-action)';
          stop.badge = true;
          if (isLastAction(i, node.nodeType)) stop.fly = this.i18n.t('wiz_run_fly_done');
          break;

        default:
          stop.caption = node.label;
          stop.captionColor = 'var(--c-action)';
          stop.badge = true;
          if (i === lastIdx) stop.fly = this.i18n.t('wiz_run_fly_done');
          break;
      }

      stops.push(stop);
    }

    return stops;
  }

  // ── Master timeline (async) ───────────────────────────────────
  async runTimeline(): Promise<void> {
    try {
      await this.runTimelineInner();
    } catch (e) {
      if (e !== 'cancelled') throw e;
    }
  }

  private async runTimelineInner(): Promise<void> {
    const order = this.computeBuildOrder();
    const es = this.planEdges();
    if (order.length === 0) return;

    // ── BUILD PHASE ──────────────────────────────────────────
    this.zone.run(() => {
      this.wizard.stageStatus.set('building');
      this.wizard.narrationKey.set('wiz_narr_build');
      this.cdr.markForCheck();
    });

    for (const nodeId of order) {
      this.zone.run(() => {
        this.wizard.revealedNodes.update(set => { const n = new Set(set); n.add(nodeId); return n; });
        this.cdr.markForCheck();
      });
      await this.sleep(200);

      const incomingEdges = es.filter(e => e.targetNodeId === nodeId);
      if (incomingEdges.length > 0) {
        this.zone.run(() => {
          for (const edge of incomingEdges) {
            this.wizard.drawnEdges.update(set => { const n = new Set(set); n.add(edge.id); return n; });
          }
          this.cdr.markForCheck();
        });
      }
      await this.sleep(300);
    }

    this.zone.run(() => {
      this.wizard.narrationKey.set('wiz_narr_connect');
      this.cdr.markForCheck();
    });
    await this.sleep(650);

    // ── RUN PHASE ────────────────────────────────────────────
    this.zone.run(() => {
      this.wizard.stageStatus.set('running');
      this.wizard.narrationKey.set('wiz_narr_test');
      this.cdr.markForCheck();
    });

    const runs = this.computeDemoRuns();

    for (const run of runs) {
      if (this.cancelled) return;

      this.zone.run(() => {
        this.wizard.classifyHit.set(null);
        this.wizard.extractCount.set(0);
        this.wizard.replyText.set('');
        this.wizard.stamps.set([]);
        this.wizard.caption.set(null);
        this.wizard.activeNodeId.set(null);
        this.wizard.packet.set({
          on: false,
          from: run.from,
          subj: run.subj,
          avatar: run.avatar,
          initials: run.initials,
        });
        this.wizard.pktState.set('idle');
        this.cdr.markForCheck();
      });
      await this.sleep(200);

      if (run.stops.length === 0) continue;

      // ── Arrival ──────────────────────────────────────────
      const firstDock = this.dockOf(run.stops[0].nodeId);
      this.placePacket(firstDock.x, firstDock.y - 150);
      this.zone.run(() => {
        this.wizard.packet.update(p => ({ ...p, on: true }));
        this.wizard.pktState.set('arrive');
        this.cdr.markForCheck();
      });

      const easeOutBack = (t: number) => {
        const c = 1.70158;
        return 1 + (t - 1) ** 3 + c * (t - 1) ** 2;
      };
      await this.tween({ x: firstDock.x, y: firstDock.y - 150 }, firstDock, 720, easeOutBack);

      this.zone.run(() => { this.wizard.pktState.set('pulse'); this.cdr.markForCheck(); });
      await this.sleep(160);

      // ── Stops ────────────────────────────────────────────
      for (let si = 0; si < run.stops.length; si++) {
        if (this.cancelled) return;
        const stop = run.stops[si];

        if (si > 0) {
          const edgeId = run.edgePath[si - 1];
          if (edgeId) {
            this.zone.run(() => {
              this.wizard.liveEdges.update(s => { const n = new Set(s); n.add(edgeId); return n; });
              this.cdr.markForCheck();
            });
          }
          await this.travel(run.stops[si - 1].nodeId, stop.nodeId, 820);
          if (edgeId) {
            this.zone.run(() => {
              this.wizard.liveEdges.update(s => { const n = new Set(s); n.delete(edgeId); return n; });
              this.cdr.markForCheck();
            });
          }
        }

        this.zone.run(() => {
          this.wizard.activeNodeId.set(stop.nodeId);
          this.wizard.caption.set({ text: stop.caption, color: stop.captionColor });
          this.cdr.markForCheck();
        });
        await this.sleep(360);

        if (stop.classify && stop.classifyHitId) {
          this.zone.run(() => { this.wizard.classifyHit.set(stop.classifyHitId!); this.cdr.markForCheck(); });
          await this.sleep(900);
        }

        if (stop.extract) {
          this.zone.run(() => { this.wizard.extractCount.set(0); this.cdr.markForCheck(); });
          for (let f = 1; f <= (stop.extractFieldCount || 2); f++) {
            this.zone.run(() => { this.wizard.extractCount.set(f); this.cdr.markForCheck(); });
            await this.sleep(320);
          }
        }

        if (stop.sideEdge && stop.sideNodeId) {
          this.zone.run(() => {
            this.wizard.liveEdges.update(s => { const n = new Set(s); n.add(stop.sideEdge!); return n; });
            this.cdr.markForCheck();
          });
          await this.sleep(160);

          this.zone.run(() => {
            this.wizard.activeNodeId.set(stop.sideNodeId!);
            this.wizard.caption.set({
              text: stop.sideCaption || '',
              color: stop.sideCaptionColor || 'var(--c-action)',
            });
            this.cdr.markForCheck();
          });
          await this.sleep(520);

          this.zone.run(() => {
            this.wizard.doneNodes.update(s => { const n = new Set(s); n.add(stop.sideNodeId!); return n; });
            this.wizard.liveEdges.update(s => { const n = new Set(s); n.delete(stop.sideEdge!); return n; });
            this.cdr.markForCheck();
          });

          this.zone.run(() => {
            this.wizard.activeNodeId.set(stop.nodeId);
            this.wizard.caption.set({ text: stop.caption, color: stop.captionColor });
            this.cdr.markForCheck();
          });
          await this.sleep(240);
        }

        if (stop.reply && stop.replyFull) {
          this.zone.run(() => { this.wizard.replyText.set(''); this.cdr.markForCheck(); });
          const chars = stop.replyFull;
          for (let ci = 0; ci < chars.length; ci++) {
            if (this.cancelled) return;
            this.zone.run(() => { this.wizard.replyText.set(chars.slice(0, ci + 1)); this.cdr.markForCheck(); });
            await this.sleep(14 + Math.random() * 10);
          }
          await this.sleep(360);
        }

        if (stop.stamp) {
          await this.sleep(150);
          const angle = -8 + Math.random() * 16;
          this.zone.run(() => {
            this.wizard.stamps.update(arr => [
              ...arr,
              { id: `s-${si}-${run.id}`, key: stop.stamp!.key, color: stop.stamp!.color, angle },
            ]);
            this.cdr.markForCheck();
          });
          await this.sleep(700);
        }


        const isComplex = stop.classify || stop.extract || stop.reply;
        await this.sleep(isComplex ? 460 : 720);

        this.zone.run(() => {
          this.wizard.doneNodes.update(s => { const n = new Set(s); n.add(stop.nodeId); return n; });
          this.wizard.activeNodeId.set(null);
          this.cdr.markForCheck();
        });

        if (stop.fly) {
          await this.sleep(180);
          this.zone.run(() => {
            this.wizard.caption.set({ text: stop.fly!, color: stop.captionColor });
            this.wizard.pktState.set('fly');
            this.cdr.markForCheck();
          });
          await this.sleep(80);
          await this.flyAway(stop.nodeId);
          this.zone.run(() => {
            this.wizard.packet.update(p => ({ ...p, on: false }));
            this.wizard.pktState.set('idle');
            this.cdr.markForCheck();
          });
          const pktNative = this.packetEl?.nativeElement;
          if (pktNative) pktNative.style.opacity = '';
        }
      }

      this.zone.run(() => {
        this.wizard.caption.set(null);
        this.wizard.classifyHit.set(null);
        this.cdr.markForCheck();
      });
      await this.sleep(820);
    }

    // ── READY PHASE ──────────────────────────────────────────
    this.zone.run(() => {
      this.wizard.stageStatus.set('ready');
      this.wizard.narrationKey.set('wiz_narr_ready');
      this.wizard.showFoot.set(true);
      this.cdr.markForCheck();
    });
  }
}
