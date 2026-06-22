import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { I18nService } from '../../../core/services/i18n.service';
import { IconComponent } from '../icon/icon.component';
import {
  NodeType,
  getNodeColor,
  getNodeIcon,
  getNodeLabelKey,
} from '../../../models/automation.model';
import { NODE_DOCS } from '../../data/node-docs.data';
import { Bi, HandleType } from '../../data/node-docs.model';

/**
 * Renders the full reference for one automation node from {@link NODE_DOCS}.
 * Shared by the docs node pages (`/docs/nodes/<type>`) and the in-editor "?"
 * node-info modal, so the two surfaces never drift. Color, icon and label come
 * from `NODE_PALETTE` (single source of truth).
 */
@Component({
  selector: 'app-node-reference',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  templateUrl: './node-reference.component.html',
  styleUrl: './node-reference.component.scss',
})
export class NodeReferenceComponent {
  protected i18n = inject(I18nService);

  readonly type = input.required<NodeType>();
  /** Compact rendering for the editor modal (tighter spacing). */
  readonly compact = input(false);

  protected readonly doc = computed(() => NODE_DOCS[this.type()] ?? null);

  /** True when modes carry their own variables/handles (e.g. Trigger) — render them per mode. */
  protected readonly modesDetailed = computed(() =>
    (this.doc()?.modes ?? []).some(m => (m.produces?.length ?? 0) > 0 || (m.handlesOut?.length ?? 0) > 0),
  );
  protected readonly color = computed(() => getNodeColor(this.type()));
  protected readonly icon = computed(() => getNodeIcon(this.type()));
  protected readonly label = computed(() => this.i18n.t(getNodeLabelKey(this.type())));

  /** Resolves a bilingual value for the active language. */
  protected tr(bi: Bi | undefined): string {
    return bi ? bi[this.i18n.lang()] : '';
  }

  /** Wraps a variable path in literal double braces for display. */
  protected varToken(v: string): string {
    return `{{${v}}}`;
  }

  private static readonly HANDLE_COLORS: Record<HandleType, string> = {
    email: 'var(--accent)',
    any: 'var(--fg-muted)',
    cat: '#ec4899',
    param: '#8b5cf6',
    json: '#06b6d4',
    done: '#f59e0b',
  };

  /** Hollow ring for pass-through types, filled dot for typed payloads. */
  protected handleFilled(htype: HandleType): boolean {
    return htype !== 'email' && htype !== 'any';
  }

  protected handleColor(htype: HandleType): string {
    return NodeReferenceComponent.HANDLE_COLORS[htype];
  }
}
