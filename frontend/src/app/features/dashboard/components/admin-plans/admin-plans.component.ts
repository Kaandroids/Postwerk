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
import { PlanModel, PlanRequest } from '../../../../models/admin.model';
import { humanizeError } from '../../../../shared/utils/error.util';

/** Admin page for managing subscription plans with CRUD form for limits, pricing, and features. */
@Component({
  selector: 'app-admin-plans',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, ErrorBannerComponent, PageContentComponent, FormsModule, DecimalPipe],
  templateUrl: './admin-plans.component.html',
  styleUrl: './admin-plans.component.scss',
})
export class AdminPlansComponent implements OnInit {
  protected i18n = inject(I18nService);
  protected identity = inject(AdminIdentityService);
  private adminService = inject(AdminService);
  private confirmDialog = inject(ConfirmDialogService);

  plans = signal<PlanModel[]>([]);
  loading = signal(true);
  view = signal<'list' | 'form'>('list');
  editingPlan = signal<PlanModel | null>(null);
  error = signal('');

  // Form fields
  name = '';
  tokenLimit = 0;
  automationLimit = 0;
  emailAccountLimit = 0;
  price = 0;
  costLimitEur = 0;
  apiWebhookEnabled = false;
  inboundWebhookLimit = 0;

  ngOnInit() {
    this.loadPlans();
  }

  loadPlans() {
    this.loading.set(true);
    this.adminService.getPlans().subscribe({
      next: p => { this.plans.set(p); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  showCreateForm() {
    this.editingPlan.set(null);
    this.name = '';
    this.tokenLimit = 0;
    this.automationLimit = 0;
    this.emailAccountLimit = 0;
    this.price = 0;
    this.costLimitEur = 0;
    this.apiWebhookEnabled = false;
    this.inboundWebhookLimit = 0;
    this.view.set('form');
  }

  showEditForm(plan: PlanModel) {
    this.editingPlan.set(plan);
    this.name = plan.name;
    this.tokenLimit = plan.tokenLimit;
    this.automationLimit = plan.automationLimit;
    this.emailAccountLimit = plan.emailAccountLimit;
    this.price = plan.price;
    this.costLimitEur = plan.costLimitCents / 100;
    this.apiWebhookEnabled = plan.apiWebhookEnabled;
    this.inboundWebhookLimit = plan.inboundWebhookLimit;
    this.view.set('form');
  }

  cancel() {
    this.view.set('list');
  }

  save() {
    const req: PlanRequest = {
      name: this.name,
      tokenLimit: this.tokenLimit,
      automationLimit: this.automationLimit,
      emailAccountLimit: this.emailAccountLimit,
      price: this.price,
      costLimitCents: Math.round(this.costLimitEur * 100),
      apiWebhookEnabled: this.apiWebhookEnabled,
      inboundWebhookLimit: this.inboundWebhookLimit,
    };

    this.error.set('');
    const editing = this.editingPlan();
    if (editing) {
      this.adminService.updatePlan(editing.id, req).subscribe({
        next: () => { this.view.set('list'); this.loadPlans(); },
        error: (err) => this.error.set(humanizeError(err, this.i18n.t('admin_plans_action_failed'))),
      });
    } else {
      this.adminService.createPlan(req).subscribe({
        next: () => { this.view.set('list'); this.loadPlans(); },
        error: (err) => this.error.set(humanizeError(err, this.i18n.t('admin_plans_action_failed'))),
      });
    }
  }

  async deletePlan(plan: PlanModel) {
    // Deleting a plan is irreversible and affects every subscriber — confirm first.
    const ok = await this.confirmDialog.confirm({
      title: this.i18n.t('admin_plans_delete_title'),
      message: this.i18n.t('admin_plans_delete_msg', { name: plan.name }),
      confirmText: this.i18n.t('admin_plans_delete_confirm'),
      cancelText: this.i18n.t('confirm_cancel'),
      tone: 'danger',
    });
    if (!ok) return;
    this.error.set('');
    this.adminService.deletePlan(plan.id).subscribe({
      next: () => this.loadPlans(),
      error: (err) => this.error.set(humanizeError(err, this.i18n.t('admin_plans_action_failed'))),
    });
  }
}
