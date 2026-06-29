import { inject, Injectable } from '@angular/core';
import { I18nService } from '../../../../core/services/i18n.service';
import {
  AutomationConstant,
  ExtractNodeConfig,
  IntegrationCallNodeConfig,
  InputNodeConfig,
  NodeType,
  TriggerNodeConfig,
  VariableGroup,
  VariableItem,
  WebhookNodeConfig,
} from '../../../../models/automation.model';
import { ParameterItem, ParameterSet } from '../../../../models/parameter-set.model';

/** Minimal structural view of an editor node needed for variable-graph traversal. */
export interface GraphNode {
  id: string;
  nodeType: NodeType;
  label: string;
  config: string;
  nodeKey?: string | null;
}

/** Minimal structural view of an editor edge needed for variable-graph traversal. */
export interface GraphEdge {
  outputId: string;
  inputId: string;
}

/**
 * Derives the set of runtime variables available to a node based on its upstream graph.
 *
 * <p>Extracted from {@code AutomationEditorComponent} to isolate the pure variable-derivation
 * and upstream-traversal logic from the editor's UI/state concerns. Methods are stateless and
 * operate only on the node/edge data passed in; behaviour is unchanged from the original inline
 * helpers.</p>
 */
@Injectable({ providedIn: 'root' })
export class VariableGraphService {
  private i18n = inject(I18nService);

