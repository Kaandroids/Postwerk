import { ChangeDetectionStrategy, Component, inject, signal, computed, afterNextRender } from '@angular/core';
import { I18nService } from '../../../../core/services/i18n.service';
import { UserService } from '../../../../core/services/user.service';
import { PlanService } from '../../../../core/services/plan.service';
import { UsageResponse } from '../../../../models/usage.model';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { PageContentComponent } from '../page-content/page-content.component';

interface PlanDef {
  id: string;
  name: string;
  icon: string;
  tone: string;
  priceM: number | null;
  priceY: number | null;
  taglineKey: string;
  featureKeys: string[];
  recommended?: boolean;
}

interface AddonDef {
  id: string;
  titleKey: string;
  descKey: string;
  price: number;
  unitKey: string;
}

interface InvoiceDef {
  id: string;
  date: string;
  amount: number;
  plan: string;
}

interface UsageItem {
  key: string;
  labelKey: string;
  used: number;
  of: number;
}

const PLANS: PlanDef[] = [
  {
    id: 'starter', name: 'Starter', icon: 'seed', tone: 'sage',
    priceM: 0, priceY: 0,
    taglineKey: 'plans_starter_tagline',
    featureKeys: ['plans_starter_f1', 'plans_starter_f2', 'plans_starter_f3', 'plans_starter_f4'],
  },
  {
    id: 'pro', name: 'Pro', icon: 'bolt', tone: 'ochre',
    priceM: 19, priceY: 190,
    taglineKey: 'plans_pro_tagline',
    featureKeys: ['plans_pro_f1', 'plans_pro_f2', 'plans_pro_f3', 'plans_pro_f4', 'plans_pro_f5', 'plans_pro_f6'],
  },
  {
    id: 'business', name: 'Business', icon: 'spark', tone: 'terracotta',
    priceM: 49, priceY: 490,
    taglineKey: 'plans_business_tagline',
    featureKeys: ['plans_business_f1', 'plans_business_f2', 'plans_business_f3', 'plans_business_f4', 'plans_business_f5', 'plans_business_f6'],
    recommended: true,
  },
  {
    id: 'enterprise', name: 'Enterprise', icon: 'building', tone: 'indigo',
    priceM: null, priceY: null,
    taglineKey: 'plans_enterprise_tagline',
    featureKeys: ['plans_enterprise_f1', 'plans_enterprise_f2', 'plans_enterprise_f3', 'plans_enterprise_f4', 'plans_enterprise_f5'],
  },
];

const ADDONS: AddonDef[] = [
  { id: 'extra-mailbox', titleKey: 'plans_addon_mailbox_title', descKey: 'plans_addon_mailbox_desc', price: 4, unitKey: 'plans_addon_mailbox_unit' },
  { id: 'ai-credits', titleKey: 'plans_addon_ai_title', descKey: 'plans_addon_ai_desc', price: 12, unitKey: 'plans_addon_ai_unit' },
];

const INVOICES: InvoiceDef[] = [
  { id: 'INV-2026-04', date: '2026-04-14', amount: 19.00, plan: 'Pro · Monatlich' },
  { id: 'INV-2026-03', date: '2026-03-14', amount: 19.00, plan: 'Pro · Monatlich' },
  { id: 'INV-2026-02', date: '2026-02-14', amount: 19.00, plan: 'Pro · Monatlich' },
  { id: 'INV-2026-01', date: '2026-01-14', amount: 19.00, plan: 'Pro · Monatlich' },
  { id: 'INV-2025-12', date: '2025-12-14', amount: 19.00, plan: 'Pro · Monatlich' },
  { id: 'INV-2025-11', date: '2025-11-14', amount: 19.00, plan: 'Pro · Monatlich' },
];

const CURRENT = {
  planId: 'pro',
  cycle: 'monthly' as const,
  startedAt: '2025-09-14',
  renewsAt: '2026-05-14',
  usage: {
    mailboxes: { used: 4, of: 5 },
    replies: { used: 1247, of: 2000 },
    ai: { used: 312, of: 500 },
  },
  paymentMethod: { brand: 'Visa', last4: '4242' },
};

const PLAN_ORDER = ['starter', 'pro', 'business', 'enterprise'];

/** Subscription plans page showing plan comparison cards, current usage meters, billing history, and add-ons. */
@Component({
  selector: 'app-plans-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, PageContentComponent],
  templateUrl: './plans-page.component.html',
  styleUrl: './plans-page.component.scss',
})
export class PlansPageComponent {
  protected readonly i18n = inject(I18nService);
  private readonly userService = inject(UserService);
  private readonly planService = inject(PlanService);

  readonly plans = PLANS;
  readonly addons = ADDONS;
  readonly invoices = INVOICES;
  readonly current = CURRENT;

  readonly cycle = signal<'monthly' | 'yearly'>('monthly');
  readonly currentPlanId = signal(CURRENT.planId);
  readonly usageBarWidths = signal<Record<string, number>>({ mailboxes: 0, automations: 0, ai: 0 });
  readonly usageData = signal<UsageResponse | null>(null);

