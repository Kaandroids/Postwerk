export interface StatCard {
  key: string;
  labelKey: string;
  value: string;
  delta: number;
  up: boolean;
  spark: number[];
}

export interface Campaign {
  nameKey_de: string;
  nameKey_en: string;
  meta: string;
  meta_en?: string;
  status: 'live' | 'draft' | 'paused';
}

export interface FunnelStep {
  labelKey_de: string;
  labelKey_en: string;
  pct: number;
  value: string;
}

export interface ActivityItem {
  who: string;
  text_de: string;
  text_en: string;
  time: string;
  time_en: string;
}

import { OrgPermission } from '../../../models/organization.model';

export interface NavItem {
  key: string;
  labelKey: string;
  icon: string;
  badge?: number;
  children?: NavChild[];
  /** Org permission required to see this item; omitted = always visible. */
  perm?: OrgPermission;
}

export interface NavChild {
  key: string;
  labelKey: string;
  badge?: number;
  /** Org permission required to see this child; omitted = always visible. */
  perm?: OrgPermission;
}

export const STATS: StatCard[] = [
  { key: 'sent', labelKey: 'stat_sent', value: '184.220', delta: 12.4, up: true, spark: [10, 12, 14, 11, 16, 18, 17, 22, 21, 24, 28, 30] },
  { key: 'open', labelKey: 'stat_open', value: '48,2%', delta: 3.1, up: true, spark: [40, 42, 41, 44, 43, 46, 45, 47, 46, 48, 47, 48] },
  { key: 'click', labelKey: 'stat_click', value: '12,8%', delta: -0.7, up: false, spark: [14, 13, 14, 12, 13, 14, 13, 12, 13, 12, 12, 12] },
  { key: 'subscribers', labelKey: 'stat_subscribers', value: '42.108', delta: 8.2, up: true, spark: [30, 32, 33, 35, 34, 36, 38, 39, 40, 41, 42, 42] },
];

export const CHART_DATA = {
  sent: [3200, 3800, 4100, 3600, 4500, 5200, 4800, 5100, 5800, 6200, 5900, 6800, 7200, 7600],
  delivered: [3000, 3700, 3900, 3500, 4300, 5000, 4700, 4900, 5600, 6000, 5700, 6500, 7000, 7400],
};

export const CAMPAIGNS: Campaign[] = [
  { nameKey_de: 'Q4 Produkt-Launch', nameKey_en: 'Q4 Product Launch', meta: '42.108 · 2h', status: 'live' },
  { nameKey_de: 'Webinar-Erinnerung', nameKey_en: 'Webinar Reminder', meta: '12.480 · gestern', meta_en: '12,480 · yesterday', status: 'live' },
  { nameKey_de: 'Onboarding-Sequenz', nameKey_en: 'Onboarding Sequence', meta: 'Automation', status: 'live' },
  { nameKey_de: 'Schwarzer Freitag — Entwurf', nameKey_en: 'Black Friday — Draft', meta: '0 · —', status: 'draft' },
  { nameKey_de: 'Reaktivierung Wave 3', nameKey_en: 'Re-engagement Wave 3', meta: '8.200 · pausiert', meta_en: '8,200 · paused', status: 'paused' },
];

export const FUNNEL: FunnelStep[] = [
  { labelKey_de: 'Versendet', labelKey_en: 'Sent', pct: 100, value: '184.220' },
  { labelKey_de: 'Zugestellt', labelKey_en: 'Delivered', pct: 98, value: '180.617' },
  { labelKey_de: 'Geöffnet', labelKey_en: 'Opened', pct: 48, value: '88.426' },
  { labelKey_de: 'Geklickt', labelKey_en: 'Clicked', pct: 13, value: '23.580' },
  { labelKey_de: 'Konvertiert', labelKey_en: 'Converted', pct: 4, value: '7.368' },
];

export const ACTIVITY: ActivityItem[] = [
  { who: 'AS', text_de: 'Anna hat die Kampagne <b>Q4 Produkt-Launch</b> versendet.', text_en: 'Anna sent the <b>Q4 Product Launch</b> campaign.', time: 'vor 12 Min', time_en: '12 min ago' },
  { who: 'MK', text_de: 'Mira hat <b>Onboarding-Sequenz</b> aktualisiert.', text_en: 'Mira updated <b>Onboarding Sequence</b>.', time: 'vor 1 Std', time_en: '1 hr ago' },
  { who: 'JT', text_de: 'Jonas hat 412 Kontakte importiert.', text_en: 'Jonas imported 412 contacts.', time: 'vor 3 Std', time_en: '3 hrs ago' },
  { who: 'AS', text_de: 'Anna hat einen neuen Workspace erstellt.', text_en: 'Anna created a new workspace.', time: 'gestern', time_en: 'yesterday' },
];
