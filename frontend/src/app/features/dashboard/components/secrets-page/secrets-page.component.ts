import { ChangeDetectionStrategy, Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { I18nService } from '../../../../core/services/i18n.service';
import { SecretService } from '../../../../core/services/secret.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state.component';
import { PageContentComponent } from '../page-content/page-content.component';
import { Secret } from '../../../../models/secret.model';

/** CRUD page for managing encrypted secrets used in automation webhook headers and API calls. */
@Component({
  selector: 'app-secrets-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, EmptyStateComponent, PageContentComponent, FormsModule],
  templateUrl: './secrets-page.component.html',
  styleUrl: './secrets-page.component.scss',
})
export class SecretsPageComponent implements OnInit {
  protected i18n = inject(I18nService);
  private secretService = inject(SecretService);

  secrets = signal<Secret[]>([]);
  loading = signal(true);
  view = signal<'list' | 'form'>('list');
  editingSecret = signal<Secret | null>(null);
  showValue = signal(false);

  name = '';
  description = '';
  value = '';

  ngOnInit() {
    this.loadSecrets();
  }

  loadSecrets() {
    this.loading.set(true);
    this.secretService.list().subscribe({
      next: s => { this.secrets.set(s); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  showCreateForm() {
    this.editingSecret.set(null);
    this.name = '';
    this.description = '';
    this.value = '';
    this.showValue.set(false);
    this.view.set('form');
  }

  showEditForm(secret: Secret) {
    this.editingSecret.set(secret);
    this.name = secret.name;
    this.description = secret.description || '';
    this.value = '';
    this.showValue.set(false);
    this.view.set('form');
  }

  cancel() {
    this.view.set('list');
  }

  save() {
    const req = {
      name: this.name,
      description: this.description || null,
      value: this.value,
    };

    const editing = this.editingSecret();
    if (editing) {
      this.secretService.update(editing.id, req).subscribe({
        next: () => { this.view.set('list'); this.loadSecrets(); },
        error: () => { /* save failed — form stays open for retry */ },
      });
    } else {
      this.secretService.create(req).subscribe({
        next: () => { this.view.set('list'); this.loadSecrets(); },
        error: () => { /* save failed — form stays open for retry */ },
      });
    }
  }

  deleteSecret(secret: Secret) {
    this.secretService.delete(secret.id).subscribe({
      next: () => this.loadSecrets(),
      error: () => { /* deletion failed — list unchanged */ },
    });
  }

  toggleShowValue() {
    this.showValue.set(!this.showValue());
  }

  formatDate(iso: string | null): string {
    if (!iso) return '–';
    return this.i18n.formatDate(iso);
  }
}
