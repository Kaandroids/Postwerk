import { Component, ChangeDetectionStrategy, inject, input, output, computed, signal } from '@angular/core';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { I18nService } from '../../../../core/services/i18n.service';
import { WizardService } from '../../services/wizard.service';
import { WizardFlowNode, WizardFlowEdge, WizNodeKind } from '../../models/wizard.model';

interface DynamicStep {
  kind: WizNodeKind;
  label: string;
  description: string;
}

const NODE_KIND_MAP: Record<string, WizNodeKind> = {
  TRIGGER: 'trigger',
  FILTER: 'filter',
  CATEGORIZE: 'classify',
  EXTRACT: 'extract',
  EMAIL_ACTION: 'action',
  SEND_EMAIL: 'action',
  LABEL: 'action',
  REMOVE_LABEL: 'action',
  WEBHOOK: 'action',
  DELAY: 'action',
};

const NODE_ICONS: Record<string, string> = {
  TRIGGER: 'mail',
  FILTER: 'filter',
  EXTRACT: 'sparkles',
  CATEGORIZE: 'tag',
  DELAY: 'clock',
  LABEL: 'tag',
  EMAIL_ACTION: 'send',
  SEND_EMAIL: 'mail',
  REMOVE_LABEL: 'tag',
  WEBHOOK: 'webhook',
};

const TYPE_LABELS_DE: Record<string, string> = {
  TRIGGER: 'E-Mail-Eingang',
  FILTER: 'Filter',
  CATEGORIZE: 'KI-Kategorisierung',
  EXTRACT: 'Datenextraktion',
  EMAIL_ACTION: 'E-Mail-Aktion',
  SEND_EMAIL: 'E-Mail senden',
  LABEL: 'Label zuweisen',
  REMOVE_LABEL: 'Label entfernen',
  WEBHOOK: 'Webhook',
  DELAY: 'Verzögerung',
};

const TYPE_LABELS_EN: Record<string, string> = {
  TRIGGER: 'Email Trigger',
  FILTER: 'Filter',
  CATEGORIZE: 'AI Classification',
  EXTRACT: 'Data Extraction',
  EMAIL_ACTION: 'Email Action',
  SEND_EMAIL: 'Send Email',
  LABEL: 'Assign Label',
  REMOVE_LABEL: 'Remove Label',
  WEBHOOK: 'Webhook',
  DELAY: 'Delay',
};

const DESC_DE: Record<string, string> = {
  TRIGGER: 'Jede neue E-Mail wird automatisch erfasst.',
  FILTER: 'Filterregeln sortieren E-Mails vor.',
  CATEGORIZE: 'Die KI kategorisiert den Inhalt.',
  EXTRACT: 'Wichtige Daten werden ausgelesen.',
  EMAIL_ACTION: 'Die E-Mail wird beantwortet, weitergeleitet oder verschoben.',
  SEND_EMAIL: 'Eine neue E-Mail wird gesendet.',
  LABEL: 'Ein Label wird zugewiesen.',
  REMOVE_LABEL: 'Ein Label wird entfernt.',
  WEBHOOK: 'Ein externer Dienst wird benachrichtigt.',
  DELAY: 'Die Verarbeitung wird verzögert.',
};

const DESC_EN: Record<string, string> = {
  TRIGGER: 'Every new email is captured automatically.',
  FILTER: 'Filter rules sort emails upfront.',
  CATEGORIZE: 'The AI classifies the content.',
  EXTRACT: 'Key data is extracted automatically.',
  EMAIL_ACTION: 'The email is replied to, forwarded, or moved.',
  SEND_EMAIL: 'A new email is sent.',
  LABEL: 'A label is assigned.',
  REMOVE_LABEL: 'A label is removed.',
  WEBHOOK: 'An external service is notified.',
  DELAY: 'Processing is delayed.',
};

@Component({
  selector: 'app-wizard-how-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  templateUrl: './wizard-how-card.component.html',
  styleUrl: './wizard-how-card.component.scss',
})
export class WizardHowCardComponent {
  protected readonly i18n = inject(I18nService);
  private readonly wizard = inject(WizardService);
  readonly visible = input(false);
  readonly continueChat = output<void>();

  /** Collapsed state — the card can be folded down to just its header. */
  readonly collapsed = signal(false);

  readonly steps = computed<DynamicStep[]>(() => {
    const plan = this.wizard.automationPlan();
    if (!plan?.nodes?.length || !plan?.edges?.length) return [];

    const nodes = plan.nodes;
    const edges = plan.edges;
    const sorted = this.topoSort(nodes, edges);
    const de = this.i18n.lang() === 'de';

    return sorted.map(node => {
      const kind = NODE_KIND_MAP[node.nodeType] ?? 'action';
      const typeLabels = de ? TYPE_LABELS_DE : TYPE_LABELS_EN;
      const descs = de ? DESC_DE : DESC_EN;
      return {
        kind,
        label: typeLabels[node.nodeType] ?? node.label,
        description: descs[node.nodeType] ?? node.label,
      };
    });
  });

  getIcon(step: DynamicStep, nodes: WizardFlowNode[]): string {
    const node = nodes.find(n => (NODE_KIND_MAP[n.nodeType] ?? 'action') === step.kind);
    return NODE_ICONS[node?.nodeType ?? ''] ?? 'sparkles';
  }

  private topoSort(nodes: WizardFlowNode[], edges: WizardFlowEdge[]): WizardFlowNode[] {
    const nodeMap = new Map(nodes.map(n => [n.id, n]));
    const inDegree = new Map<string, number>();
    const adj = new Map<string, string[]>();

    for (const n of nodes) {
      inDegree.set(n.id, 0);
      adj.set(n.id, []);
    }
    for (const e of edges) {
      adj.get(e.sourceNodeId)?.push(e.targetNodeId);
      inDegree.set(e.targetNodeId, (inDegree.get(e.targetNodeId) ?? 0) + 1);
    }

    const queue: string[] = [];
    for (const [id, deg] of inDegree) {
      if (deg === 0) queue.push(id);
    }

    const result: WizardFlowNode[] = [];
    while (queue.length > 0) {
      const id = queue.shift()!;
      const node = nodeMap.get(id);
      if (node) result.push(node);
      for (const next of adj.get(id) ?? []) {
        const d = (inDegree.get(next) ?? 1) - 1;
        inDegree.set(next, d);
        if (d === 0) queue.push(next);
      }
    }

    return result;
  }
}
