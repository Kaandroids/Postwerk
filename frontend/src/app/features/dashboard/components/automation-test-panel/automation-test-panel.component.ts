import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import DOMPurify from 'dompurify';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AutomationTestService, AutomationTestCaseRequest } from '../../../../core/services/automation-test.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import {
  AutomationTestCase,
  AutomationTestResult,
  TestEmailInput,
  TestAttachment,
  TestAssertion,
  NodeTraceResult,
  RunAllTestsResponse,
} from '../../../../models/automation-test.model';
import { v } from '../../../../shared/utils/event.util';
import { humanizeError } from '../../../../shared/utils/error.util';
import { getNodeColor, getNodeIcon, TriggerMode } from '../../../../models/automation.model';

interface FlowNodeParamSet {
  id: string;
  name: string;
  parameters: string[];
}

interface FlowNodeInfo {
  id: string;
  nodeType: string;
  label: string;
  parameterSets?: FlowNodeParamSet[];
  categories?: { name: string; color: string }[];
  /** INPUT/OUTPUT exposed field names (parameter-set / output-mapping keys). */
  fields?: string[];
  /** INTEGRATION_CALL: referenced integration display name. */
  integration?: string;
  /** INTEGRATION_CALL: cached output field names for assertion field dropdowns. */
  outputFields?: string[];
}

interface CategoryOption {
  id: string;
  name: string;
  color: string;
}

interface TraceDetailItem {
  label: string;
  value: string;
}

interface IntegrationInfo {
  name: string;
  inputCount: number;
  outputCount: number;
  outputs: { key: string; value: string }[];
  mocked: boolean;
  error: string | null;
}

/** Status options per node type — includes the integration node types (INPUT / OUTPUT / INTEGRATION_CALL). */
const STATUS_BY_TYPE: Record<string, string[]> = {
  TRIGGER: ['PASSED', 'SKIPPED'],
  FILTER: ['MATCHED', 'NOT_MATCHED'],
  EXTRACT: ['EXTRACTED', 'SKIPPED'],
  CATEGORIZE: ['CATEGORIZED', 'SKIPPED'],
  DELAY: ['SIMULATED', 'SKIPPED'],
  LABEL: ['EXECUTED', 'SIMULATED', 'SKIPPED'],
  EMAIL_ACTION: ['EXECUTED', 'SIMULATED', 'SKIPPED'],
  REMOVE_LABEL: ['EXECUTED', 'SIMULATED', 'SKIPPED'],
  WEBHOOK: ['EXECUTED', 'SIMULATED', 'ERROR', 'SKIPPED'],
  SEND_EMAIL: ['EXECUTED', 'SIMULATED', 'ERROR', 'SKIPPED'],
  INPUT: ['PASSED', 'ERROR'],
  OUTPUT: ['PASSED', 'SKIPPED', 'ERROR'],
  INTEGRATION_CALL: ['EXECUTED', 'SIMULATED', 'ERROR', 'SKIPPED'],
  VECTOR_SEARCH: ['MATCHED', 'NOT_MATCHED', 'ERROR'],
  NOTIFY: ['SIMULATED', 'ERROR'],
  FOREACH: ['PASSED', 'SKIPPED'],
};

/** Maps a node trace result status to its theme color variable. */
const STATUS_COLORS: Record<string, string> = {
  PASSED: 'var(--success)', MATCHED: 'var(--success)', CATEGORIZED: 'var(--success)',
  EXTRACTED: 'var(--success)', EXECUTED: 'var(--success)',
  FAILED: 'var(--danger)', NOT_MATCHED: 'var(--danger)', ERROR: 'var(--danger)',
  SIMULATED: 'var(--accent)',
  SKIPPED: 'var(--warning)',
};

type TestMode = 'setup' | 'result';

/** Tests surface: a rail of saved test cases plus a setup/result main column with per-node dry-run traces. */
@Component({
  selector: 'app-automation-test-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  templateUrl: './automation-test-panel.component.html',
  styleUrl: './automation-test-panel.component.scss',
})
export class AutomationTestPanelComponent {
  protected i18n = inject(I18nService);
  private fmt = inject(FormatService);
  private testService = inject(AutomationTestService);
  private destroyRef = inject(DestroyRef);
  private sanitizer = inject(DomSanitizer);

