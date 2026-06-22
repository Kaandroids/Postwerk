export interface UsageResponse {
  plan: PlanInfo;
  usage: UsageInfo;
  billingPeriod: BillingPeriod;
}

export interface PlanInfo {
  name: string;
  tokenLimit: number;
  automationLimit: number;
  emailAccountLimit: number;
  apiWebhookEnabled: boolean;
  costLimitCents: number;
}

export interface UsageInfo {
  tokensUsedThisMonth: number;
  activeAutomations: number;
  emailAccounts: number;
  /** AI cost this month truncated to whole cents (legacy; sub-cent usage reads 0). */
  costUsedCents: number;
  /** AI cost this month in raw micros (1 cent = 10,000 micros) — drives the limiter percentage. */
  costUsedMicros: number;
}

export interface BillingPeriod {
  start: string;
  end: string;
}

export interface QuotaError {
  status: number;
  limitType: 'EMAIL_ACCOUNT' | 'AUTOMATION' | 'AI_TOKEN' | 'AI_COST';
  currentUsage: number;
  maxAllowed: number;
  planName: string;
  message: string;
}
