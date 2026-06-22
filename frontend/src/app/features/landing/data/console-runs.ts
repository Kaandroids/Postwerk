import { ActType, Bi, ViaType } from './landing.util';

/** A single automation run shown cycling through the hero Autopilot console. */
export interface ConsoleRun {
  from: string;
  init: string;
  grad: string;
  via: ViaType;
  subj: Bi;
  chip: Bi;
  action: Bi;
  tag: Bi;
  act: ActType;
}

/** Hero console event cycle. Order matters — first impression must NOT be email-only. */
export const LP2_RUNS: ConsoleRun[] = [
  {
    from: 'Shopify', init: 'SH', grad: 'linear-gradient(135deg,#cfe9d6,#7eb692)', via: 'webhook',
    subj: { de: 'order/created · #5512 · 612,00 €', en: 'order/created · #5512 · €612.00' },
    chip: { de: 'Bestellung · > 500 €', en: 'Order · > €500' },
    action: { de: '→ Notion-Eintrag erstellt, #sales benachrichtigt', en: '→ Notion entry created, #sales notified' },
    tag: { de: 'Ausgeführt', en: 'Executed' }, act: 'flag',
  },
  {
    from: 'Stripe', init: 'S', grad: 'linear-gradient(135deg,#fde7c8,#d4a574)', via: 'webhook',
    subj: { de: 'payment_intent.succeeded · 1.240,00 €', en: 'payment_intent.succeeded · €1,240.00' },
    chip: { de: 'Zahlung', en: 'Payment' },
    action: { de: '→ Rechnung erstellt, an Buchhaltung übergeben', en: '→ invoice created, handed to accounting' },
    tag: { de: 'Verbucht', en: 'Booked' }, act: 'forward',
  },
  {
    from: 'Maja S.', init: 'M', grad: 'linear-gradient(135deg,#e7d4f0,#b48dc6)', via: 'email',
    subj: { de: 'Wie kündige ich meinen Pro-Plan?', en: 'How do I cancel my Pro plan?' },
    chip: { de: 'Support-Anfrage', en: 'Support question' },
    action: { de: '→ Antwortentwurf erstellt, CRM aktualisiert', en: '→ reply drafted, CRM updated' },
    tag: { de: 'Beantwortet', en: 'Replied' }, act: 'reply',
  },
  {
    from: 'Cron · 07:00', init: '⏱', grad: 'linear-gradient(135deg,#d6dcf0,#8c9ac9)', via: 'schedule',
    subj: { de: 'Täglicher Umsatzreport', en: 'Daily revenue report' },
    chip: { de: 'Geplanter Lauf', en: 'Scheduled run' },
    action: { de: '→ Zahlen per API geholt, Report an Team gesendet', en: '→ numbers pulled via API, report sent to team' },
    tag: { de: 'Gesendet', en: 'Sent' }, act: 'forward',
  },
  {
    from: 'Typeform', init: 'TF', grad: 'linear-gradient(135deg,#c8e4f0,#7fa8c9)', via: 'webhook',
    subj: { de: 'Neue Bewerbung — Senior Designer', en: 'New application — Senior Designer' },
    chip: { de: 'Bewerbung', en: 'Application' },
    action: { de: '→ in HR-Pipeline einsortiert, Termin vorgeschlagen', en: '→ routed to HR pipeline, interview proposed' },
    tag: { de: 'Einsortiert', en: 'Routed' }, act: 'archive',
  },
];