  automationId = input.required<string>();
  nodes = input<FlowNodeInfo[]>([]);
  allCategories = input<CategoryOption[]>([]);
  /** True when the edited automation is an INTEGRATION — the mock-email panel becomes a seeded INPUT-values panel. */
  isIntegration = input<boolean>(false);
  /** The automation's TRIGGER mode — WEBHOOK swaps the mock-email panel for a `trigger.*` payload editor. */
  triggerMode = input<TriggerMode>('EMAIL');

  /** True when the trigger is an inbound webhook (no email; the run is seeded from a JSON payload). */
  isWebhook = computed(() => !this.isIntegration() && this.triggerMode() === 'WEBHOOK');

  testCases = signal<AutomationTestCase[]>([]);
  selectedTest = signal<AutomationTestCase | null>(null);
  testResult = signal<AutomationTestResult | null>(null);
  testMode = signal<TestMode>('setup');
  creating = signal(false);
  running = signal(false);
  runningAll = signal(false);
  loading = signal(false);
  error = signal('');
  runAllSummary = signal<{ passed: number; total: number } | null>(null);

  /** Trace timeline: which node-detail rows are expanded. */
  openSteps = signal<Set<string>>(new Set());
  /** Toggles the staggered entrance animation right after a run. */
  staggerOn = signal(false);

  // Setup form state
  formName = signal('');
  formDescription = signal('');
  formFrom = signal('');
  formTo = signal('');
  formSubject = signal('');
  formBody = signal('');
  formReceivedAt = signal('');
  formInReplyTo = signal('');
  formCategoryIds = signal<string[]>([]);
  /** Mock attachments (name/contentType/size) seeded onto the test email — drives FOREACH over email.attachments. */
  formAttachments = signal<TestAttachment[]>([]);
  formInputFields = signal<Record<string, string>>({});
  /** Webhook trigger payload as editable key/value rows (→ `trigger.*` variables). */
  formTriggerPayload = signal<{ key: string; value: string }[]>([]);
  formAssertions = signal<TestAssertion[]>([]);
  formAssertionParamSetIds = signal<(string | null)[]>([]);
  editingTestId = signal<string | null>(null);

  nodeOptions = computed(() => this.nodes().map(n => ({ id: n.id, label: `${n.label} (${n.nodeType})`, type: n.nodeType })));

  /** The INPUT node's seeded field names (integration tests). */
  inputFieldNames = computed(() => this.nodes().find(n => n.nodeType === 'INPUT')?.fields || []);

  /** Whether a main column should render (a test is selected or a new one is being created). */
  hasActive = computed(() => this.creating() || this.selectedTest() !== null);

  constructor() {
    effect(() => {
      const id = this.automationId();
      if (id) {
        this.loadTests();
      }
    });
  }

