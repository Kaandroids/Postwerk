import { inject, Injectable } from '@angular/core';
import { I18nService } from '../../../../core/services/i18n.service';
import { AutomationConstant, AutomationKind } from '../../../../models/automation.model';
import { ParameterSet } from '../../../../models/parameter-set.model';
import { GraphEdge, GraphNode, VariableGraphService } from './variable-graph.service';

/** Severity of a lint issue — only `error` blocks activation/publish. */
export type LintSeverity = 'error' | 'warning';

/** A single semantic problem found in an automation flow, keyed by a shared issue code. */
export interface LintIssue {
  code: string;
  severity: LintSeverity;
  nodeId: string | null;
  message: string;
}

/** Matches `{{ token }}` placeholders in free-text config fields. */
const TOKEN = /\{\{\s*([^}]+?)\s*\}\}/g;

/** Namespaces always considered resolvable (avoids dangling false-positives). */
const ALWAYS_ALLOWED_NS = new Set<string>(['const']);

/**
 * Frontend mirror of the backend {@code AutomationValidator}: an instant, in-editor "linter" that
 * implements the shared core rule catalog via the same issue-<b>code</b> vocabulary. Used for live
 * per-node badges, the Problems panel, and the activate guard. The backend remains the authoritative
 * enforcement gate; this service only drives UX feedback.
 *
 * @see VariableGraphService — reused for upstream traversal / namespace derivation.
 */
@Injectable({ providedIn: 'root' })
export class AutomationLintService {
  private i18n = inject(I18nService);
  private graph = inject(VariableGraphService);

  /** Runs the core rule catalog against a flow, returning all issues (errors + warnings). */
  lint(
    kind: AutomationKind,
    nodes: GraphNode[],
    edges: GraphEdge[],
    constants: AutomationConstant[],
    parameterSets: ParameterSet[],
  ): LintIssue[] {
    const issues: LintIssue[] = [];
    const constantNames = new Set((constants ?? []).map(c => c.name));

    // ── MISSING_TRIGGER ───────────────────────────────────────────────
    if (kind !== 'INTEGRATION' && !nodes.some(n => n.nodeType === 'TRIGGER')) {
      issues.push(this.error('MISSING_TRIGGER', null, {}));
    }

    const nodesWithIncoming = new Set<string>();
    for (const e of edges) {
      nodesWithIncoming.add(this.targetId(e));
    }

    for (const node of nodes) {
      const cfg = this.parse(node.config);
      const type = node.nodeType;
      const label = this.label(node);

      // ── ORPHAN_NODE ───────────────────────────────────────────────
      if (type !== 'TRIGGER' && type !== 'INPUT' && !nodesWithIncoming.has(node.id)) {
        issues.push(this.warning('ORPHAN_NODE', node.id, { label }));
      }

      // ── Required-config rules ─────────────────────────────────────
      this.checkRequiredConfig(node, cfg, label, issues);

      // ── DANGLING_VARIABLE ─────────────────────────────────────────
      this.checkDangling(node, cfg, label, nodes, edges, constantNames, issues);
    }

    return issues;
  }