  readonly usageItems = computed<UsageItem[]>(() => {
    const data = this.usageData();
    if (data) {
      return [
        { key: 'mailboxes', labelKey: 'plans_usage_mailboxes', used: data.usage.emailAccounts, of: data.plan.emailAccountLimit },
        { key: 'automations', labelKey: 'plans_usage_automations', used: data.usage.activeAutomations, of: data.plan.automationLimit },
        { key: 'ai', labelKey: 'plans_usage_ai', used: data.usage.tokensUsedThisMonth, of: data.plan.tokenLimit },
      ];
    }
    return [
      { key: 'mailboxes', labelKey: 'plans_usage_mailboxes', used: CURRENT.usage.mailboxes.used, of: CURRENT.usage.mailboxes.of },
      { key: 'automations', labelKey: 'plans_usage_automations', used: 0, of: 3 },
      { key: 'ai', labelKey: 'plans_usage_ai', used: CURRENT.usage.ai.used, of: CURRENT.usage.ai.of },
    ];
  });

  readonly currentPlan = computed(() => PLANS.find(p => p.id === this.currentPlanId()));
  readonly currentRank = computed(() => PLAN_ORDER.indexOf(this.currentPlanId()));

  readonly daysLeft = computed(() => {
    const data = this.usageData();
    if (data) {
      const diff = new Date(data.billingPeriod.end).getTime() - Date.now();
      return Math.max(0, Math.ceil(diff / 86400000));
    }
    const diff = new Date(CURRENT.renewsAt).getTime() - Date.now();
    return Math.max(0, Math.ceil(diff / 86400000));
  });

  constructor() {
    this.planService.getUsage().subscribe({
      next: (usage) => {
        this.usageData.set(usage);
        this.currentPlanId.set(usage.plan.name.toLowerCase());
      },
      error: () => {},
    });

    afterNextRender(() => {
      setTimeout(() => this.updateBarWidths(), 80);
    });
  }

  private updateBarWidths(): void {
    const items = this.usageItems();
    const widths: Record<string, number> = {};
    for (const u of items) {
      widths[u.key] = u.of === -1 ? 100 : this.pct(u.used, u.of);
    }
    this.usageBarWidths.set(widths);
  }

  toggleCycle(c: 'monthly' | 'yearly') {
    this.cycle.set(c);
  }

  fmtPrice(v: number | null): string {
    if (v == null) return this.i18n.t('plans_on_request');
    if (v === 0) return this.i18n.t('plans_free');
    const lang = this.i18n.lang();
    return lang === 'de' ? `${v} €` : `€${v}`;
  }

  fmtDate(iso: string): string {
    const lang = this.i18n.lang();
    return new Date(iso).toLocaleDateString(lang === 'de' ? 'de-DE' : 'en-US', { day: '2-digit', month: 'short', year: 'numeric' });
  }

  pct(used: number, of: number): number {
    if (of === 0) return 0;
    return Math.min(100, Math.round((used / of) * 100));
  }

  fmtNum(n: number): string {
    const lang = this.i18n.lang();
    return n.toLocaleString(lang === 'de' ? 'de-DE' : 'en-US');
  }

  planPrice(plan: PlanDef): number | null {
    return this.cycle() === 'monthly' ? plan.priceM : plan.priceY;
  }

  priceUnit(price: number | null): string {
    if (price == null) return '';
    return this.cycle() === 'monthly' ? this.i18n.t('plans_per_month') : this.i18n.t('plans_per_year');
  }

  bannerPriceUnit(): string {
    return this.cycle() === 'monthly' ? this.i18n.t('plans_per_month') : this.i18n.t('plans_per_year');
  }

  planRank(planId: string): number {
    return PLAN_ORDER.indexOf(planId);
  }

  ctaLabel(plan: PlanDef): string {
    if (plan.id === this.currentPlanId()) return this.i18n.t('plans_current_plan');
    if (plan.id === 'enterprise') return this.i18n.t('plans_contact_sales');
    return this.planRank(plan.id) > this.currentRank()
      ? this.i18n.t('plans_upgrade_btn')
      : this.i18n.t('plans_downgrade_btn');
  }

  ctaClass(plan: PlanDef): string {
    if (plan.id === this.currentPlanId()) return 'pl-btn pl-btn-block pl-btn-disabled';
    if (this.planRank(plan.id) > this.currentRank()) return 'pl-btn pl-btn-block pl-btn-primary';
    return 'pl-btn pl-btn-block pl-btn-secondary';
  }

  ctaIcon(plan: PlanDef): string | null {
    if (plan.id === this.currentPlanId() || plan.id === 'enterprise') return null;
    return this.planRank(plan.id) > this.currentRank() ? 'arrowUpLine' : 'arrowDownLine';
  }

  isWarn(used: number, of: number): boolean {
    if (of <= 0) return false;
    return this.pct(used, of) >= 80;
  }
}
