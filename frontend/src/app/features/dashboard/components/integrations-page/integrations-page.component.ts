import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AutomationService } from '../../../../core/services/automation.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { ButtonComponent } from '../../../../shared/components/button/button.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
import { ColorPickerComponent } from '../../../../shared/components/color-picker/color-picker.component';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state.component';
import { PageContentComponent } from '../page-content/page-content.component';
import { Automation, AutomationRequest, DEFAULT_COLORS } from '../../../../models/automation.model';
import { CrudPageBase } from '../../../../shared/utils/crud-page.base';
import { humanizeError } from '../../../../shared/utils/error.util';

/** List + create page for integrations (trigger-less reusable automations, kind=INTEGRATION). */
@Component({
  selector: 'app-integrations-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, ButtonComponent, ErrorBannerComponent, ColorPickerComponent, EmptyStateComponent, PageContentComponent],
  templateUrl: './integrations-page.component.html',
  styleUrl: '../automations-page/automations-page.component.scss',
})
export class IntegrationsPageComponent extends CrudPageBase {
  private automationService = inject(AutomationService);
  private router = inject(Router);

  readonly colors = DEFAULT_COLORS;

  integrations = signal<Automation[]>([]);

  // Form fields
  name = signal('');
  description = signal('');
  color = signal(DEFAULT_COLORS[3]);

  searchQuery = signal('');

  filtered = computed(() => {
    const q = this.searchQuery().trim().toLowerCase();
    return this.integrations().filter(a =>
      !q || a.name.toLowerCase().includes(q) || (a.description || '').toLowerCase().includes(q));
  });

  constructor() {
    super();
    this.load();
  }

  openEditor(a: Automation): void {
    this.router.navigate(['/dashboard/automations', a.id, 'edit']);
  }

  deleteIntegration(id: string): void {
    this.deleteWithConfirm('intg_delete_confirm', () => this.automationService.delete(id), () => this.load());
  }

  submit(event: Event): void {
    event.preventDefault();
    this.error.set('');
    this.fieldErr.set({});

    if (!this.name().trim()) {
      this.error.set(this.i18n.t('auto_error_required'));
      this.fieldErr.set({ name: true });
      return;
    }

    this.loading.set(true);
    const req: AutomationRequest = {
      name: this.name(),
      description: this.description() || null,
      color: this.color(),
      kind: 'INTEGRATION',
    };

    this.automationService.create(req).subscribe({
      next: (created) => {
        this.loading.set(false);
        this.view.set('list');
        this.openEditor(created);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(humanizeError(err, 'Error'));
      },
    });
  }

  private load(): void {
    this.automationService.listIntegrations().subscribe({
      next: (list) => this.integrations.set(list),
      error: () => {},
    });
  }

  protected override resetForm(): void {
    this.name.set('');
    this.description.set('');
    this.color.set(DEFAULT_COLORS[Math.floor(Math.random() * DEFAULT_COLORS.length)]);
    this.error.set('');
    this.fieldErr.set({});
  }
}
