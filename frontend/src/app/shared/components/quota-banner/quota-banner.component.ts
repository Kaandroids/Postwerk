import { ChangeDetectionStrategy, Component, inject, computed } from '@angular/core';
import { Router } from '@angular/router';
import { I18nService } from '../../../core/services/i18n.service';
import { QuotaNotificationService } from '../../../core/services/quota-notification.service';
import { IconComponent } from '../icon/icon.component';

/** Dismissable warning banner that appears when a plan quota limit is exceeded, with an upgrade CTA. */
@Component({
  selector: 'app-quota-banner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (quota.quotaError(); as error) {
      <div class="quota-banner" data-testid="quota-banner">
        <div class="quota-banner-content">
          <app-icon name="warning" />
          <div class="quota-banner-text">
            <strong>{{ i18n.t('quota_exceeded_title') }}</strong>
            <span>{{ message() }}</span>
          </div>
        </div>
        <div class="quota-banner-actions">
          <button class="quota-btn-upgrade" (click)="goToPlans()" data-testid="quota-upgrade-btn">
            <app-icon name="arrowUpLine" />
            {{ i18n.t('quota_upgrade') }}
          </button>
          <button class="quota-btn-dismiss" (click)="quota.dismiss()" data-testid="quota-dismiss-btn">
            <app-icon name="close" />
          </button>
        </div>
      </div>
    }
  `,
  styles: [`
    .quota-banner {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      padding: 12px 20px;
      background: color-mix(in srgb, var(--warning) 12%, var(--bg-2));
      border-bottom: 1px solid color-mix(in srgb, var(--warning) 30%, var(--border));
    }
    .quota-banner-content {
      display: flex;
      align-items: center;
      gap: 12px;
      color: var(--warning);
    }
    .quota-banner-text {
      display: flex;
      flex-direction: column;
      gap: 2px;
      font-size: 13px;
      color: var(--fg);
    }
    .quota-banner-text strong {
      font-size: 14px;
    }
    .quota-banner-actions {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .quota-btn-upgrade {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 6px 14px;
      border: none;
      border-radius: 6px;
      background: var(--accent);
      color: #fff;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
      white-space: nowrap;
    }
    .quota-btn-upgrade:hover { opacity: 0.9; }
    .quota-btn-dismiss {
      display: flex;
      align-items: center;
      padding: 4px;
      border: none;
      background: none;
      color: var(--fg-muted);
      cursor: pointer;
      border-radius: 4px;
    }
    .quota-btn-dismiss:hover { background: var(--bg); }
  `],
})
export class QuotaBannerComponent {
  protected readonly i18n = inject(I18nService);
  protected readonly quota = inject(QuotaNotificationService);
  private readonly router = inject(Router);

  readonly message = computed(() => {
    const error = this.quota.quotaError();
    if (!error) return '';
    switch (error.limitType) {
      case 'EMAIL_ACCOUNT':
        return this.i18n.t('quota_email_account', { max: '' + error.maxAllowed });
      case 'AUTOMATION':
        return this.i18n.t('quota_automation', { max: '' + error.maxAllowed });
      case 'AI_TOKEN':
      case 'AI_COST':
        return error.maxAllowed === 0
          ? this.i18n.t('quota_ai_disabled')
          : this.i18n.t('quota_ai_token');
      default:
        return error.message;
    }
  });

  goToPlans(): void {
    this.quota.dismiss();
    this.router.navigate(['/dashboard/plans']);
  }
}