  private checkRequiredConfig(node: GraphNode, cfg: Record<string, unknown>, label: string, issues: LintIssue[]): void {
    switch (node.nodeType) {
      case 'EXTRACT': {
        const extractions = Array.isArray(cfg['extractions']) ? cfg['extractions'] as Record<string, unknown>[] : [];
        const missingRef = extractions.some(ex => this.isBlank(ex?.['parameterSetId']));
        if (extractions.length === 0 || missingRef) {
          issues.push(this.error('EXTRACT_NO_PARAMSET', node.id, { label }));
        }
        break;
      }
      case 'CATEGORIZE': {
        const cats = Array.isArray(cfg['categoryIds']) ? cfg['categoryIds'] : [];
        if (cats.length === 0) issues.push(this.error('CATEGORIZE_NO_CATEGORIES', node.id, { label }));
        break;
      }
      case 'LABEL':
      case 'REMOVE_LABEL':
        if (this.isBlank(cfg['categoryId'])) issues.push(this.error('LABEL_NO_CATEGORY', node.id, { label }));
        break;
      case 'SEND_EMAIL':
        if (this.isBlank(cfg['to'])) issues.push(this.error('SEND_NO_RECIPIENT', node.id, { label }));
        break;
      case 'EMAIL_ACTION':
        if (this.emailActionMissingTarget(cfg)) issues.push(this.error('EMAILACTION_NO_TARGET', node.id, { label }));
        break;
      case 'WEBHOOK':
        if (this.isBlank(cfg['url'])) issues.push(this.error('WEBHOOK_NO_URL', node.id, { label }));
        break;
      case 'INTEGRATION_CALL':
        if (this.isBlank(cfg['integrationId'])) issues.push(this.error('INTEGRATION_NO_REF', node.id, { label }));
        break;
      case 'INPUT':
      case 'OUTPUT':
        if (this.isBlank(cfg['parameterSetId'])) issues.push(this.error('INTEGRATION_NO_REF', node.id, { label }));
        break;
      case 'VECTOR_SEARCH':
        if (this.isBlank(cfg['knowledgeBaseId'])) issues.push(this.error('VECTOR_SEARCH_NO_KB', node.id, { label }));
        if (this.isBlank(cfg['queryVariable'])) issues.push(this.error('VECTOR_SEARCH_NO_QUERY', node.id, { label }));
        break;
      case 'NOTIFY':
        if (String(cfg['recipientType'] ?? 'USER') === 'USER' && this.isBlank(cfg['recipientUserId'])) {
          issues.push(this.error('NOTIFY_NO_RECIPIENT', node.id, { label }));
        }
        if (this.isBlank(cfg['templateId']) && this.isBlank(cfg['message']) && this.isBlank(cfg['title'])) {
          issues.push(this.error('NOTIFY_NO_MESSAGE', node.id, { label }));
        }
        break;
      case 'FOREACH':
        if (this.isBlank(cfg['sourceVariable'])) issues.push(this.error('FOREACH_NO_SOURCE', node.id, { label }));
        break;
      default:
        break;
    }
  }

  private emailActionMissingTarget(cfg: Record<string, unknown>): boolean {
    const mode = String(cfg['actionMode'] ?? 'REPLY');
    switch (mode) {
      case 'FORWARD': return this.isBlank(cfg['toAddress']);
      case 'MOVE_FOLDER': return this.isBlank(cfg['folder']);
      default: { // REPLY
        const source = String(cfg['contentSource'] ?? 'VORLAGE');
        return source === 'MANUAL' ? this.isBlank(cfg['body']) : this.isBlank(cfg['templateId']);
      }
    }
  }

  // ── Dangling variable detection (namespace-level, mirrors VariableGraphService) ──

  private checkDangling(
    node: GraphNode, cfg: Record<string, unknown>, label: string,
    nodes: GraphNode[], edges: GraphEdge[], constants: Set<string>, issues: LintIssue[],
  ): void {
    const referenced = new Set<string>();

    switch (node.nodeType) {
      case 'SEND_EMAIL':
      case 'EMAIL_ACTION':
        this.addTokens(cfg['subject'], referenced);
        this.addTokens(cfg['body'], referenced);
        break;
      case 'WEBHOOK': {
        this.addTokens(cfg['url'], referenced);
        this.addTokens(cfg['body'], referenced);
        const headers = Array.isArray(cfg['headers']) ? cfg['headers'] as Record<string, unknown>[] : [];
        for (const h of headers) this.addTokens(h?.['value'], referenced);
        break;
      }
      case 'OUTPUT':
        this.addMappingTokens(cfg['outputMappings'], referenced);
        break;
      case 'INTEGRATION_CALL':
        this.addMappingTokens(cfg['inputMappings'], referenced);
        break;
      case 'VECTOR_SEARCH':
        this.addTokens(cfg['queryVariable'], referenced);
        break;
      case 'NOTIFY':
        this.addTokens(cfg['title'], referenced);
        this.addTokens(cfg['message'], referenced);
        this.addTokens(cfg['linkUrl'], referenced);
        break;
      case 'FILTER':
        this.collectFilterFields(cfg, referenced);
        break;
      case 'EXTRACT':
      case 'CATEGORIZE': {
        const sv = Array.isArray(cfg['sourceVariables']) ? cfg['sourceVariables'] as string[] : [];
        for (const v of sv) this.addReference(v, referenced);
        break;
      }
      case 'FOREACH':
        this.addReference(cfg['sourceVariable'], referenced);
        break;
      default:
        break;
    }

    if (referenced.size === 0) return;
    const available = this.availableNamespaces(node.id, nodes, edges);
    for (const ns of referenced) {
      if (ALWAYS_ALLOWED_NS.has(ns) || available.has(ns) || constants.has(ns)) continue;
      issues.push(this.warning('DANGLING_VARIABLE', node.id, { label, var: ns }));
    }
  }

