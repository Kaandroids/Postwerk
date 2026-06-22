import { ChangeDetectionStrategy, Component, inject, signal, OnInit } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { I18nService } from '../../../../core/services/i18n.service';
import { AdminService } from '../../../../core/services/admin.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
import { PageContentComponent } from '../page-content/page-content.component';
import { ModelPricing, ModelPricingRequest } from '../../../../models/admin.model';
import { humanizeError } from '../../../../shared/utils/error.util';

/** Admin page for editing per-model AI pricing (USD per million tokens) that feeds cost tracking. */
@Component({
  selector: 'app-admin-pricing',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, ErrorBannerComponent, PageContentComponent, FormsModule, DecimalPipe],
  templateUrl: './admin-pricing.component.html',
  // Reuse the plans page form/button styles (DRY); pricing-specific rules live in the first file.
  styleUrls: ['./admin-pricing.component.scss', '../admin-plans/admin-plans.component.scss'],
})
export class AdminPricingComponent implements OnInit {
  protected i18n = inject(I18nService);
  protected identity = inject(AdminIdentityService);
  private adminService = inject(AdminService);
  private confirmDialog = inject(ConfirmDialogService);

  models = signal<ModelPricing[]>([]);
  loading = signal(true);
  view = signal<'list' | 'form'>('list');
  editing = signal<ModelPricing | null>(null);
  error = signal('');

  // Form fields
  model = '';
  inputPerMillion = 0;
  outputPerMillion = 0;

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.adminService.getModelPricing().subscribe({
      next: m => { this.models.set(m); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  showCreateForm() {
    this.editing.set(null);
    this.model = '';
    this.inputPerMillion = 0;
    this.outputPerMillion = 0;
    this.view.set('form');
  }

  showEditForm(m: ModelPricing) {
    this.editing.set(m);
    this.model = m.model;
    this.inputPerMillion = m.inputPerMillion;
    this.outputPerMillion = m.outputPerMillion;
    this.view.set('form');
  }

  cancel() {
    this.view.set('list');
  }

  save() {
    const req: ModelPricingRequest = {
      model: this.model.trim(),
      inputPerMillion: this.inputPerMillion,
      outputPerMillion: this.outputPerMillion,
    };
    this.error.set('');
    const editing = this.editing();
    const obs = editing
      ? this.adminService.updateModelPricing(editing.id, req)
      : this.adminService.createModelPricing(req);
    obs.subscribe({
      next: () => { this.view.set('list'); this.load(); },
      error: (err) => this.error.set(humanizeError(err, this.i18n.t('admin_pricing_action_failed'))),
    });
  }

  async deleteModel(m: ModelPricing) {
    // Deleting a rate makes the model fall back to its application.yml rate (0 if none) — confirm first.
    const ok = await this.confirmDialog.confirm({
      title: this.i18n.t('admin_pricing_delete_title'),
      message: this.i18n.t('admin_pricing_delete_msg', { model: m.model }),
      confirmText: this.i18n.t('admin_pricing_delete_confirm'),
      cancelText: this.i18n.t('confirm_cancel'),
      tone: 'danger',
    });
    if (!ok) return;
    this.error.set('');
    this.adminService.deleteModelPricing(m.id).subscribe({
      next: () => this.load(),
      error: (err) => this.error.set(humanizeError(err, this.i18n.t('admin_pricing_action_failed'))),
    });
  }
}
