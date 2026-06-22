import { NodeType } from '../../models/automation.model';
import { Bi } from '../../shared/data/node-docs.model';
import { NODE_DOC_ORDER } from '../../shared/data/node-docs.data';

export type DocAudience = 'user' | 'admin' | 'dev';

export interface DocArticleRef {
  /** Route slug under /docs, e.g. `getting-started/welcome` or `nodes/webhook`. */
  slug: string;
  /** Static title (prose articles). Node pages derive their title from the palette. */
  title?: Bi;
  audience: DocAudience[];
  /** Set for node-reference pages; the title comes from `getNodeLabelKey`. */
  node?: NodeType;
}

export interface DocSection {
  id: string;
  icon: string;
  title: Bi;
  items: DocArticleRef[];
}

export interface DocCategory {
  id: string;
  icon: string;
  title: Bi;
  blurb: Bi;
  audience: DocAudience[];
  /** Slug the card links to. */
  to: string;
}

/** Node-reference items, generated from the palette order (single source of truth). */
const NODE_ITEMS: DocArticleRef[] = [
  { slug: 'nodes/overview', title: { en: 'Overview', de: 'Überblick' }, audience: ['user', 'dev'] },
  ...NODE_DOC_ORDER.map((t): DocArticleRef => ({
    slug: `nodes/${t.toLowerCase()}`,
    node: t,
    audience: ['user', 'dev'],
  })),
];

export const DOCS_NAV: DocSection[] = [
  {
    id: 'getting-started', icon: 'rocket',
    title: { en: 'Getting Started', de: 'Erste Schritte' },
    items: [
      { slug: 'getting-started/welcome', title: { en: 'Welcome to Postwerk', de: 'Willkommen bei Postwerk' }, audience: ['user'] },
      { slug: 'getting-started/quickstart', title: { en: 'Quickstart', de: 'Schnellstart' }, audience: ['user'] },
      { slug: 'getting-started/connect-mailbox', title: { en: 'Connect a mailbox', de: 'Postfach verbinden' }, audience: ['user'] },
    ],
  },
  {
    id: 'core-concepts', icon: 'layers',
    title: { en: 'Core Concepts', de: 'Grundkonzepte' },
    items: [
      { slug: 'core-concepts/how-automations-work', title: { en: 'How automations work', de: 'Wie Automatisierungen funktionieren' }, audience: ['user', 'dev'] },
      { slug: 'core-concepts/branches', title: { en: 'Branches & handles', de: 'Zweige & Anschlüsse' }, audience: ['user', 'dev'] },
    ],
  },
  {
    id: 'nodes', icon: 'blocks',
    title: { en: 'Node Reference', de: 'Node-Referenz' },
    items: NODE_ITEMS,
  },
  {
    id: 'ai-assistant', icon: 'sparkle',
    title: { en: 'AI Assistant', de: 'KI-Assistent' },
    items: [
      { slug: 'ai-assistant/building-with-ai', title: { en: 'Building with the assistant', de: 'Mit dem Assistenten bauen' }, audience: ['user'] },
    ],
  },
  {
    id: 'testing', icon: 'beaker',
    title: { en: 'Testing & Simulation', de: 'Test & Simulation' },
    items: [
      { slug: 'testing/test-cases', title: { en: 'Test cases & assertions', de: 'Testfälle & Prüfungen' }, audience: ['user', 'dev'] },
    ],
  },
  {
    id: 'resources', icon: 'folder',
    title: { en: 'Resources', de: 'Ressourcen' },
    items: [
      { slug: 'resources/knowledge-base', title: { en: 'Knowledge bases', de: 'Wissensdatenbanken' }, audience: ['user'] },
    ],
  },
  {
    id: 'marketplace', icon: 'store',
    title: { en: 'Marketplace', de: 'Marktplatz' },
    items: [
      { slug: 'marketplace/publishing', title: { en: 'Publishing & installing', de: 'Veröffentlichen & Installieren' }, audience: ['user'] },
    ],
  },
  {
    id: 'teams', icon: 'users',
    title: { en: 'Teams', de: 'Teams' },
    items: [
      { slug: 'teams/roles-permissions', title: { en: 'Roles & permissions', de: 'Rollen & Berechtigungen' }, audience: ['admin'] },
    ],
  },
  {
    id: 'developer', icon: 'code',
    title: { en: 'Developer', de: 'Entwickler' },
    items: [
      { slug: 'developer/expressions', title: { en: 'Variables & expressions', de: 'Variablen & Ausdrücke' }, audience: ['dev', 'user'] },
      { slug: 'developer/api', title: { en: 'Triggering via webhook', de: 'Auslösen per Webhook' }, audience: ['dev'] },
    ],
  },
  {
    id: 'faq', icon: 'faq',
    title: { en: 'FAQ', de: 'FAQ' },
    items: [
      { slug: 'faq/general', title: { en: 'Frequently asked', de: 'Häufige Fragen' }, audience: ['user', 'admin', 'dev'] },
    ],
  },
];

export const DOC_AUDIENCES: { id: DocAudience; label: Bi }[] = [
  { id: 'user', label: { en: 'End user', de: 'Nutzer' } },
  { id: 'admin', label: { en: 'Admin', de: 'Admin' } },
  { id: 'dev', label: { en: 'Developer', de: 'Entwickler' } },
];