  /** Namespaces produced by all nodes upstream of {@link nodeId}. */
  private availableNamespaces(nodeId: string, nodes: GraphNode[], edges: GraphEdge[]): Set<string> {
    const ns = new Set<string>();
    for (const up of this.graph.getUpstreamNodes(nodeId, nodes, edges)) {
      const cfg = this.parse(up.config);
      switch (up.nodeType) {
        case 'TRIGGER':
          ns.add('trigger');
          if (String(cfg['triggerMode'] ?? 'EMAIL') === 'EMAIL') ns.add('email');
          break;
        case 'EXTRACT': {
          const count = Array.isArray(cfg['extractions']) ? (cfg['extractions'] as unknown[]).length : 0;
          for (let i = 0; i < count; i++) ns.add('extraction_' + i);
          break;
        }
        case 'CATEGORIZE': ns.add('category'); break;
        case 'WEBHOOK': ns.add('http_' + (up.nodeKey ?? up.id)); break;
        case 'INPUT': ns.add('input'); break;
        case 'INTEGRATION_CALL': ns.add('integration_' + (up.nodeKey ?? up.id)); break;
        case 'VECTOR_SEARCH': ns.add('vectorsearch_' + (up.nodeKey ?? up.id)); break;
        case 'NOTIFY': ns.add('notify_' + (up.nodeKey ?? up.id)); break;
        case 'FOREACH': ns.add(String(cfg['itemAlias'] ?? 'item') || 'item'); break;
        default: break;
      }
    }
    return ns;
  }

  private collectFilterFields(cfg: Record<string, unknown>, referenced: Set<string>): void {
    const checks = Array.isArray(cfg['checks']) ? cfg['checks'] as Record<string, unknown>[] : [];
    for (const check of checks) {
      const groups = Array.isArray(check?.['groups']) ? check['groups'] as Record<string, unknown>[] : [];
      for (const group of groups) {
        const conditions = Array.isArray(group?.['conditions']) ? group['conditions'] as Record<string, unknown>[] : [];
        for (const cond of conditions) this.addReference(cond?.['field'], referenced);
      }
    }
  }

  private addMappingTokens(mappings: unknown, referenced: Set<string>): void {
    if (!mappings || typeof mappings !== 'object' || Array.isArray(mappings)) return;
    for (const v of Object.values(mappings as Record<string, unknown>)) this.addTokens(v, referenced);
  }

  private addTokens(text: unknown, referenced: Set<string>): void {
    if (typeof text !== 'string' || !text) return;
    for (const m of text.matchAll(TOKEN)) this.addReference(m[1], referenced);
  }

  /** Reduces a raw variable reference (e.g. `extraction_0.amount`) to its namespace. */
  private addReference(raw: unknown, referenced: Set<string>): void {
    if (typeof raw !== 'string') return;
    const trimmed = raw.trim();
    if (!trimmed) return;
    const dot = trimmed.indexOf('.');
    const namespace = dot >= 0 ? trimmed.substring(0, dot) : trimmed;
    if (namespace) referenced.add(namespace);
  }

  private targetId(edge: GraphEdge): string {
    return edge.inputId.split('_')[0];
  }

  private parse(config: string): Record<string, unknown> {
    try { return JSON.parse(config || '{}') as Record<string, unknown>; } catch { return {}; }
  }

  private label(node: GraphNode): string {
    return node.label && node.label.trim() ? node.label : node.nodeType;
  }

  private isBlank(value: unknown): boolean {
    return value === null || value === undefined || String(value).trim() === '';
  }

  private error(code: string, nodeId: string | null, vars: Record<string, string>): LintIssue {
    return { code, severity: 'error', nodeId, message: this.i18n.t('auto_lint_' + code.toLowerCase(), vars) };
  }

  private warning(code: string, nodeId: string | null, vars: Record<string, string>): LintIssue {
    return { code, severity: 'warning', nodeId, message: this.i18n.t('auto_lint_' + code.toLowerCase(), vars) };
  }
}
