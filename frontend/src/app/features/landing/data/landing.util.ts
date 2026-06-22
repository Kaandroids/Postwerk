/** Bilingual string pair used across landing v2 static data. */
export interface Bi {
  de: string;
  en: string;
}

export type Lang = 'de' | 'en';

/** Trigger-type badge identifiers used by the `.lp2-via` badge system. */
export type ViaType = 'webhook' | 'email' | 'schedule' | 'manual';

/** Result-tag colour keys used by `.lp2-tag[data-act]`. */
export type ActType = 'forward' | 'reply' | 'flag' | 'archive';

/** i18n key for each trigger-badge label. */
export const VIA_KEY: Record<ViaType, string> = {
  webhook: 'p2_via_webhook',
  email: 'p2_via_email',
  schedule: 'p2_via_schedule',
  manual: 'p2_via_manual',
};

/** Pick the localized value of a bilingual pair, falling back to English. */
export function pickLang(value: Bi, lang: Lang): string {
  return value[lang] || value.en;
}