export const DOC_CATEGORIES: DocCategory[] = [
  { id: 'getting-started', icon: 'rocket', title: { en: 'Getting Started', de: 'Erste Schritte' }, blurb: { en: 'Connect a mailbox and ship your first automation in ten minutes.', de: 'Postfach verbinden und in zehn Minuten die erste Automatisierung live nehmen.' }, audience: ['user'], to: 'getting-started/welcome' },
  { id: 'core-concepts', icon: 'layers', title: { en: 'Core Concepts', de: 'Grundkonzepte' }, blurb: { en: 'How triggers, nodes, branches, and variables fit together.', de: 'Wie Trigger, Nodes, Zweige und Variablen zusammenspielen.' }, audience: ['user', 'dev'], to: 'core-concepts/how-automations-work' },
  { id: 'nodes', icon: 'blocks', title: { en: 'Node Reference', de: 'Node-Referenz' }, blurb: { en: 'Every block you can drop on the canvas, with fields and outputs.', de: 'Jeder Baustein für die Canvas, mit Feldern und Ausgängen.' }, audience: ['user', 'dev'], to: 'nodes/overview' },
  { id: 'ai-assistant', icon: 'sparkle', title: { en: 'AI Assistant', de: 'KI-Assistent' }, blurb: { en: 'Describe a workflow in plain language and let Postwerk build it.', de: 'Einen Ablauf in Worten beschreiben und Postwerk ihn bauen lassen.' }, audience: ['user'], to: 'ai-assistant/building-with-ai' },
  { id: 'testing', icon: 'beaker', title: { en: 'Testing & Simulation', de: 'Test & Simulation' }, blurb: { en: 'Pin test cases, replay real mail, and assert on node output.', de: 'Testfälle fixieren, echte Mails erneut abspielen und Node-Ausgaben prüfen.' }, audience: ['user', 'dev'], to: 'testing/test-cases' },
  { id: 'resources', icon: 'folder', title: { en: 'Resources', de: 'Ressourcen' }, blurb: { en: 'Categories, templates, parameter sets, and knowledge bases.', de: 'Kategorien, Vorlagen, Parametersets und Wissensdatenbanken.' }, audience: ['user'], to: 'resources/knowledge-base' },
  { id: 'marketplace', icon: 'store', title: { en: 'Marketplace', de: 'Marktplatz' }, blurb: { en: 'Install community automations or publish your own as a snapshot.', de: 'Community-Automatisierungen installieren oder eigene als Snapshot veröffentlichen.' }, audience: ['user'], to: 'marketplace/publishing' },
  { id: 'teams', icon: 'users', title: { en: 'Teams', de: 'Teams' }, blurb: { en: 'Members, permissions, plans, and AI quota for your organization.', de: 'Mitglieder, Berechtigungen, Tarife und KI-Kontingent Ihrer Organisation.' }, audience: ['admin'], to: 'teams/roles-permissions' },
  { id: 'developer', icon: 'code', title: { en: 'Developer', de: 'Entwickler' }, blurb: { en: 'The variable expression syntax and triggering runs via webhook.', de: 'Die Variablen-Ausdruckssyntax und das Auslösen per Webhook.' }, audience: ['dev'], to: 'developer/expressions' },
  { id: 'faq', icon: 'faq', title: { en: 'FAQ', de: 'FAQ' }, blurb: { en: 'Quick answers on quota, privacy, AI, and data handling.', de: 'Schnelle Antworten zu Kontingent, Datenschutz, KI und Datenverarbeitung.' }, audience: ['user', 'admin', 'dev'], to: 'faq/general' },
];

export const DOC_POPULAR: { to: string; label: Bi; cat: Bi }[] = [
  { to: 'getting-started/quickstart', label: { en: 'Build your first automation', de: 'Die erste Automatisierung bauen' }, cat: { en: 'Getting Started', de: 'Erste Schritte' } },
  { to: 'nodes/categorize', label: { en: 'Route mail with the Categorize node', de: 'Mail mit der Kategorisieren-Node leiten' }, cat: { en: 'Node Reference', de: 'Node-Referenz' } },
  { to: 'developer/expressions', label: { en: 'The {{variable}} expression syntax', de: 'Die {{variable}}-Ausdruckssyntax' }, cat: { en: 'Developer', de: 'Entwickler' } },
  { to: 'nodes/webhook', label: { en: 'Call an external API with Webhook', de: 'Eine externe API per Webhook aufrufen' }, cat: { en: 'Node Reference', de: 'Node-Referenz' } },
  { to: 'ai-assistant/building-with-ai', label: { en: 'Let the AI assistant build it', de: 'Den KI-Assistenten bauen lassen' }, cat: { en: 'AI Assistant', de: 'KI-Assistent' } },
  { to: 'teams/roles-permissions', label: { en: 'Invite teammates & set permissions', de: 'Teammitglieder einladen & berechtigen' }, cat: { en: 'Teams', de: 'Teams' } },
];

/** Flat ordered slug list (prose articles only) for prev/next navigation. */
export const DOC_ORDER: string[] = DOCS_NAV.flatMap(s => s.items.map(i => i.slug));
