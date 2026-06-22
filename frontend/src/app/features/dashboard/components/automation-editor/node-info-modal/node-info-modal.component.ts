import { ChangeDetectionStrategy, Component, HostListener, computed, inject, input, output } from '@angular/core';
import { I18nService } from '../../../../../core/services/i18n.service';
import { IconComponent } from '../../../../../shared/components/icon/icon.component';
import { NodeReferenceComponent } from '../../../../../shared/components/node-reference/node-reference.component';
import { NodeType } from '../../../../../models/automation.model';

/**
 * In-editor node help. Opened by the "?" affordance on the node config panel,
 * it renders the shared {@link NodeReferenceComponent} (same content as the
 * docs node page) with a deep-link to the full docs article.
 */
@Component({
  selector: 'app-node-info-modal',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, NodeReferenceComponent],
  templateUrl: './node-info-modal.component.html',
  styleUrl: './node-info-modal.component.scss',
})
export class NodeInfoModalComponent {
  protected i18n = inject(I18nService);

  readonly nodeType = input.required<NodeType>();
  readonly close = output<void>();

  protected readonly docsUrl = computed(() => `/docs/nodes/${this.nodeType().toLowerCase()}`);

  @HostListener('document:keydown.escape')
  protected onEsc(): void {
    this.close.emit();
  }
}
