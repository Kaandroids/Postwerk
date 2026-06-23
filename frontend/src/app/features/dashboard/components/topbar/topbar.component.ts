import { ChangeDetectionStrategy, Component, DestroyRef, inject, computed, effect, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { I18nService } from '../../../../core/services/i18n.service';
import { LayoutService } from '../../../../core/services/layout.service';
import { AiChatService } from '../../../../core/services/ai-chat.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { PlanService } from '../../../../core/services/plan.service';
import { QuotaNotificationService } from '../../../../core/services/quota-notification.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { ThemeToggleComponent } from '../../../../shared/components/theme-toggle/theme-toggle.component';
import { AccountSwitcherComponent } from '../../../../shared/components/account-switcher/account-switcher.component';
import { OrgSwitcherComponent } from '../../../../shared/components/org-switcher/org-switcher.component';
import { NotificationCenterComponent } from '../../../notifications/notification-center/notification-center.component';

/** Dashboard top bar with sidebar toggle, breadcrumb, search, AI chat trigger, and user controls. */
@Component({
  selector: 'app-topbar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, ThemeToggleComponent, AccountSwitcherComponent, OrgSwitcherComponent, NotificationCenterComponent],
  template: `
    <div class="dash-topbar">
      <button class="dash-icon-btn" aria-label="Menu" (click)="layout.toggleSidebar()">
        <app-icon name="menu" />
      </button>
      @if (!isAdminMode()) {
      <div class="dash-search">
        <span class="dash-search-icon"><app-icon name="search" /></span>
        <input type="text" autocomplete="nope" name="dash-search-nofill" [placeholder]="i18n.t('dash_search_placeholder')" />
        <span class="dash-kbd">⌘K</span>
      </div>
      <div class="dash-workspace-group">
        <app-org-switcher />
        <app-account-switcher />
        @if (showLimiter()) {
          <div class="ai-limiter" data-testid="topbar-ai-limiter" (click)="goToPlans()">
            <div class="ai-limiter-head">
              <span class="ai-limiter-label">AI Limit</span>
              <span class="ai-limiter-right">
                @if (isUnlimited()) {
                  <span class="ai-limiter-value">∞</span>
                } @else {
                  <span class="ai-limiter-value" [style.color]="limiterColor()">{{ limiterPercentLabel() }}%</span>
                }
                <button class="ai-limiter-refresh" data-testid="topbar-ai-refresh"
                        [attr.data-spin]="manualRefreshing() ? '1' : '0'" [disabled]="manualRefreshing()"
                        [attr.aria-label]="i18n.t('an_refresh')" [attr.title]="i18n.t('an_refresh')"
                        (click)="refreshUsage($event)">
                  <app-icon name="refresh" />
                </button>
              </span>
            </div>
            @if (!isUnlimited()) {
              <div class="ai-limiter-track" role="progressbar" [attr.aria-valuenow]="limiterPercent()" [attr.aria-valuemin]="0" [attr.aria-valuemax]="100" aria-label="AI usage">
                <div class="ai-limiter-fill" [style.width.%]="limiterFillPercent()" [style.background]="limiterColor()"></div>
              </div>
            }
          </div>
        }
      </div>
      }
      <div class="dash-topbar-actions">
        @if (isAdmin()) {
          <button
            class="dash-icon-btn dash-admin-toggle"
            [attr.aria-label]="isAdminMode() ? i18n.t('admin_switch_to_user') : i18n.t('admin_switch_to_admin')"
            [attr.title]="isAdminMode() ? i18n.t('admin_switch_to_user') : i18n.t('admin_switch_to_admin')"
            [attr.data-active]="isAdminMode() ? '1' : '0'"
            (click)="toggleAdminMode()"
          >
            <app-icon name="shield" />
          </button>
        }
        <button class="dash-icon-btn" [class.dash-ai-locked]="aiDisabled()" aria-label="AI Assistant" (click)="toggleAi()" [attr.data-active]="chat.isOpen() ? '1' : '0'" data-testid="topbar-ai-btn">
          <app-icon name="sparkle" />
          @if (aiDisabled()) {
            <span class="dash-lock-badge"><app-icon name="lock" /></span>
          }
        </button>
        <app-theme-toggle class="dash-hide-lg" />
        <button class="dash-icon-btn dash-hide-md" aria-label="Help"><app-icon name="help" /></button>
        <app-notification-center />
      </div>
    </div>
  `,
  styleUrl: './topbar.component.scss',
})
export class TopbarComponent {
  protected i18n = inject(I18nService);
  protected layout = inject(LayoutService);
  protected chat = inject(AiChatService);
  private adminIdentity = inject(AdminIdentityService);
  private planService = inject(PlanService);
  private quotaNotification = inject(QuotaNotificationService);
  private router = inject(Router);

  private currentUrl = signal(this.router.url);

  /** Spins the manual-refresh icon only for user-initiated refreshes (not nav-triggered reloads). */
  protected manualRefreshing = signal(false);

  /** Shared usage signal (populated by PlanService.loadUsage); null until first load / on error. */
  private usage = this.planService.usage;

  private costLimitCents = computed(() => this.usage()?.plan.costLimitCents ?? null);

  // "Is staff" (server-derived) gates the admin-mode toggle, so SUPPORT/BILLING/etc. (platform role
  // USER) also see it. Loaded once via GET /admin/me; non-staff get 403 → toggle stays hidden.
  isAdmin = computed(() => this.adminIdentity.isStaff());
  isAdminMode = computed(() => this.currentUrl().includes('/admin'));
  aiDisabled = computed(() => this.costLimitCents() === 0);

  showLimiter = computed(() => {
    const limit = this.costLimitCents();
    return limit !== null && limit !== 0;
  });

  isUnlimited = computed(() => this.costLimitCents() === -1);

  /** Unrounded usage percentage — single source of truth in PlanService (shared with analytics). */
  private rawPercent = computed(() => this.planService.costUsagePercent() ?? 0);

  limiterPercent = computed(() => Math.min(Math.round(this.rawPercent()), 100));

  /** Display label: "<1" when there is real but sub-1% usage (so it never falsely reads 0). */
  limiterPercentLabel = computed(() => {
    const raw = this.rawPercent();
    if (raw > 0 && raw < 1) return '<1';
    return String(this.limiterPercent());
  });

  /** Bar fill width — give any non-zero usage a minimum visible sliver so the bar moves off empty. */
  limiterFillPercent = computed(() => {
    const raw = this.rawPercent();
    if (raw > 0 && raw < 2) return 2;
    return this.limiterPercent();
  });

  limiterColor = computed(() => {
    const pct = this.limiterPercent();
    if (pct >= 95) return 'var(--danger)';
    if (pct >= 80) return 'var(--warning)';
    return 'var(--accent)';
  });

  private destroyRef = inject(DestroyRef);

  constructor() {
    // Clear the manual-refresh spinner once the shared usage load settles.
    effect(() => { if (!this.planService.loadingUsage() && this.manualRefreshing()) this.manualRefreshing.set(false); });

    // Resolve staff identity once so the admin toggle can appear (errors for non-staff are expected).
    this.adminIdentity.load().subscribe({ error: () => {} });

    // Load AI usage now, then re-pull on every navigation. Automation runs spend AI server-side and
    // can't notify the SPA, so refreshing on navigation keeps the limiter honest as the user moves
    // around (chat spend triggers an immediate refresh from AiChatService — see loadUsage there).
    this.planService.loadUsage();

    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(e => {
      this.currentUrl.set(e.urlAfterRedirects);
      this.planService.loadUsage();
    });
  }

  toggleAi(): void {
    if (this.aiDisabled()) {
      this.quotaNotification.show({
        status: 429,
        limitType: 'AI_COST',
        currentUsage: 0,
        maxAllowed: 0,
        planName: 'Starter',
        message: 'AI features not available on current plan',
      });
      return;
    }
    this.chat.toggleOpen();
  }

  toggleAdminMode(): void {
    if (this.isAdminMode()) {
      this.router.navigate(['/dashboard']);
    } else {
      this.router.navigate(['/dashboard/admin']);
    }
  }

  goToPlans(): void {
    this.router.navigate(['/dashboard/plans']);
  }

  /** Manually re-pull AI usage (does not navigate to plans). */
  refreshUsage(event: Event): void {
    event.stopPropagation();
    if (this.planService.loadingUsage()) return;
    this.manualRefreshing.set(true);
    this.planService.loadUsage();
  }
}