  /** Returns the variable groups available to {@link nodeId}, scoped by its upstream nodes. */
  getAvailableVariables(
    nodeId: string,
    nodes: GraphNode[],
    edges: GraphEdge[],
    constants: AutomationConstant[],
    parameterSets: ParameterSet[],
  ): VariableGroup[] {
    const groups: VariableGroup[] = [];
    const upstream = this.getUpstreamNodes(nodeId, nodes, edges);

    // User-defined constants — always available
    if (constants.length) {
      groups.push({
        label: this.i18n.t('auto_vars_group_constants'),
        icon: 'code',
        color: '#0ea5e9',
        variables: constants.map(c => ({
          key: `const.${c.name}`,
          label: c.name,
          description: c.description || (c.type === 'secret' ? this.i18n.t('auto_const_type_secret') : c.value),
        })),
      });
    }

    // Email base variables — only if upstream TRIGGER(EMAIL) exists
    if (this.hasUpstreamEmailTrigger(upstream)) {
      groups.push({
        label: this.i18n.t('auto_vars_group_email'),
        icon: 'mail',
        color: '#10b981',
        variables: [
          { key: 'email.from', label: this.i18n.t('auto_var_email_from'), description: this.i18n.t('auto_var_email_from_desc') },
          { key: 'email.fromName', label: this.i18n.t('auto_var_email_fromName'), description: this.i18n.t('auto_var_email_fromName_desc') },
          { key: 'email.to', label: this.i18n.t('auto_var_email_to'), description: this.i18n.t('auto_var_email_to_desc') },
          { key: 'email.subject', label: this.i18n.t('auto_var_email_subject'), description: this.i18n.t('auto_var_email_subject_desc') },
          { key: 'email.body', label: this.i18n.t('auto_var_email_body'), description: this.i18n.t('auto_var_email_body_desc') },
          { key: 'email.receivedAt', label: this.i18n.t('auto_var_email_receivedAt'), description: this.i18n.t('auto_var_email_receivedAt_desc') },
          { key: 'email.hasAttachments', label: this.i18n.t('auto_var_email_hasAttachments'), description: this.i18n.t('auto_var_email_hasAttachments_desc') },
          { key: 'email.attachments', label: this.i18n.t('auto_var_email_attachments'), description: this.i18n.t('auto_var_email_attachments_desc') },
          { key: 'email.folder', label: this.i18n.t('auto_var_email_folder'), description: this.i18n.t('auto_var_email_folder_desc') },
        ],
      });
    }

    // Traverse upstream nodes
    for (const n of upstream) {
      if (n.nodeType === 'EXTRACT') {
        try {
          const cfg: ExtractNodeConfig = JSON.parse(n.config || '{}');
          const vars: VariableItem[] = [];
          (cfg.extractions || []).forEach((entry, i) => {
            const ps = parameterSets.find(p => p.id === entry.parameterSetId);
            if (ps) {
              ps.parameters.forEach(param => {
                vars.push({
                  key: `extraction_${i}.${param.name}`,
                  label: `${ps.name}: ${param.name}`,
                  description: this.i18n.t('auto_var_extract_desc'),
                });
              });
            }
          });
          if (vars.length) {
            groups.push({ label: n.label || 'Extract', icon: 'sparkle', color: '#8b5cf6', variables: vars });
          }
        } catch { /* ignore */ }
      } else if (n.nodeType === 'WEBHOOK') {
        const vars: VariableItem[] = [
          { key: `http_${n.nodeKey ?? n.id}.statusCode`, label: this.i18n.t('auto_var_webhook_statusCode'), description: this.i18n.t('auto_var_webhook_statusCode_desc') },
          { key: `http_${n.nodeKey ?? n.id}.body`, label: this.i18n.t('auto_var_webhook_body'), description: this.i18n.t('auto_var_webhook_body_desc') },
        ];
        try {
          const cfg: WebhookNodeConfig = JSON.parse(n.config || '{}');
          for (const schema of cfg.responseSchemas || []) {
            const ps = parameterSets.find(p => p.id === schema.parameterSetId);
            if (ps) {
              this.collectParamVars(`http_${n.nodeKey ?? n.id}`, '', ps.parameters, ps.name, schema.condition, vars);
            }
          }
        } catch { /* ignore */ }
        groups.push({ label: n.label || 'HTTP Request', icon: 'globe', color: '#06b6d4', variables: vars });
      } else if (n.nodeType === 'INPUT') {
        // Integration entry point — exposes input.<field> from the referenced parameter set.
        try {
          const cfg: InputNodeConfig = JSON.parse(n.config || '{}');
          const ps = parameterSets.find(p => p.id === cfg.parameterSetId);
          if (ps && ps.parameters.length) {
            groups.push({
              label: this.i18n.t('auto_vars_group_input'),
              icon: 'signin',
              color: '#10b981',
              variables: ps.parameters.map(param => ({
                key: `input.${param.name}`,
                label: `${ps.name}: ${param.name}`,
                description: param.description || this.i18n.t('auto_var_input_desc'),
              })),
            });
          }
        } catch { /* ignore */ }
      } else if (n.nodeType === 'TRIGGER') {
        // MANUAL / inbound-WEBHOOK triggers seed trigger.<field> from a parameter set (manual: the
        // user-entered values at run time; webhook: the mapped request-body fields). Expose those so
        // downstream nodes can reference them and the linter doesn't flag them as dangling.
        try {
          const cfg = JSON.parse(n.config || '{}') as TriggerNodeConfig;
          if (cfg.triggerMode === 'MANUAL' || cfg.triggerMode === 'WEBHOOK') {
            const vars: VariableItem[] = [];
            const ps = parameterSets.find(p => p.id === cfg.parameterSetId);
            if (ps) {
              ps.parameters.forEach(param => vars.push({
                key: `trigger.${param.name}`,
                label: `${ps.name}: ${param.name}`,
                description: param.description || this.i18n.t('auto_var_trigger_desc'),
              }));
            }
            vars.push({
              key: 'trigger.receivedAt',
              label: this.i18n.t('auto_var_trigger_receivedAt'),
              description: this.i18n.t('auto_var_trigger_receivedAt_desc'),
            });
            groups.push({ label: n.label || this.i18n.t('auto_node_trigger'), icon: 'zap', color: '#10b981', variables: vars });
          }
        } catch { /* ignore */ }
      } else if (n.nodeType === 'FOREACH') {
        // Iterator — exposes the current element under the alias (default `item`). For
        // email.attachments the element carries the attachment metadata fields; index/count always.
        try {
          const cfg = JSON.parse(n.config || '{}') as { sourceVariable?: string; itemAlias?: string };
          const alias = cfg.itemAlias || 'item';
          const vars: VariableItem[] = [];
          if (cfg.sourceVariable === 'email.attachments') {
            vars.push(
              // The element itself — add as an EXTRACT/CATEGORIZE source to feed THIS one attachment to the AI.
              { key: alias, label: this.i18n.t('auto_var_attachment_current'), description: this.i18n.t('auto_var_attachment_current_desc') },
              { key: `${alias}.name`, label: this.i18n.t('auto_var_attachment_name'), description: this.i18n.t('auto_var_attachment_name_desc') },
              { key: `${alias}.contentType`, label: this.i18n.t('auto_var_attachment_type'), description: this.i18n.t('auto_var_attachment_type_desc') },
              { key: `${alias}.size`, label: this.i18n.t('auto_var_attachment_size'), description: this.i18n.t('auto_var_attachment_size_desc') },
            );
          }
          vars.push(
            { key: `${alias}.index`, label: this.i18n.t('auto_var_foreach_index'), description: this.i18n.t('auto_var_foreach_index_desc') },
            { key: `${alias}.count`, label: this.i18n.t('auto_var_foreach_count'), description: this.i18n.t('auto_var_foreach_count_desc') },
          );
          groups.push({ label: n.label || this.i18n.t('auto_node_foreach'), icon: 'layers', color: '#0891b2', variables: vars });
        } catch { /* ignore */ }
      } else if (n.nodeType === 'VECTOR_SEARCH') {
        // Knowledge-base match — exposes vectorsearch_<nodeId>.confidence/.reason + the matched
        // entry's fields (vectorsearch_<nodeId>.match.<field>), cached from the KB's parameter set.
        const vars: VariableItem[] = [
          { key: `vectorsearch_${n.nodeKey ?? n.id}.confidence`, label: this.i18n.t('auto_test_confidence_label'), description: this.i18n.t('auto_var_vs_desc') },
          { key: `vectorsearch_${n.nodeKey ?? n.id}.reason`, label: this.i18n.t('auto_test_reason_label'), description: this.i18n.t('auto_var_vs_desc') },
        ];
        try {
          const cfg = JSON.parse(n.config || '{}') as { matchFields?: string[] };
          for (const f of cfg.matchFields || []) {
            vars.push({ key: `vectorsearch_${n.nodeKey ?? n.id}.match.${f}`, label: `match.${f}`, description: this.i18n.t('auto_var_vs_match_desc') });
          }
        } catch { /* ignore */ }
        groups.push({ label: n.label || this.i18n.t('auto_node_vector_search'), icon: 'sparkle', color: '#0ea5e9', variables: vars });
      } else if (n.nodeType === 'INTEGRATION_CALL') {
        // Reusable integration result — exposes integration_<nodeId>.<outputField>.
        try {
          const cfg: IntegrationCallNodeConfig = JSON.parse(n.config || '{}');
          const fields = cfg.outputFields || [];
          if (fields.length) {
            groups.push({
              label: n.label || this.i18n.t('auto_node_integration_call'),
              icon: 'cube',
              color: '#a855f7',
              variables: fields.map(f => ({
                key: `integration_${n.nodeKey ?? n.id}.${f}`,
                label: f,
                description: this.i18n.t('auto_var_integration_desc'),
              })),
            });
          }
        } catch { /* ignore */ }
      } else if (n.nodeType === 'NOTIFY') {
        // In-app notification — exposes notify_<nodeId>.sent (boolean) + .recipientCount (number).
        groups.push({
          label: n.label || this.i18n.t('auto_node_notify'),
          icon: 'bell',
          color: '#6d28d9',
          variables: [
            { key: `notify_${n.nodeKey ?? n.id}.sent`, label: this.i18n.t('auto_notify_var_sent'), description: this.i18n.t('auto_notify_var_desc') },
            { key: `notify_${n.nodeKey ?? n.id}.recipientCount`, label: this.i18n.t('auto_notify_var_recipient_count'), description: this.i18n.t('auto_notify_var_desc') },
          ],
        });
      }
    }

    return groups;
  }

