import { Bi } from './landing.util';

/** A marketplace preview card on the landing page. */
export interface MarketCard {
  cat: string;
  title: Bi;
  desc: Bi;
  by: string;
  av: string;
  grad: string;
  rating: string;
  installs: string;
  price: number;
}

/** Six sample marketplace listings shown in the preview grid. */
export const LP2_MKT_CARDS: MarketCard[] = [
  { cat: 'Finance', title: { de: 'Rechnungs-Workflow', en: 'Invoice workflow' }, desc: { de: 'Erkennt Rechnungen, extrahiert Beträge, leitet an die Buchhaltung weiter und legt sie in DATEV ab.', en: 'Detects invoices, extracts amounts, forwards to accounting and files them in DATEV.' }, by: 'Marta S.', av: 'MS', grad: 'linear-gradient(135deg,#fde7c8,#d4a574)', rating: '4.9', installs: '3.214', price: 0 },
  { cat: 'Support', title: { de: 'Support-Triage mit KI', en: 'AI support triage' }, desc: { de: 'Beantwortet häufige Fragen automatisch, eskaliert komplexe Fälle mit Zusammenfassung an Ihr Team.', en: 'Answers common questions automatically, escalates complex cases to your team with a summary.' }, by: 'David B.', av: 'DB', grad: 'linear-gradient(135deg,#cfe9d6,#7eb692)', rating: '4.8', installs: '2.402', price: 12 },
  { cat: 'Sales', title: { de: 'Lead-Router für HubSpot', en: 'Lead router for HubSpot' }, desc: { de: 'Qualifiziert eingehende Anfragen, legt Deals an und benachrichtigt den richtigen Vertriebler in Slack.', en: 'Qualifies inbound requests, creates deals and pings the right rep in Slack.' }, by: 'Aylin K.', av: 'AK', grad: 'linear-gradient(135deg,#e7d4f0,#b48dc6)', rating: '4.7', installs: '1.876', price: 19 },
  { cat: 'HR', title: { de: 'Bewerbungs-Pipeline', en: 'Hiring pipeline' }, desc: { de: 'Sortiert Bewerbungen, plant Erstgespräche und hält Kandidaten automatisch auf dem Laufenden.', en: 'Sorts applications, schedules first calls and keeps candidates updated automatically.' }, by: 'Jonas W.', av: 'JW', grad: 'linear-gradient(135deg,#e8e0d2,#a89e8a)', rating: '4.9', installs: '986', price: 0 },
  { cat: 'E-Commerce', title: { de: 'Versand-Updates', en: 'Shipping updates' }, desc: { de: 'Informiert Kunden proaktiv über Versandstatus und beantwortet "Wo ist mein Paket?"-Anfragen.', en: 'Proactively informs customers about shipping status and answers "where is my order?" emails.' }, by: 'Lena H.', av: 'LH', grad: 'linear-gradient(135deg,#c8e4f0,#7fa8c9)', rating: '4.6', installs: '1.241', price: 9 },
  { cat: 'Reporting', title: { de: 'Wochen-Digest', en: 'Weekly digest' }, desc: { de: 'Sammelt Kennzahlen aus Ihren Tools und schickt montags einen kompakten Bericht an die Geschäftsführung.', en: 'Collects metrics from your tools and sends a compact Monday report to leadership.' }, by: 'Tom R.', av: 'TR', grad: 'linear-gradient(135deg,#f0d4d4,#c98d8d)', rating: '4.8', installs: '2.109', price: 0 },
];