  loadTests(): void {
    this.error.set('');
    this.loading.set(true);
    this.testService.getTests(this.automationId()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: tests => {
        this.testCases.set(tests);
        this.loading.set(false);
        // Keep the selected card in sync with the freshly loaded list.
        const sel = this.selectedTest();
        if (sel) {
          const fresh = tests.find(t => t.id === sel.id);
          if (fresh) this.selectedTest.set(fresh);
        } else if (!this.creating() && tests.length > 0) {
          this.openTest(tests[0]);
        }
      },
      error: () => { this.loading.set(false); this.error.set(this.i18n.t('auto_test_error')); },
    });
  }

  // ── Status / node-type helpers ────────────────────────────
  getStatusOptions(nodeId: string): string[] {
    const node = this.nodes().find(n => n.id === nodeId);
    return STATUS_BY_TYPE[node?.nodeType || ''] || ['PASSED', 'SKIPPED'];
  }
  getNodeType(nodeId: string): string {
    return this.nodes().find(n => n.id === nodeId)?.nodeType || '';
  }
  getParamSets(nodeId: string): FlowNodeParamSet[] {
    return this.nodes().find(n => n.id === nodeId)?.parameterSets || [];
  }
  getParamNames(nodeId: string, paramSetId: string): string[] {
    return this.getParamSets(nodeId).find(p => p.id === paramSetId)?.parameters || [];
  }
  getCategoryNames(nodeId: string): string[] {
    return this.nodes().find(n => n.id === nodeId)?.categories?.map(c => c.name) || [];
  }
  /** Field names a node exposes for `+ Feld-Prüfung` (INPUT/OUTPUT param-set keys, INTEGRATION_CALL output fields). */
  getNodeFields(nodeId: string): string[] {
    return this.nodes().find(n => n.id === nodeId)?.fields
      || this.nodes().find(n => n.id === nodeId)?.outputFields || [];
  }

  // ── Rail interactions ─────────────────────────────────────
  openTest(tc: AutomationTestCase): void {
    this.error.set('');
    this.creating.set(false);
    this.selectedTest.set(tc);
    this.editingTestId.set(tc.id);
    this.loadFormFrom(tc);
    this.testResult.set(null);
    if (tc.lastResult) {
      this.setTestMode('result');
    } else {
      this.testMode.set('setup');
    }
  }

  showCreateForm(): void {
    this.editingTestId.set(null);
    this.selectedTest.set(null);
    this.creating.set(true);
    this.testResult.set(null);
    this.formName.set('');
    this.formDescription.set('');
    this.formFrom.set('');
    this.formTo.set('');
    this.formSubject.set('');
    this.formBody.set('');
    this.formReceivedAt.set('');
    this.formInReplyTo.set('');
    this.formCategoryIds.set([]);
    this.formAttachments.set([]);
    this.formInputFields.set({});
    this.formTriggerPayload.set([]);
    this.formAssertions.set([]);
    this.formAssertionParamSetIds.set([]);
    this.testMode.set('setup');
  }

  // ── Mock attachment rows (FOREACH over email.attachments) ──────
  addAttachment(): void {
    this.formAttachments.update(list => [...list, { name: '', contentType: 'application/pdf', size: 0 }]);
  }

  removeAttachment(index: number): void {
    this.formAttachments.update(list => list.filter((_, i) => i !== index));
  }

  updateAttachment(index: number, field: keyof TestAttachment, value: string): void {
    this.formAttachments.update(list => list.map((att, i) =>
      i === index ? { ...att, [field]: field === 'size' ? (Number(value) || 0) : value } : att));
  }

  private loadFormFrom(tc: AutomationTestCase): void {
    this.formName.set(tc.name);
    this.formDescription.set(tc.description || '');
    this.formFrom.set(tc.emailInput.from);
    this.formTo.set(tc.emailInput.to);
    this.formSubject.set(tc.emailInput.subject);
    this.formBody.set(tc.emailInput.body);
    this.formReceivedAt.set(tc.emailInput.receivedAt || '');
    this.formInReplyTo.set(tc.emailInput.inReplyTo || '');
    this.formCategoryIds.set(tc.emailInput.categoryIds ? [...tc.emailInput.categoryIds] : []);
    this.formAttachments.set(tc.emailInput.attachments ? tc.emailInput.attachments.map(a => ({ ...a })) : []);
    const seeded: Record<string, string> = {};
    const inFields = tc.emailInput.inputFields || {};
    for (const f of this.inputFieldNames()) seeded[f] = String(inFields[f] ?? '');
    this.formInputFields.set(seeded);
    const payload = tc.emailInput.triggerPayload || {};
    this.formTriggerPayload.set(Object.entries(payload).map(([key, value]) => ({ key, value: String(value ?? '') })));
    this.formAssertions.set([...tc.assertions]);
    this.formAssertionParamSetIds.set(tc.assertions.map(a => {
      if (!a.field) return null;
      const node = this.nodes().find(n => n.id === a.nodeId);
      if (!node?.parameterSets) return null;
      return node.parameterSets.find(ps => ps.parameters.includes(a.field!))?.id || null;
    }));
  }

  setTestMode(mode: TestMode): void {
    this.testMode.set(mode);
    this.staggerOn.set(false);
    if (mode === 'result' && !this.testResult()) {
      const tc = this.selectedTest();
      if (tc?.lastResult) this.fetchLatestResult(tc);
    }
  }

  private fetchLatestResult(tc: AutomationTestCase): void {
    this.loading.set(true);
    this.testService.getLatestResult(this.automationId(), tc.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => { this.testResult.set(result); this.loading.set(false); },
        error: () => { this.loading.set(false); },
      });
  }

  // ── Setup form (assertions) ───────────────────────────────
  addAssertion(): void {
    const firstNode = this.nodes()[0];
    const statuses = STATUS_BY_TYPE[firstNode?.nodeType || ''] || ['PASSED'];
    this.formAssertions.update(list => [...list, {
      nodeId: firstNode?.id || '',
      expectedStatus: statuses[0],
      field: null,
      expectedValue: null,
    }]);
    this.formAssertionParamSetIds.update(list => [...list, null]);
  }
  removeAssertion(index: number): void {
    this.formAssertions.update(list => list.filter((_, i) => i !== index));
    this.formAssertionParamSetIds.update(list => list.filter((_, i) => i !== index));
  }
  updateAssertionNode(index: number, nodeId: string): void {
    const statuses = STATUS_BY_TYPE[this.getNodeType(nodeId)] || ['PASSED'];
    this.formAssertions.update(list => list.map((a, i) =>
      i === index ? { ...a, nodeId, expectedStatus: statuses[0], field: null, expectedValue: null } : a));
    this.formAssertionParamSetIds.update(list => list.map((val, i) => i === index ? null : val));
  }
  updateAssertionStatus(index: number, expectedStatus: string): void {
    this.formAssertions.update(list => list.map((a, i) => i === index ? { ...a, expectedStatus } : a));
  }
  updateAssertionParamSet(index: number, paramSetId: string): void {
    this.formAssertionParamSetIds.update(list => list.map((val, i) => i === index ? (paramSetId || null) : val));
    this.formAssertions.update(list => list.map((a, i) => i === index ? { ...a, field: null, expectedValue: null } : a));
  }
  updateAssertionField(index: number, field: string): void {
    this.formAssertions.update(list => list.map((a, i) => i === index ? { ...a, field: field || null, expectedValue: null } : a));
  }
  updateAssertionValue(index: number, expectedValue: string): void {
    this.formAssertions.update(list => list.map((a, i) => i === index ? { ...a, expectedValue: expectedValue || null } : a));
  }
  toggleCategoryId(categoryId: string): void {
    this.formCategoryIds.update(ids => ids.includes(categoryId) ? ids.filter(id => id !== categoryId) : [...ids, categoryId]);
  }
  updateAssertionCategory(index: number, categoryName: string): void {
    this.formAssertions.update(list => list.map((a, i) =>
      i === index ? { ...a, field: categoryName ? 'categoryName' : null, expectedValue: categoryName || null } : a));
  }
  updateInputField(key: string, value: string): void {
    this.formInputFields.update(m => ({ ...m, [key]: value }));
  }

  // ── Webhook trigger payload rows ──────────────────────────
  addPayloadRow(): void {
    this.formTriggerPayload.update(rows => [...rows, { key: '', value: '' }]);
  }
  removePayloadRow(index: number): void {
    this.formTriggerPayload.update(rows => rows.filter((_, i) => i !== index));
  }
  updatePayloadKey(index: number, key: string): void {
    this.formTriggerPayload.update(rows => rows.map((r, i) => i === index ? { ...r, key } : r));
  }
  updatePayloadValue(index: number, value: string): void {
    this.formTriggerPayload.update(rows => rows.map((r, i) => i === index ? { ...r, value } : r));
  }
  /** Build the trigger payload object from the editable rows (drops blank keys). */
  private buildTriggerPayload(): Record<string, unknown> {
    const obj: Record<string, unknown> = {};
    for (const row of this.formTriggerPayload()) {
      const k = row.key.trim();
      if (k) obj[k] = row.value;
    }
    return obj;
  }

  saveTest(): void {
    this.error.set('');
    const inputFields = this.isIntegration() ? this.formInputFields() : null;
    const triggerPayload = this.isWebhook() ? this.buildTriggerPayload() : null;
    const emailInput: TestEmailInput = {
      from: this.formFrom(),
      to: this.formTo(),
      subject: this.formSubject(),
      body: this.formBody(),
      receivedAt: this.formReceivedAt() || null,
      inReplyTo: this.formInReplyTo() || null,
      categoryIds: this.formCategoryIds().length > 0 ? this.formCategoryIds() : null,
      inputFields: inputFields && Object.keys(inputFields).length > 0 ? inputFields : null,
      triggerPayload: triggerPayload && Object.keys(triggerPayload).length > 0 ? triggerPayload : null,
      attachments: this.formAttachments().length > 0 ? this.formAttachments() : null,
    };
    const req: AutomationTestCaseRequest = {
      name: this.formName(),
      description: this.formDescription() || null,
      emailInput,
      assertions: this.formAssertions(),
    };

    const editId = this.editingTestId();
    const obs = editId
      ? this.testService.updateTest(this.automationId(), editId, req)
      : this.testService.createTest(this.automationId(), req);

    obs.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: saved => {
        this.creating.set(false);
        this.editingTestId.set(saved.id);
        this.selectedTest.set(saved);
        this.testResult.set(null);
        this.testMode.set('result');
        this.loadTests();
      },
      error: err => this.error.set(humanizeError(err, 'Save failed')),
    });
  }

  cancelForm(): void {
    this.error.set('');
    this.creating.set(false);
    const sel = this.selectedTest();
    if (sel) this.openTest(sel);
    else if (this.testCases().length > 0) this.openTest(this.testCases()[0]);
  }

  deleteTest(tc: AutomationTestCase): void {
    this.error.set('');
    this.testService.deleteTest(this.automationId(), tc.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        if (this.selectedTest()?.id === tc.id) {
          this.selectedTest.set(null);
          this.testResult.set(null);
        }
        this.loadTests();
      },
      error: err => this.error.set(humanizeError(err, 'Delete failed')),
    });
  }

  // ── Running ───────────────────────────────────────────────
  runSingleTest(tc: AutomationTestCase): void {
    this.error.set('');
    this.running.set(true);
    this.creating.set(false);
    this.selectedTest.set(tc);
    this.editingTestId.set(tc.id);
    this.testService.runTest(this.automationId(), tc.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => {
        this.testResult.set(result);
        this.running.set(false);
        this.openSteps.set(new Set());
        this.testMode.set('result');
        this.staggerOn.set(true);
        this.loadTests();
      },
      error: err => {
        this.running.set(false);
        this.error.set(humanizeError(err, 'Run failed'));
      },
    });
  }

  runActive(): void {
    const tc = this.selectedTest();
    if (tc && !this.creating()) this.runSingleTest(tc);
  }

  runAll(): void {
    this.error.set('');
    this.runningAll.set(true);
    this.testService.runAllTests(this.automationId()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (resp: RunAllTestsResponse) => {
        this.runningAll.set(false);
        this.runAllSummary.set({ passed: resp.passed, total: resp.totalTests });
        this.loadTests();
        if (resp.results.length > 0) {
          const last = resp.results[resp.results.length - 1];
          this.testResult.set(last);
          this.selectedTest.set(this.testCases().find(tc => tc.id === last.testCaseId) || null);
          this.testMode.set('result');
          this.staggerOn.set(true);
        }
      },
      error: err => {
        this.runningAll.set(false);
        this.error.set(humanizeError(err, 'Run all failed'));
      },
    });
  }

  // ── Result rail card helpers ──────────────────────────────
  /** Grade keyword for a card's left accent border (`ok` / `bad` / `never`). */
  cardGrade(tc: AutomationTestCase): 'ok' | 'bad' | 'never' {
    if (!tc.lastResult) return 'never';
    return tc.lastResult.status === 'PASSED' ? 'ok' : 'bad';
  }
  cardResultLine(tc: AutomationTestCase): string {
    const r = tc.lastResult;
    if (!r) return this.i18n.t('auto_test_not_run');
    if (r.status === 'PASSED') {
      return this.i18n.t('auto_test_assertions_passed', { passed: String(r.passedCount), total: String(r.totalCount) });
    }
    const failed = r.totalCount - r.passedCount;
    return this.i18n.t('auto_test_n_failed', { failed: String(failed), total: String(r.totalCount) });
  }
  cardMeta(tc: AutomationTestCase): string {
    const r = tc.lastResult;
    if (!r) return tc.emailInput.from || '';
    return `${this.formatTimestamp(r.executedAt)} · ${(r.durationMs / 1000).toFixed(2)} s`;
  }

  // ── Trace / result helpers ────────────────────────────────
  isStepOpen(nodeId: string): boolean { return this.openSteps().has(nodeId); }
  toggleStep(nodeId: string): void {
    this.openSteps.update(s => {
      const next = new Set(s);
      next.has(nodeId) ? next.delete(nodeId) : next.add(nodeId);
      return next;
    });
  }
  stepHasDetail(node: NodeTraceResult): boolean {
    return this.formatNodeDetails(node).length > 0
      || this.getIntegrationInfo(node) !== null
      || node.nodeType === 'OUTPUT';
  }

  readonly getNodeIcon = getNodeIcon;
  readonly getNodeColor = getNodeColor;
  getStatusColor(status: string): string { return STATUS_COLORS[status] ?? 'var(--fg-muted)'; }
  statusKind(status: string): 'ok' | 'bad' | 'sim' | 'mut' {
    if (status === 'SIMULATED') return 'sim';
    if (status === 'SKIPPED') return 'mut';
    if (STATUS_COLORS[status] === 'var(--danger)' || status === 'FAILED' || status === 'ERROR' || status === 'NOT_MATCHED') return 'bad';
    return 'ok';
  }

  /** INTEGRATION_CALL trace detail (name, input/output counts, returned map). Null for other types. */
  getIntegrationInfo(node: NodeTraceResult): IntegrationInfo | null {
    if (node.nodeType !== 'INTEGRATION_CALL') return null;
    const d = node.resultDetail || {};
    const output = (d['output'] as Record<string, unknown>) || {};
    const outputs = Object.entries(output).map(([key, val]) => ({ key, value: String(val ?? '') }));
    return {
      name: String(d['integration'] ?? '—'),
      inputCount: Number(d['inputFields'] ?? 0),
      outputCount: outputs.length,
      outputs,
      mocked: d['mocked'] === true,
      error: d['error'] ? String(d['error']) : null,
    };
  }

  /** Human-readable detail rows for a node trace result. */
  formatNodeDetails(node: NodeTraceResult): TraceDetailItem[] {
    const d = node.resultDetail;
    if (!d || Object.keys(d).length === 0) return [];
    const items: TraceDetailItem[] = [];

    switch (node.nodeType) {
      case 'FILTER':
        if (d['matched'] !== undefined) {
          items.push({
            label: this.i18n.t('auto_test_result_matched').split(' ')[0],
            value: d['matched'] ? this.i18n.t('auto_test_result_matched') : this.i18n.t('auto_test_result_not_matched'),
          });
        }
        break;

      case 'EXTRACT': {
        const extracted = d['extractedValues'] as Record<string, Record<string, unknown>> | undefined;
        if (extracted && typeof extracted === 'object') {
          for (const fields of Object.values(extracted)) {
            if (fields && typeof fields === 'object') {
              for (const [k, val] of Object.entries(fields)) {
                if (k.startsWith('_')) continue;
                items.push({ label: k, value: String(val ?? '') });
              }
            }
          }
        }
        break;
      }

      case 'CATEGORIZE':
        if (d['categoryName']) items.push({ label: this.i18n.t('auto_test_category_label'), value: String(d['categoryName']) });
        if (d['confidence'] !== undefined) items.push({ label: this.i18n.t('auto_test_confidence_label'), value: `${Number(d['confidence'])}%` });
        break;

      case 'FOREACH':
        if (d['source']) items.push({ label: this.i18n.t('auto_foreach_source'), value: String(d['source']) });
        if (d['count'] !== undefined) items.push({ label: this.i18n.t('auto_var_foreach_count'), value: String(d['count']) });
        if (d['alias']) items.push({ label: this.i18n.t('auto_foreach_alias'), value: String(d['alias']) });
        if (d['truncatedFrom'] !== undefined) items.push({ label: '', value: `(${d['truncatedFrom']} → ${d['count']})` });
        break;

      case 'VECTOR_SEARCH': {
        if (d['confidence'] !== undefined) items.push({ label: this.i18n.t('auto_test_confidence_label'), value: `${Number(d['confidence'])}%` });
        if (d['reason']) items.push({ label: this.i18n.t('auto_test_reason_label'), value: String(d['reason']) });
        const match = d['match'] as Record<string, unknown> | undefined;
        if (match && typeof match === 'object') {
          for (const [k, val] of Object.entries(match)) {
            items.push({ label: k, value: String(val ?? '') });
          }
        }
        break;
      }

      case 'EMAIL_ACTION': {
        const actionMode = d['actionMode'] as string;
        if (actionMode === 'FORWARD') {
          if (d['toAddress']) items.push({ label: 'To', value: String(d['toAddress']) });
          if (d['templateName']) items.push({ label: 'Template', value: String(d['templateName']) });
        } else if (actionMode === 'MOVE_FOLDER') {
          if (d['folder']) items.push({ label: this.i18n.t('auto_move_folder'), value: String(d['folder']) });
          if (d['isTrash']) items.push({ label: '', value: '(Trash)' });
        } else if (d['templateName']) {
          items.push({ label: 'Template', value: String(d['templateName']) });
        }
        if (d['reason'] === 'dry-run') items.push({ label: '', value: `(${this.i18n.t('auto_test_dry_run_label')})` });
        break;
      }

      case 'REMOVE_LABEL':
        if (d['categoryName']) items.push({ label: this.i18n.t('auto_remove_label_category'), value: String(d['categoryName']) });
        if (d['removed'] !== undefined) items.push({ label: '', value: d['removed'] ? '✓' : '–' });
        break;

      case 'DELAY':
        if (d['delayMinutes'] !== undefined) items.push({ label: this.i18n.t('auto_delay_minutes'), value: `${d['delayMinutes']} min` });
        if (d['delayedUntil']) items.push({ label: this.i18n.t('auto_port_delayed'), value: String(d['delayedUntil']) });
        break;

      case 'LABEL':
        if (d['categoryName']) items.push({ label: this.i18n.t('auto_label_category'), value: String(d['categoryName']) });
        break;

      case 'WEBHOOK':
        if (d['method']) items.push({ label: this.i18n.t('auto_webhook_method'), value: String(d['method']) });
        if (d['url']) items.push({ label: this.i18n.t('auto_webhook_url'), value: String(d['url']) });
        if (d['statusCode'] !== undefined) items.push({ label: 'Status', value: String(d['statusCode']) });
        if (d['reason'] === 'dry-run') items.push({ label: '', value: `(${this.i18n.t('auto_test_dry_run_label')})` });
        if (d['error']) items.push({ label: 'Error', value: String(d['error']) });
        break;

      case 'SEND_EMAIL':
        if (d['to']) items.push({ label: 'To', value: String(d['to']) });
        if (d['subject']) items.push({ label: 'Subject', value: String(d['subject']) });
        if (d['reason'] === 'dry-run') items.push({ label: '', value: `(${this.i18n.t('auto_test_dry_run_label')})` });
        if (d['error']) items.push({ label: 'Error', value: String(d['error']) });
        break;

      case 'INPUT':
        if (d['inputFields'] !== undefined) {
          items.push({ label: this.i18n.t('auto_test_input_fields'), value: this.i18n.t('auto_test_n_set', { n: String(d['inputFields']) }) });
        }
        break;

      case 'OUTPUT': {
        const returned = (d['returned'] as Record<string, unknown>) || {};
        for (const [k, val] of Object.entries(returned)) items.push({ label: k, value: String(val ?? '') });
        break;
      }

      case 'NOTIFY':
        if (d['recipientCount'] !== undefined) items.push({ label: this.i18n.t('auto_notify_recipient_count'), value: String(d['recipientCount']) });
        if (d['title']) items.push({ label: this.i18n.t('auto_notify_title'), value: String(d['title']) });
        if (d['message']) items.push({ label: this.i18n.t('auto_notify_message'), value: String(d['message']) });
        if (d['reason'] === 'dry-run') items.push({ label: '', value: `(${this.i18n.t('auto_test_dry_run_label')})` });
        if (d['error']) items.push({ label: 'Error', value: String(d['error']) });
        break;
    }
    return items;
  }

  getStatusSummary(result: AutomationTestResult): string {
    const passed = result.assertionResults.filter(a => a.passed).length;
    const total = result.assertionResults.length;
    if (result.status === 'ERROR') return this.i18n.t('auto_test_error_msg', { message: result.errorMessage || '' });
    if (result.status === 'PASSED') return this.i18n.t('auto_test_assertions_passed', { passed: String(passed), total: String(total) });
    return this.i18n.t('auto_test_assertions_failed', { passed: String(passed), total: String(total) });
  }
  resultPassedCount(result: AutomationTestResult): number { return result.assertionResults.filter(a => a.passed).length; }

  getAssertionNodeLabel(assertionIndex: number): string {
    const tc = this.selectedTest();
    if (!tc || !tc.assertions[assertionIndex]) return `#${assertionIndex + 1}`;
    const node = this.nodes().find(n => n.id === tc.assertions[assertionIndex].nodeId);
    return node?.label || `#${assertionIndex + 1}`;
  }

  formatTimestamp(iso: string): string {
    if (!iso) return '';
    return this.fmt.dateTime(iso);
  }

  sanitizeHtml(html: string): SafeHtml {
    const clean = DOMPurify.sanitize(html, { ALLOWED_TAGS: ['b', 'i', 'em', 'strong', 'br', 'p', 'span', 'div', 'ul', 'ol', 'li', 'a', 'table', 'tr', 'td', 'th', 'thead', 'tbody'], ALLOWED_ATTR: ['href', 'target', 'class', 'style'] });
    return this.sanitizer.bypassSecurityTrustHtml(clean);
  }

  readonly v = v;
}