  /**
   * Recursively walks a (possibly nested) response ParameterSet, pushing one variable per scalar
   * leaf with a flat dot/bracket key — mirroring the backend's ParameterSet-driven response
   * extraction (e.g. order.customer.email, items[0].sku, items.length).
   */
  private collectParamVars(
    keyPrefix: string,
    labelPrefix: string,
    params: ParameterItem[],
    psName: string,
    condition: string,
    out: VariableItem[],
  ): void {
    for (const param of params) {
      if (!param.name) continue;
      const key = `${keyPrefix}.${param.name}`;
      const labelPath = labelPrefix ? `${labelPrefix}.${param.name}` : param.name;
      const children = param.children ?? [];
      const push = (k: string, lp: string) =>
        out.push({ key: k, label: `${psName}: ${lp} (${condition})`, description: this.i18n.t('auto_var_webhook_param_desc') });
      if (param.isList) {
        if (children.length) {
          this.collectParamVars(`${key}[0]`, `${labelPath}[0]`, children, psName, condition, out);
        } else {
          push(`${key}[0]`, `${labelPath}[0]`);
        }
        push(`${key}.length`, `${labelPath}.length`);
      } else if (children.length) {
        this.collectParamVars(key, labelPath, children, psName, condition, out);
      } else {
        push(key, labelPath);
      }
    }
  }

  /** True when any upstream node is a TRIGGER in EMAIL mode. */
  hasUpstreamEmailTrigger(upstream: GraphNode[]): boolean {
    return upstream.some(n => {
      if (n.nodeType !== 'TRIGGER') return false;
      try {
        const cfg = JSON.parse(n.config || '{}');
        return (cfg.triggerMode || 'EMAIL') === 'EMAIL';
      } catch { return false; }
    });
  }

  /** Breadth-first walk back through the edge graph collecting all nodes upstream of {@link nodeId}. */
  getUpstreamNodes(nodeId: string, nodes: GraphNode[], edges: GraphEdge[]): GraphNode[] {
    const result: GraphNode[] = [];
    const visited = new Set<string>();
    const queue = [nodeId];
    while (queue.length) {
      const current = queue.shift()!;
      if (visited.has(current)) continue;
      visited.add(current);
      for (const edge of edges) {
        // edges: outputId = "sourceNodeId_sourceHandle", inputId = "targetNodeId_targetHandle"
        const targetId = edge.inputId.split('_')[0];
        const sourceId = edge.outputId.split('_')[0];
        if (targetId === current && !visited.has(sourceId)) {
          const node = nodes.find(n => n.id === sourceId);
          if (node) {
            result.push(node);
            queue.push(sourceId);
          }
        }
      }
    }
    return result;
  }
}
