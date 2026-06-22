import { Bi } from './landing.util';

/** A three-part highlighted AI message: [lead, bold keyword, tail]. */
export interface ChatSegment {
  de: [string, string, string];
  en: [string, string, string];
}

/** A pipeline node built by the AI chat demo. */
export interface ChatNode {
  t: Bi;
  d: Bi;
  icon: string;
}

/** The user prompt typed in at the start of the chat demo. */
export const LP2_CHAT_USER: Bi = {
  de: 'Wenn eine Shopify-Bestellung über 500 € reinkommt: Lege sie in Notion an und benachrichtige den Kanal #sales.',
  en: 'When a Shopify order over €500 comes in: add it to Notion and notify the #sales channel.',
};

/** The AI's first reply (announces the build). */
export const LP2_CHAT_AI1: ChatSegment = {
  de: ['Verstanden — ich baue eine Automation mit ', '4 Schritten', ': Trigger, Filter und zwei Aktionen. Einen Moment…'],
  en: ['Got it — building an automation with ', '4 steps', ': trigger, filter and two actions. One moment…'],
};

/** The AI's second reply (announces the successful test). */
export const LP2_CHAT_AI2: ChatSegment = {
  de: ['Fertig. Der ', 'Testlauf', ' mit Ihrer letzten Bestellung war erfolgreich — ich aktiviere die Automation.'],
  en: ['Done. The ', 'test run', ' with your latest order succeeded — activating the automation now.'],
};

/** The four pipeline nodes built sequentially in the chat demo. */
export const LP2_CHAT_NODES: ChatNode[] = [
  { t: { de: 'Trigger · Shopify', en: 'Trigger · Shopify' }, d: { de: 'Neue Bestellung', en: 'New order' }, icon: 'M13 2L3 14h9l-1 8 10-12h-9l1-8z' },
  { t: { de: 'Filter', en: 'Filter' }, d: { de: 'Betrag > 500 €', en: 'Amount > €500' }, icon: 'M22 3H2l8 9.46V19l4 2v-8.54L22 3z' },
  { t: { de: 'Aktion · Notion', en: 'Action · Notion' }, d: { de: 'Eintrag anlegen', en: 'Create entry' }, icon: 'M12 5v14M5 12h14' },
  { t: { de: 'Aktion · Slack', en: 'Action · Slack' }, d: { de: '#sales benachrichtigen', en: 'Notify #sales' }, icon: 'M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z' },
];
