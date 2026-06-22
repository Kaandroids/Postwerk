import { ActType, Bi, ViaType } from './landing.util';

/** One run row shown under a studio template's generated automation. */
export interface StudioSample {
  from: string;
  via: ViaType;
  subj: Bi;
  tag: Bi;
  act: ActType;
}

/** A use-case studio template: a natural-language rule plus example runs. */
export interface StudioRule {
  id: string;
  icon: string;
  label: Bi;
  prompt: { a: Bi; kw: Bi; b: Bi; act: Bi; c: Bi };
  samples: StudioSample[];
}

/** Use-cases studio templates (webhook / email / schedule / form triggers). */
export const LP2_RULES: StudioRule[] = [
  {
    id: 'orders',
    icon: 'M6 2L3 6v14a2 2 0 002 2h14a2 2 0 002-2V6l-3-4zM3 6h18M16 10a4 4 0 01-8 0',
    label: { de: 'Bestellungen verarbeiten', en: 'Process orders' },
    prompt: {
      a: { de: 'Wenn eine ', en: 'When a ' },
      kw: { de: 'Shopify-Bestellung', en: 'Shopify order' },
      b: { de: ' eingeht, ', en: ' comes in, ' },
      act: { de: 'erstelle das Versandlabel per API', en: 'create the shipping label via API' },
      c: { de: ' und trage die Sendung in Google Sheets ein.', en: ' and log the shipment in Google Sheets.' },
    },
    samples: [
      { from: 'Shopify', via: 'webhook', subj: { en: 'order/created · #5519 · €89.00', de: 'order/created · #5519 · 89,00 €' }, tag: { de: 'Label erstellt', en: 'Label created' }, act: 'flag' },
      { from: 'Shopify', via: 'webhook', subj: { en: 'order/created · #5518 · €342.50', de: 'order/created · #5518 · 342,50 €' }, tag: { de: 'Label erstellt', en: 'Label created' }, act: 'flag' },
      { from: 'Shopify', via: 'webhook', subj: { en: 'order/created · #5517 · €1,204.00', de: 'order/created · #5517 · 1.204,00 €' }, tag: { de: 'Label erstellt', en: 'Label created' }, act: 'flag' },
    ],
  },
  {
    id: 'invoices',
    icon: 'M9 11h6M9 15h4M5 7h14a2 2 0 012 2v10a2 2 0 01-2 2H5a2 2 0 01-2-2V9a2 2 0 012-2z',
    label: { de: 'Rechnungen ablegen', en: 'File invoices' },
    prompt: {
      a: { de: 'Wenn eine ', en: 'When an ' },
      kw: { de: 'Rechnung', en: 'invoice' },
      b: { de: ' per E-Mail eintrifft, ', en: ' arrives by email, ' },
      act: { de: 'leite sie an die Buchhaltung weiter', en: 'forward it to accounting' },
      c: { de: ' und lege sie in DATEV ab.', en: ' and file it in DATEV.' },
    },
    samples: [
      { from: 'Stripe', via: 'email', subj: { en: 'Invoice #INV-4821 · €1,240.00 due Apr 30', de: 'Rechnung #INV-4821 · 1.240,00 € fällig 30.04.' }, tag: { de: 'Weitergeleitet', en: 'Forwarded' }, act: 'forward' },
      { from: 'Vercel', via: 'email', subj: { en: 'Your monthly receipt is ready', de: 'Ihre Monatsquittung ist bereit' }, tag: { de: 'Weitergeleitet', en: 'Forwarded' }, act: 'forward' },
      { from: 'AWS Billing', via: 'email', subj: { en: 'Account 8821 — March charges', de: 'Konto 8821 — Märzgebühren' }, tag: { de: 'Weitergeleitet', en: 'Forwarded' }, act: 'forward' },
    ],
  },
  {
    id: 'reports',
    icon: 'M12 2a10 10 0 110 20 10 10 0 010-20zM12 6v6l4 2',
    label: { de: 'Reports automatisch senden', en: 'Send reports automatically' },
    prompt: {
      a: { de: 'Jeden ', en: 'Every ' },
      kw: { de: 'Montag um 07:00', en: 'Monday at 7:00' },
      b: { de: ', ', en: ', ' },
      act: { de: 'hole die Kennzahlen per API', en: 'pull the metrics via API' },
      c: { de: ' und sende den Wochenreport an #leadership.', en: ' and send the weekly report to #leadership.' },
    },
    samples: [
      { from: 'Cron · Mo 07:00', via: 'schedule', subj: { en: 'Weekly report · CW 24', de: 'Wochenreport · KW 24' }, tag: { de: 'Gesendet', en: 'Sent' }, act: 'forward' },
      { from: 'Cron · Mo 07:00', via: 'schedule', subj: { en: 'Weekly report · CW 23', de: 'Wochenreport · KW 23' }, tag: { de: 'Gesendet', en: 'Sent' }, act: 'forward' },
      { from: 'Cron · Mo 07:00', via: 'schedule', subj: { en: 'Weekly report · CW 22', de: 'Wochenreport · KW 22' }, tag: { de: 'Gesendet', en: 'Sent' }, act: 'forward' },
    ],
  },
  {
    id: 'manual',
    icon: 'M8 5v14l11-7z',
    label: { de: 'Manuell starten', en: 'Run manually' },
    prompt: {
      a: { de: 'Wenn ich die Automation ', en: 'When I ' },
      kw: { de: 'manuell starte', en: 'run it manually' },
      b: { de: ', ', en: ', ' },
      act: { de: 'verarbeite die markierten E-Mails', en: 'process the flagged emails' },
      c: { de: ' und erstelle eine Zusammenfassung.', en: ' and build a summary.' },
    },
    samples: [
      { from: 'Inbox', via: 'manual', subj: { en: '12 flagged invoices', de: '12 markierte Rechnungen' }, tag: { de: 'Exportiert', en: 'Exported' }, act: 'archive' },
      { from: 'Inbox', via: 'manual', subj: { en: 'Weekly digest', de: 'Wochen-Report' }, tag: { de: 'Erstellt', en: 'Created' }, act: 'flag' },
      { from: 'Inbox', via: 'manual', subj: { en: '30 open requests', de: '30 offene Anfragen' }, tag: { de: 'Sortiert', en: 'Sorted' }, act: 'archive' },
    ],
  },
  {
    id: 'support',
    icon: 'M21 11.5a8.38 8.38 0 01-.9 3.8 8.5 8.5 0 01-7.6 4.7 8.38 8.38 0 01-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 01-.9-3.8 8.5 8.5 0 014.7-7.6 8.38 8.38 0 013.8-.9h.5a8.48 8.48 0 018 8v.5z',
    label: { de: 'Support-Anfragen beantworten', en: 'Answer support requests' },
    prompt: {
      a: { de: 'Wenn eine ', en: 'When a ' },
      kw: { de: 'Support-Anfrage', en: 'support request' },
      b: { de: ' eingeht, ', en: ' comes in, ' },
      act: { de: 'entwirf eine Antwort', en: 'draft a reply' },
      c: { de: ' und aktualisiere den Kontakt im CRM.', en: ' and update the contact in the CRM.' },
    },
    samples: [
      { from: 'Maja S.', via: 'email', subj: { en: 'How do I cancel my Pro plan?', de: 'Wie kündige ich meinen Pro-Plan?' }, tag: { de: 'Antwort entworfen', en: 'Reply drafted' }, act: 'reply' },
      { from: 'Tomás P.', via: 'email', subj: { en: 'Refund question — order #2241', de: 'Erstattungsfrage — Bestellung #2241' }, tag: { de: 'Antwort entworfen', en: 'Reply drafted' }, act: 'reply' },
      { from: 'Lina B.', via: 'email', subj: { en: 'Pricing for 25 seats?', de: 'Preise für 25 Plätze?' }, tag: { de: 'Antwort entworfen', en: 'Reply drafted' }, act: 'reply' },
    ],
  },
];
