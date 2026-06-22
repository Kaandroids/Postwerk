import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { I18nService } from '../../../core/services/i18n.service';
import { AdminQuotaService } from '../../../core/services/admin-quota.service';
import { AdminIdentityService } from '../../../core/services/admin-identity.service';
import { IconComponent } from '../icon/icon.component';
import { QuotaTargetType } from '../../../models/admin-quota.model';

/**
 * Lightweight "Grant AI credit" overlay reused from the user and org detail modals.
 * The target is pre-bound (no picker) and the kind is fixed to CREDIT — it is a shortcut for the
 * full Quota Overrides page, POSTing to the same `/admin/quota-overrides` endpoint. Rendered as a
 * sibling overlay (its own scrim + centered card, z-index above the detail modal) so it is never
 * trapped by the detail modal's transform. Gating is QUOTA_OVERRIDE (UX only — backend enforces).
 */
@Component({
  selector: 'app-grant-credit-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  templateUrl: './grant-credit-dialog.component.html',
  styleUrl: './grant-credit-dialog.component.scss',
})
export class GrantCreditDialogComponent {
  protected i18n = inject(I18nService);
  protected identity = inject(AdminIdentityService);
  private quotaService = inject(AdminQuotaService);

  // ── Inputs: the pre-bound target ──────────────────────────────────────────
  targetType = input.required<QuotaTargetType>();
  targetId = input.required<string>();
  targetName = input.required<string>();
  /** Secondary line under the name — user email or org slug. */
  targetSub = input<string>('');
  /** Base monthly cap in cents (-1 unlimited, 0 disabled, >0 cap). null = unknown → credit-only preview. */
  baseCapCents = input<number | null>(null);
  plan = input<string | null>(null);

  // ── Outputs ───────────────────────────────────────────────────────────────
  close = output<void>();
  granted = output<{ amountCents: number; targetName: string }>();

  // ── Form state ──────────────────────────────────────────────────────────────
  amount = signal('');
  expiryDate = signal('');
  noExpiry = signal(true);
  reason = signal('');
  attempted = signal(false);
  saving = signal(false);
  error = signal('');

  readonly amountCents = computed(() => Math.round((parseFloat(this.amount()) || 0) * 100));
  readonly canGrant = computed(() => this.identity.has('QUOTA_OVERRIDE'));

  readonly baseUnlimited = computed(() => this.baseCapCents() === -1);
  readonly baseKnown = computed(() => {
    const c = this.baseCapCents();
    return c != null && c >= 0;
  });
  /** Effective monthly cap after the credit, in cents (null = base unknown). */
  readonly previewCents = computed(() =>
    this.baseKnown() ? (this.baseCapCents() as number) + this.amountCents() : null);

  readonly amountMissing = computed(() => !(this.amountCents() > 0));
  readonly reasonMissing = computed(() => !this.reason().trim());

  /** cents → whole-euro localized currency string; null → ∞. */
  euro(cents: number | null | undefined): string {
    if (cents == null) return '∞';
    const locale = this.i18n.lang() === 'de' ? 'de-DE' : 'en-US';
    return (cents / 100).toLocaleString(locale, { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 });
  }

  initial(): string {
    return (this.targetName()?.trim()?.[0] ?? '?').toUpperCase();
  }

  hue(): number {
    const s = this.targetName() ?? '';
    let h = 0;
    for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360;
    return h;
  }

  /** Today as YYYY-MM-DD (min for the date input). */
  get todayIso(): string {
    return new Date().toISOString().slice(0, 10);
  }

  onAmount(event: Event) { this.amount.set((event.target as HTMLInputElement).value); }
  onExpiry(event: Event) { this.expiryDate.set((event.target as HTMLInputElement).value); }
  onReason(event: Event) { this.reason.set((event.target as HTMLTextAreaElement).value); }

  toggleNoExpiry(event: Event) {
    const checked = (event.target as HTMLInputElement).checked;
    this.noExpiry.set(checked);
    if (checked) this.expiryDate.set('');
  }

  onScrim() {
    if (!this.saving()) this.close.emit();
  }

  submit() {
    if (!this.canGrant() || this.saving()) return;
    this.attempted.set(true);
    if (this.amountMissing() || this.reasonMissing()) return;

    this.saving.set(true);
    this.error.set('');
    this.quotaService.create({
      targetType: this.targetType(),
      targetId: this.targetId(),
      kind: 'CREDIT',
      amountCents: this.amountCents(),
      expiresAt: this.noExpiry() || !this.expiryDate() ? null : this.expiryDate(),
      reason: this.reason().trim(),
    }).subscribe({
      next: () => {
        this.saving.set(false);
        this.granted.emit({ amountCents: this.amountCents(), targetName: this.targetName() });
      },
      error: () => {
        this.saving.set(false);
        this.error.set(this.i18n.t('gcd_failed'));
      },
    });
  }
}
