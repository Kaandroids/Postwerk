import { NodeType } from '../../models/automation.model';
import { NodeDoc } from './node-docs.model';

/**
 * Source-verified reference content for every automation node, shared by the
 * docs node pages (`/docs/nodes/<type>`) and the in-editor "?" node-info modal.
 *
 * Config fields, produced variables and handles are taken from the actual
 * executors/processors, `AutomationValidator` and `variable-graph.service.ts`
 * — NOT guessed. Color/icon/label are intentionally absent (derived from
 * `NODE_PALETTE`). AI nodes use Google Gemini; there is no user-facing model
 * picker.
 */
export const NODE_DOCS: Partial<Record<NodeType, NodeDoc>> = {
  TRIGGER: {
    type: 'TRIGGER',
    summary: {
      en: 'Starts an automation. A trigger listens for incoming email on one or more connected mailboxes, an inbound webhook, or a schedule, and emits each event into the flow.',
      de: 'Startet eine Automatisierung. Ein Trigger wartet auf eingehende E-Mails verbundener Postfächer, auf einen eingehenden Webhook oder auf einen Zeitplan und gibt jedes Ereignis in den Ablauf.',
    },
    modes: [
      {
        name: { en: 'Email', de: 'E-Mail' },
        desc: {
          en: 'Fires on each incoming message in the selected mailboxes, and splits first-time mail from replies.',
          de: 'Löst bei jeder eingehenden Nachricht in den gewählten Postfächern aus und trennt Erstnachrichten von Antworten.',
        },
        produces: [
          { v: 'email.from', d: { en: 'Sender address.', de: 'Absenderadresse.' } },
          { v: 'email.fromName', d: { en: 'Sender display name.', de: 'Anzeigename des Absenders.' } },
          { v: 'email.subject', d: { en: 'Subject line.', de: 'Betreffzeile.' } },
          { v: 'email.body', d: { en: 'Full text body.', de: 'Vollständiger Textkörper.' } },
          { v: 'email.receivedAt', d: { en: 'Receipt time (ISO 8601). Also: to, cc, isRead, folder, isReply, hasAttachments.', de: 'Empfangszeit (ISO 8601). Außerdem: to, cc, isRead, folder, isReply, hasAttachments.' } },
        ],
        handlesOut: [
          { name: 'new-email', htype: 'email', desc: { en: 'A first-time message (not a reply).', de: 'Eine erstmalige Nachricht (keine Antwort).' } },
          { name: 'reply', htype: 'email', desc: { en: 'A reply to an existing thread.', de: 'Eine Antwort auf einen bestehenden Verlauf.' } },
        ],
      },
      {
        name: { en: 'Webhook', de: 'Webhook' },
        desc: {
          en: 'Exposes a public inbound URL; an external POST starts a run. The endpoint is secured by None / API key / HMAC.',
          de: 'Stellt eine öffentliche Inbound-URL bereit; ein externer POST startet einen Durchlauf. Der Endpunkt wird per Keine / API-Key / HMAC abgesichert.',
        },
        produces: [
          { v: 'trigger.<field>', d: { en: 'Each field of the inbound JSON payload, mapped via the parameter set.', de: 'Jedes Feld der eingehenden JSON-Payload, über das Parameterset abgebildet.' } },
        ],
        handlesOut: [
          { name: 'output', htype: 'any', desc: { en: 'Continues into the flow.', de: 'Läuft in den Ablauf weiter.' } },
        ],
      },
      {
        name: { en: 'Schedule', de: 'Zeitplan' },
        desc: {
          en: 'Runs on a recurring interval or cron (every few minutes, hourly, daily, weekly, monthly) — useful to drive a Send email or Webhook node on a timer.',
          de: 'Läuft in einem wiederkehrenden Intervall oder per Cron (alle paar Minuten, stündlich, täglich, wöchentlich, monatlich) — nützlich, um eine Send-E-Mail- oder Webhook-Node zeitgesteuert auszulösen.',
        },
        produces: [],
        handlesOut: [
          { name: 'output', htype: 'any', desc: { en: 'Continues into the flow (no variables injected).', de: 'Läuft in den Ablauf weiter (keine Variablen injiziert).' } },
        ],
      },
    ],
    fields: [
      { name: 'triggerMode', type: 'enum', desc: { en: 'Email · Webhook · Schedule — how runs are started.', de: 'E-Mail · Webhook · Zeitplan — wie Durchläufe gestartet werden.' } },
      { name: 'accountIds', type: 'account[]', desc: { en: 'Which connected mailboxes feed this trigger (Email & Webhook modes).', de: 'Welche verbundenen Postfächer diesen Trigger speisen (E-Mail- & Webhook-Modus).' } },
      { name: 'parameterSetId', type: 'Parameterset', desc: { en: 'Webhook mode: maps the inbound JSON payload onto the trigger.* fields.', de: 'Webhook-Modus: bildet die eingehende JSON-Payload auf die trigger.*-Felder ab.' } },
      { name: 'webhook auth', type: 'None · API key · HMAC', desc: { en: 'Secures the public inbound URL (stored on the endpoint, not in node config).', de: 'Sichert die öffentliche Inbound-URL (am Endpunkt gespeichert, nicht in der Node-Konfiguration).' } },
      { name: 'schedule', type: 'preset · interval · cron', desc: { en: 'Schedule mode: how often to fire — every 5/15/30 min, hourly, daily 09:00, weekly, monthly, or a raw cron.', de: 'Zeitplan-Modus: wie oft ausgelöst wird — alle 5/15/30 Min., stündlich, täglich 09:00, wöchentlich, monatlich oder roher Cron.' } },
    ],
    produces: [],
    handlesIn: [],
    handlesOut: [],
    example: {
      title: { en: 'Receive support mail', de: 'Support-Mails empfangen' },
      body: { en: 'Set Mode to Email and tick the support@ mailbox. Every inbound message now opens a run with the full email.* namespace available downstream.', de: 'Modus auf E-Mail setzen und das support@-Postfach anhaken. Jede eingehende Nachricht startet nun einen Durchlauf mit dem vollständigen email.*-Namespace.' },
    },
    notes: [
      { en: 'An automation must contain exactly one trigger (validator code MISSING_TRIGGER).', de: 'Eine Automatisierung muss genau einen Trigger enthalten (Validator-Code MISSING_TRIGGER).' },
    ],
  },

  FILTER: {
    type: 'FILTER',
    summary: {
      en: 'Lets a run continue only when its conditions match. Build named checks of condition groups (DNF: OR between groups, AND within a group). The first matching check fires its branch; everything else takes the fallback.',
      de: 'Lässt einen Durchlauf nur fortfahren, wenn die Bedingungen zutreffen. Erstellen Sie benannte Prüfungen aus Bedingungsgruppen (DNF: ODER zwischen Gruppen, UND innerhalb einer Gruppe). Die erste passende Prüfung löst ihren Zweig aus; alles andere geht über den Fallback.',
    },
    fields: [
      { name: 'checks', type: 'check[]', desc: { en: 'Named checks; each routes to its own output handle.', de: 'Benannte Prüfungen; jede führt zu einem eigenen Ausgang.' } },
      { name: 'groups / conditions', type: 'field · operator · value', desc: { en: 'DNF rows — OR between groups, AND within a group.', de: 'DNF-Zeilen — ODER zwischen Gruppen, UND innerhalb einer Gruppe.' } },
      { name: 'operator', type: 'enum', desc: { en: 'equals, contains, starts/ends with, is true/false, greater/less than … (text compares are case-insensitive; >/< are numeric).', de: 'gleich, enthält, beginnt/endet mit, ist wahr/falsch, größer/kleiner als … (Textvergleiche ignorieren Groß-/Kleinschreibung; >/< numerisch).' } },
      { name: 'field / value', type: 'expression', desc: { en: 'Any {{variable}} on the left; a literal or expression to compare against.', de: 'Links eine beliebige {{Variable}}; rechts ein Literal oder Ausdruck zum Vergleich.' } },
    ],
    produces: [],
    handlesIn: [{ name: 'in', htype: 'any', desc: { en: 'Upstream value.', de: 'Wert von oberhalb.' } }],
    handlesOut: [
      { name: 'check_<i>', htype: 'any', desc: { en: 'One handle per check, labeled with the check name; fires on first match.', de: 'Ein Ausgang pro Prüfung, mit dem Prüfnamen beschriftet; löst beim ersten Treffer aus.' } },
      { name: 'fallback', htype: 'any', desc: { en: 'No check matched.', de: 'Keine Prüfung hat zugetroffen.' } },
    ],
    example: {
      title: { en: 'Only invoices', de: 'Nur Rechnungen' },
      body: { en: 'Check "Invoices": {{email.subject}} contains "invoice" OR {{email.hasAttachments}} is true. Matching mail flows on; the rest stops here.', de: 'Prüfung „Rechnungen“: {{email.subject}} enthält „Rechnung“ ODER {{email.hasAttachments}} ist wahr. Passende Mails fließen weiter; der Rest endet hier.' },
    },
  },

  CATEGORIZE: {
    type: 'CATEGORIZE',
    summary: {
      en: 'AI routing. Reads a source value and assigns one of your categories with a confidence score, then routes the run down the matched category\'s branch. Mail that does not clear the threshold takes the uncategorized handle.',
      de: 'KI-Routing. Liest einen Quellwert, weist eine Ihrer Kategorien mit Konfidenzwert zu und leitet den Durchlauf über den Zweig der getroffenen Kategorie. Mail unter dem Schwellenwert geht über den Ausgang „nicht kategorisiert“.',
    },
    fields: [
      { name: 'categoryIds', type: 'category[]', desc: { en: 'The set of categories the model may choose from.', de: 'Die Menge der Kategorien, aus denen das Modell wählen darf.' } },
      { name: 'threshold', type: '0–100 (default 70)', desc: { en: 'Minimum confidence required to accept a match.', de: 'Mindestkonfidenz, um einen Treffer zu akzeptieren.' } },
      { name: 'sourceVariables', type: 'string[]', desc: { en: 'Which variables to classify (defaults to {{email.body}}).', de: 'Welche Variablen klassifiziert werden (Standard {{email.body}}).' } },
    ],
    produces: [
      { v: 'category.name', d: { en: 'Chosen category name.', de: 'Name der gewählten Kategorie.' } },
      { v: 'category.id', d: { en: 'Chosen category id.', de: 'ID der gewählten Kategorie.' } },
      { v: 'category.color', d: { en: 'Category color.', de: 'Farbe der Kategorie.' } },
      { v: 'category.confidence', d: { en: 'Model confidence, 0–1.', de: 'Modellkonfidenz, 0–1.' } },
    ],
    handlesIn: [{ name: 'in', htype: 'email', desc: { en: 'Message to classify.', de: 'Zu klassifizierende Nachricht.' } }],
    handlesOut: [
      { name: 'category_<i>', htype: 'cat', desc: { en: 'One handle per configured category — fires on the accepted match.', de: 'Ein Ausgang pro konfigurierter Kategorie — löst beim akzeptierten Treffer aus.' } },
      { name: 'uncategorized', htype: 'cat', desc: { en: 'Below threshold, "Other", or an error.', de: 'Unter Schwellenwert, „Sonstiges“ oder ein Fehler.' } },
    ],
    example: {
      title: { en: 'Triage by intent', de: 'Nach Anliegen sortieren' },
      body: { en: 'Categories Sales, Support, Billing; threshold 70. A refund request lands on the Billing branch with a short reason.', de: 'Kategorien Vertrieb, Support, Abrechnung; Schwellenwert 70. Eine Rückerstattungsanfrage landet im Abrechnungs-Zweig mit kurzer Begründung.' },
    },
    notes: [
      { en: 'Powered by Google Gemini. An implicit "Other" fallback is always offered to the model.', de: 'Angetrieben von Google Gemini. Ein impliziter „Sonstiges“-Fallback wird dem Modell stets angeboten.' },
      { en: 'Mockable in tests (supply a handle + category + confidence). Validator: CATEGORIZE_NO_CATEGORIES if empty.', de: 'In Tests mockbar (Ausgang + Kategorie + Konfidenz angeben). Validator: CATEGORIZE_NO_CATEGORIES, wenn leer.' },
    ],
  },

  EXTRACT: {
    type: 'EXTRACT',
    summary: {
      en: 'AI data extraction. For each extraction entry, loads a parameter set as the schema and asks Gemini to pull those fields out of free-text email, so downstream nodes can use real values like an order number or a phone number.',
      de: 'KI-Datenextraktion. Lädt pro Extraktions-Eintrag ein Parameterset als Schema und lässt Gemini diese Felder aus dem Freitext der E-Mail ziehen, damit nachgelagerte Nodes echte Werte wie Bestellnummer oder Telefonnummer nutzen können.',
    },
    fields: [
      { name: 'extractions', type: '{ parameterSetId, label }[]', desc: { en: 'Each entry extracts the fields of one parameter set.', de: 'Jeder Eintrag extrahiert die Felder eines Parametersets.' } },
      { name: 'sourceVariables', type: 'string[]', desc: { en: 'Text to read from (defaults to {{email.body}}).', de: 'Quelltext (Standard {{email.body}}).' } },
    ],
    produces: [
      { v: 'extraction_<i>.<field>', d: { en: 'One variable per schema field, per extraction entry.', de: 'Eine Variable pro Schemafeld, pro Extraktions-Eintrag.' } },
    ],
    handlesIn: [{ name: 'in', htype: 'email', desc: { en: 'Message to read.', de: 'Zu lesende Nachricht.' } }],
    handlesOut: [
      { name: 'extraction_<i>', htype: 'param', desc: { en: 'One handle per extraction entry — only successful entries activate (no fail handle).', de: 'Ein Ausgang pro Extraktions-Eintrag — nur erfolgreiche Einträge aktivieren (kein Fehler-Ausgang).' } },
    ],
    example: {
      title: { en: 'Read an order email', de: 'Eine Bestell-Mail auslesen' },
      body: { en: 'Use a "Customer" parameter set with name, order_no, phone. A reply node can then address {{extraction_0.name}} and quote {{extraction_0.order_no}}.', de: 'Ein „Kunde“-Parameterset mit name, order_no, phone verwenden. Eine Antwort-Node kann dann {{extraction_0.name}} ansprechen und {{extraction_0.order_no}} nennen.' },
    },
    notes: [
      { en: 'Powered by Google Gemini. Mockable in tests. Validator: EXTRACT_NO_PARAMSET if no parameter set is selected.', de: 'Angetrieben von Google Gemini. In Tests mockbar. Validator: EXTRACT_NO_PARAMSET, wenn kein Parameterset gewählt ist.' },
    ],
  },

  LABEL: {
    type: 'LABEL',
    summary: {
      en: 'Assigns one of your categories to the email as a persistent label, so it shows up filed in the inbox and in reporting. A pass-through action — the run continues straight on.',
      de: 'Weist der E-Mail eine Ihrer Kategorien als dauerhaftes Label zu, sodass sie abgelegt im Postfach und im Reporting erscheint. Eine Durchlauf-Aktion — der Ablauf läuft direkt weiter.',
    },
    fields: [{ name: 'categoryId', type: 'category', desc: { en: 'The label to apply.', de: 'Das zuzuweisende Label.' } }],
    produces: [],
    handlesIn: [{ name: 'in', htype: 'any', desc: { en: 'Upstream value.', de: 'Wert von oberhalb.' } }],
    handlesOut: [{ name: 'output', htype: 'any', desc: { en: 'Continues after labeling (labeled with the category name).', de: 'Läuft nach dem Labeln weiter (mit dem Kategorienamen beschriftet).' } }],
    example: {
      title: { en: 'File as Receipts', de: 'Als Belege ablegen' },
      body: { en: 'Place after a Categorize or Filter branch to tag matched mail "Receipts".', de: 'Hinter einen Kategorisierungs- oder Filter-Zweig setzen, um passende Mails mit „Belege“ zu kennzeichnen.' },
    },
    notes: [{ en: 'Validator: LABEL_NO_CATEGORY if no category is chosen. Simulated (not persisted) in dry-run.', de: 'Validator: LABEL_NO_CATEGORY, wenn keine Kategorie gewählt ist. Im Probelauf simuliert (nicht gespeichert).' }],
  },

  REMOVE_LABEL: {
    type: 'REMOVE_LABEL',
    summary: {
      en: 'Removes a previously assigned category from the email — useful for re-routing flows that re-evaluate mail, or for cleaning up a provisional label once a later node decides differently.',
      de: 'Entfernt eine zuvor zugewiesene Kategorie von der E-Mail — nützlich für Abläufe, die Mail neu bewerten, oder zum Aufräumen eines vorläufigen Labels, wenn eine spätere Node anders entscheidet.',
    },
    fields: [{ name: 'categoryId', type: 'category', desc: { en: 'The label to remove.', de: 'Das zu entfernende Label.' } }],
    produces: [],
    handlesIn: [{ name: 'in', htype: 'any', desc: { en: 'Upstream value.', de: 'Wert von oberhalb.' } }],
    handlesOut: [{ name: 'output', htype: 'any', desc: { en: 'Continues after removal.', de: 'Läuft nach dem Entfernen weiter.' } }],
    example: {
      title: { en: 'Clear the triage tag', de: 'Triage-Label entfernen' },
      body: { en: 'After a thread is marked resolved, strip the "Needs review" label so it leaves the queue.', de: 'Nachdem ein Verlauf als erledigt markiert wurde, das Label „Zu prüfen“ entfernen, damit er die Warteschlange verlässt.' },
    },
    notes: [{ en: 'Shares the LABEL_NO_CATEGORY validator code. Simulated in dry-run.', de: 'Teilt den Validator-Code LABEL_NO_CATEGORY. Im Probelauf simuliert.' }],
  },

  EMAIL_ACTION: {
    type: 'EMAIL_ACTION',
    summary: {
      en: 'Acts on the triggering email itself — reply, forward, or move to a folder. Reply and forward compose from a template or a manual body plus variables; move files the message. The most common action node.',
      de: 'Wirkt auf die auslösende E-Mail selbst — antworten, weiterleiten oder in einen Ordner verschieben. Antwort und Weiterleitung werden aus einer Vorlage oder manuellem Text plus Variablen erstellt; Verschieben legt die Nachricht ab. Die häufigste Aktions-Node.',
    },
    modes: [
      {
        name: { en: 'Reply', de: 'Antworten' },
        desc: {
          en: 'Replies to the triggering message, composed from a template or a manual subject + body with variables.',
          de: 'Antwortet auf die auslösende Nachricht, erstellt aus einer Vorlage oder manuellem Betreff + Text mit Variablen.',
        },
      },
      {
        name: { en: 'Forward', de: 'Weiterleiten' },
        desc: {
          en: 'Forwards the message to a recipient (toAddress) using template or manual content.',
          de: 'Leitet die Nachricht an einen Empfänger (toAddress) mit Vorlagen- oder manuellem Inhalt weiter.',
        },
      },
      {
        name: { en: 'Move to folder', de: 'In Ordner verschieben' },
        desc: {
          en: 'Files the message into a folder; the special __TRASH__ target moves it to the trash.',
          de: 'Legt die Nachricht in einen Ordner ab; das Spezialziel __TRASH__ verschiebt sie in den Papierkorb.',
        },
      },
    ],
    fields: [
      { name: 'actionMode', type: 'enum', desc: { en: 'Reply · Forward · Move folder.', de: 'Antworten · Weiterleiten · Ordner verschieben.' } },
      { name: 'contentSource', type: 'Vorlage | Manuell', desc: { en: 'Reply/Forward: use a template or a manual subject + body.', de: 'Antwort/Weiterleitung: Vorlage oder manueller Betreff + Text.' } },
      { name: 'templateId', type: 'template', desc: { en: 'The reply/forward template (when using a Vorlage).', de: 'Die Antwort-/Weiterleitungsvorlage (bei „Vorlage“).' } },
      { name: 'toAddress', type: 'expression', desc: { en: 'Forward: recipient(s), e.g. {{email.from}}.', de: 'Weiterleitung: Empfänger, z. B. {{email.from}}.' } },
      { name: 'folder', type: 'string', desc: { en: 'Move: destination folder (the special value __TRASH__ moves to trash).', de: 'Verschieben: Zielordner (der Spezialwert __TRASH__ verschiebt in den Papierkorb).' } },
    ],
    produces: [],
    handlesIn: [{ name: 'in', htype: 'email', desc: { en: 'Message to act on.', de: 'Zu bearbeitende Nachricht.' } }],
    handlesOut: [{ name: 'output', htype: 'done', desc: { en: 'Done — a single output handle (no separate success/fail).', de: 'Fertig — ein einzelner Ausgang (kein getrenntes Erfolg/Fehler).' } }],
    example: {
      title: { en: 'Auto-acknowledge', de: 'Automatische Eingangsbestätigung' },
      body: { en: 'Reply mode, body "Hello {{email.fromName}}, thanks — ticket {{extraction_0.order_no}} is open." The sender gets an instant, personalized confirmation.', de: 'Antwort-Modus, Text „Hallo {{email.fromName}}, danke — Ticket {{extraction_0.order_no}} ist offen.“ Der Absender erhält sofort eine personalisierte Bestätigung.' },
    },
    notes: [{ en: 'Validator: EMAILACTION_NO_TARGET (Forward without recipient / Move without folder / Reply without template or body). Simulated in dry-run — never really sent or moved.', de: 'Validator: EMAILACTION_NO_TARGET (Weiterleitung ohne Empfänger / Verschieben ohne Ordner / Antwort ohne Vorlage oder Text). Im Probelauf simuliert — nie wirklich gesendet oder verschoben.' }],
  },

  WEBHOOK: {
    type: 'WEBHOOK',
    summary: {
      en: 'Calls an external HTTP API. Configure the method, URL, headers, auth and a body built from variables; Postwerk performs the request, can retry on failure, and exposes the response to downstream nodes.',
      de: 'Ruft eine externe HTTP-API auf. Konfigurieren Sie Methode, URL, Header, Auth und einen aus Variablen gebauten Body; Postwerk führt die Anfrage aus, kann bei Fehlern wiederholen und stellt die Antwort nachgelagerten Nodes bereit.',
    },
    fields: [
      { name: 'url / method', type: 'expression · GET…DELETE', desc: { en: 'Endpoint (may contain {{variables}}) and HTTP method.', de: 'Endpunkt (kann {{Variablen}} enthalten) und HTTP-Methode.' } },
      { name: 'headers', type: 'kv[]', desc: { en: 'Header name/value pairs; values can reference secrets.', de: 'Header-Name/Wert-Paare; Werte können Secrets referenzieren.' } },
      { name: 'authType', type: 'None · Bearer · Basic · API key', desc: { en: 'Inline token/credentials or a stored secret (takes precedence).', de: 'Inline-Token/Zugangsdaten oder ein gespeichertes Secret (hat Vorrang).' } },
      { name: 'body', type: 'template', desc: { en: 'Request payload with variables (POST/PUT/PATCH).', de: 'Anfrage-Payload mit Variablen (POST/PUT/PATCH).' } },
      { name: 'timeout / retry', type: '1–60s · 0–3', desc: { en: 'Timeout, retry count, delay and which conditions to retry on (5xx/429/network).', de: 'Timeout, Wiederholungen, Verzögerung und Bedingungen (5xx/429/Netzwerk).' } },
      { name: 'responseSchemas', type: '{ name, condition, Parameterset }[]', desc: { en: 'Output branches: each entry routes a status pattern (e.g. "2xx") to its resp_<i> handle; the pinned "unmatched" branch catches the rest. An optional parameter-set parses that branch\'s response fields into variables.', de: 'Ausgangs-Zweige: jeder Eintrag leitet ein Status-Muster (z. B. „2xx“) zu seinem resp_<i>-Handle; der feste Zweig „unmatched“ fängt den Rest. Ein optionales Parameterset liest die Antwortfelder dieses Zweigs in Variablen aus.' } },
    ],
    produces: [
      { v: 'http_<id>.statusCode', d: { en: 'HTTP status code of the response (always set, even on errors).', de: 'HTTP-Statuscode der Antwort (immer gesetzt, auch bei Fehlern).' } },
      { v: 'http_<id>.body', d: { en: 'Raw response body.', de: 'Roher Antwort-Body.' } },
      { v: 'http_<id>.<field>', d: { en: 'One variable per parameter-set field of the matched branch.', de: 'Eine Variable pro Parameterset-Feld des passenden Zweigs.' } },
    ],
    handlesIn: [{ name: 'in', htype: 'any', desc: { en: 'Upstream value.', de: 'Wert von oberhalb.' } }],
    handlesOut: [
      { name: 'resp_<i>', htype: 'json', desc: { en: 'One handle per response branch; the response routes to the most specific matching status.', de: 'Ein Handle pro Antwort-Zweig; die Antwort wird zum spezifischsten passenden Status geleitet.' } },
      { name: 'unmatched', htype: 'json', desc: { en: 'Catch-all for any status no branch matched (4xx/5xx, network error, exhausted retries). Always present.', de: 'Auffangzweig für jeden Status, der zu keinem Zweig passt (4xx/5xx, Netzwerkfehler, Wiederholungen erschöpft). Immer vorhanden.' } },
    ],
    example: {
      title: { en: 'Create a CRM contact', de: 'CRM-Kontakt anlegen' },
      body: { en: 'POST a JSON body. On a 2xx the run takes the matching response branch (resp_0) and {{http_0.statusCode}} is 201; a later node can read {{http_0.body}}.', de: 'Einen JSON-Body per POST senden. Bei 2xx nimmt der Durchlauf den passenden Antwort-Zweig (resp_0) und {{http_0.statusCode}} ist 201; eine spätere Node kann {{http_0.body}} lesen.' },
      code: { lang: 'json', body: '{\n  "name": "{{extraction_0.name}}",\n  "email": "{{email.from}}",\n  "source": "inbound-email"\n}' },
    },
    notes: [
      { en: 'SSRF-validated, rate-limited (60/min per user), 32 KB response cap.', de: 'SSRF-geprüft, ratenbegrenzt (60/Min. pro Nutzer), Antwortlimit 32 KB.' },
      { en: 'In a plain dry-run the call is skipped (no real request, no webhook_* variables). Mockable, and a per-node live switch can force the real call. Validator: WEBHOOK_NO_URL.', de: 'In einem reinen Probelauf wird der Aufruf übersprungen (keine echte Anfrage, keine webhook_*-Variablen). Mockbar, und ein Live-Schalter pro Node erzwingt den echten Aufruf. Validator: WEBHOOK_NO_URL.' },
    ],
  },

  SEND_EMAIL: {
    type: 'SEND_EMAIL',
    summary: {
      en: 'Sends a brand-new message to any recipient, independent of the triggering email — notifications, internal alerts, or hand-offs. Works in webhook and schedule flows too, since it needs no trigger email.',
      de: 'Sendet eine völlig neue Nachricht an einen beliebigen Empfänger, unabhängig von der auslösenden E-Mail — Benachrichtigungen, interne Hinweise oder Übergaben. Funktioniert auch in Webhook- und Zeitplan-Abläufen, da keine Trigger-Mail nötig ist.',
    },
    fields: [
      { name: 'senderAccountId', type: 'account', desc: { en: 'Which mailbox sends (falls back to the trigger account).', de: 'Welches Postfach sendet (fällt auf das Trigger-Konto zurück).' } },
      { name: 'to / cc / bcc', type: 'expression', desc: { en: 'Recipients (variables allowed).', de: 'Empfänger (Variablen erlaubt).' } },
      { name: 'contentSource', type: 'Vorlage | Manuell', desc: { en: 'A template or a manual subject + body.', de: 'Eine Vorlage oder manueller Betreff + Text.' } },
    ],
    produces: [],
    handlesIn: [{ name: 'in', htype: 'any', desc: { en: 'Upstream value.', de: 'Wert von oberhalb.' } }],
    handlesOut: [{ name: 'output', htype: 'done', desc: { en: 'Done — a single output handle.', de: 'Fertig — ein einzelner Ausgang.' } }],
    example: {
      title: { en: 'Notify the team', de: 'Das Team benachrichtigen' },
      body: { en: 'To ops@acme.com, subject "New lead: {{extraction_0.name}}". Fires whenever the Categorize node routes to Sales.', de: 'An ops@acme.com, Betreff „Neuer Lead: {{extraction_0.name}}“. Löst aus, sobald die Kategorisierungs-Node auf Vertrieb leitet.' },
    },
    notes: [{ en: 'Validator: SEND_NO_RECIPIENT if "to" is empty. Simulated in dry-run (resolves recipients/subject/body without sending).', de: 'Validator: SEND_NO_RECIPIENT, wenn „to“ leer ist. Im Probelauf simuliert (löst Empfänger/Betreff/Text auf, ohne zu senden).' }],
  },

  DELAY: {
    type: 'DELAY',
    summary: {
      en: 'Pauses a run for a set duration before continuing — for follow-up sequences, rate-limiting downstream calls, or waiting on an external system to settle.',
      de: 'Pausiert einen Durchlauf für eine festgelegte Dauer, bevor es weitergeht — für Follow-up-Sequenzen, das Drosseln nachgelagerter Aufrufe oder das Warten auf ein externes System.',
    },
    fields: [{ name: 'delayMinutes', type: 'number (default 30, min 1)', desc: { en: 'How long to wait before the next node runs.', de: 'Wie lange gewartet wird, bevor die nächste Node läuft.' } }],
    produces: [],
    handlesIn: [{ name: 'in', htype: 'any', desc: { en: 'Upstream value.', de: 'Wert von oberhalb.' } }],
    handlesOut: [{ name: 'output', htype: 'any', desc: { en: 'Continues after the wait.', de: 'Läuft nach dem Warten weiter.' } }],
    example: {
      title: { en: 'Follow up in a day', de: 'Nach einem Tag nachfassen' },
      body: { en: 'Set 1440 minutes between an initial auto-reply and a "still need help?" Send-email node.', de: '1440 Minuten zwischen einer ersten Auto-Antwort und einer „Noch Hilfe nötig?“-Send-E-Mail-Node setzen.' },
    },
    notes: [{ en: 'Live: queues a delayed run and halts. Dry-run: simulates and does NOT halt, so downstream is still traced.', de: 'Live: stellt einen verzögerten Durchlauf in die Warteschlange und hält an. Probelauf: simuliert und hält NICHT an, sodass nachgelagerte Nodes weiter verfolgt werden.' }],
  },

  INTEGRATION_CALL: {
    type: 'INTEGRATION_CALL',
    summary: {
      en: 'Invokes a reusable Integration (a trigger-less automation) as a sub-flow, passing per-call inputs and exposing its return value to downstream nodes.',
      de: 'Ruft eine wiederverwendbare Integration (eine triggerlose Automatisierung) als Unter-Ablauf auf, übergibt aufrufspezifische Eingaben und stellt deren Rückgabe nachgelagerten Nodes bereit.',
    },
    fields: [
      { name: 'integrationId', type: 'integration', desc: { en: 'The integration to call (selecting it snapshots its input/output fields and settings).', de: 'Die aufzurufende Integration (bei Auswahl werden ihre Ein-/Ausgabefelder und Einstellungen übernommen).' } },
      { name: 'inputMappings', type: 'field → expression', desc: { en: 'Maps caller variables into the integration\'s input.* fields.', de: 'Bildet Aufrufer-Variablen auf die input.*-Felder der Integration ab.' } },
      { name: 'instanceSettings', type: 'key → value', desc: { en: 'Per-call values for the integration\'s internal constants.', de: 'Aufrufspezifische Werte für die internen Konstanten der Integration.' } },
    ],
    produces: [
      { v: 'integration_<id>.<outputField>', d: { en: 'One variable per output field the integration returns.', de: 'Eine Variable pro Ausgabefeld, das die Integration zurückgibt.' } },
    ],
    handlesIn: [{ name: 'in', htype: 'any', desc: { en: 'Upstream value.', de: 'Wert von oberhalb.' } }],
    handlesOut: [
      { name: 'done', htype: 'done', desc: { en: 'The integration returned successfully.', de: 'Die Integration wurde erfolgreich zurückgegeben.' } },
      { name: 'failure', htype: 'done', desc: { en: 'Missing, not an integration, or an exception.', de: 'Fehlt, ist keine Integration oder eine Ausnahme.' } },
    ],
    example: {
      title: { en: 'Reuse a lookup', de: 'Eine Nachschlage-Logik wiederverwenden' },
      body: { en: 'Call a "lookup customer" integration with {{email.from}} and read {{integration_0.tier}} downstream.', de: 'Eine Integration „Kunde nachschlagen“ mit {{email.from}} aufrufen und {{integration_0.tier}} nachgelagert lesen.' },
    },
    notes: [
      { en: 'Maximum nesting depth 5. Mockable in tests. Validator: INTEGRATION_NO_REF if no integration is selected.', de: 'Maximale Verschachtelungstiefe 5. In Tests mockbar. Validator: INTEGRATION_NO_REF, wenn keine Integration gewählt ist.' },
      { en: 'An automation that contains an Integration call cannot be published to the marketplace yet.', de: 'Eine Automatisierung mit einem Integrations-Aufruf kann noch nicht im Marktplatz veröffentlicht werden.' },
    ],
  },

  INPUT: {
    type: 'INPUT',
    summary: {
      en: 'The single mandatory entry point of an Integration. The caller pre-seeds input.*, so this node is a pass-through that declares the integration\'s input shape.',
      de: 'Der einzige verpflichtende Einstiegspunkt einer Integration. Der Aufrufer befüllt input.* vorab, daher ist diese Node ein Durchlauf, der die Eingabeform der Integration deklariert.',
    },
    fields: [{ name: 'parameterSetId', type: 'Parameterset', desc: { en: 'Declares the input fields (input.<field>).', de: 'Deklariert die Eingabefelder (input.<field>).' } }],
    produces: [{ v: 'input.<field>', d: { en: 'One variable per parameter, seeded by the caller.', de: 'Eine Variable pro Parameter, vom Aufrufer befüllt.' } }],
    handlesIn: [],
    handlesOut: [{ name: 'output', htype: 'param', desc: { en: 'Into the integration flow (start node, no input).', de: 'In den Integrations-Ablauf (Startknoten, kein Eingang).' } }],
    example: {
      title: { en: 'Define an integration\'s inputs', de: 'Die Eingaben einer Integration definieren' },
      body: { en: 'Point it at a "Lookup request" parameter set with one field, email; callers then map a value into {{input.email}}.', de: 'Auf ein Parameterset „Nachschlage-Anfrage“ mit einem Feld email verweisen; Aufrufer bilden dann einen Wert auf {{input.email}} ab.' },
    },
    notes: [{ en: 'Only for Integration-kind automations; exactly one is required. Validator: INTEGRATION_NO_REF if no parameter set.', de: 'Nur für Automatisierungen vom Typ Integration; genau eine ist erforderlich. Validator: INTEGRATION_NO_REF, wenn kein Parameterset.' }],
  },

  OUTPUT: {
    type: 'OUTPUT',
    summary: {
      en: 'The optional return point of an Integration. Resolves each return field from its mapping, writes the result back to the caller, and halts the sub-flow.',
      de: 'Der optionale Rückgabepunkt einer Integration. Löst jedes Rückgabefeld aus seiner Zuordnung auf, schreibt das Ergebnis an den Aufrufer zurück und beendet den Unter-Ablauf.',
    },
    fields: [
      { name: 'parameterSetId', type: 'Parameterset', desc: { en: 'Declares the return shape.', de: 'Deklariert die Rückgabeform.' } },
      { name: 'outputMappings', type: 'field → expression', desc: { en: 'Each return field\'s source, e.g. {{input.x}} or {{extraction_1.y}}.', de: 'Die Quelle jedes Rückgabefelds, z. B. {{input.x}} oder {{extraction_1.y}}.' } },
    ],
    produces: [],
    handlesIn: [{ name: 'result', htype: 'param', desc: { en: 'The value to return (input only, no output).', de: 'Der zurückzugebende Wert (nur Eingang, kein Ausgang).' } }],
    handlesOut: [],
    example: {
      title: { en: 'Return the looked-up fields', de: 'Die nachgeschlagenen Felder zurückgeben' },
      body: { en: 'Map tier ← {{vectorsearch_0.match.tier}}; the caller reads it as {{integration_<callNode>.tier}}.', de: 'tier ← {{vectorsearch_0.match.tier}} zuordnen; der Aufrufer liest es als {{integration_<callNode>.tier}}.' },
    },
    notes: [{ en: 'Optional — zero or one per integration. Validator: INTEGRATION_NO_REF if no parameter set.', de: 'Optional — null oder eine pro Integration. Validator: INTEGRATION_NO_REF, wenn kein Parameterset.' }],
  },

  VECTOR_SEARCH: {
    type: 'VECTOR_SEARCH',
    summary: {
      en: 'Matches an incoming value against an organization knowledge base. Postwerk retrieves the closest entries with hybrid vector + full-text search, a Gemini judge picks the best one, and the run routes by confidence — ideal for mapping free text onto a fixed list like account codes.',
      de: 'Gleicht einen eingehenden Wert mit einer Organisations-Wissensdatenbank ab. Postwerk holt die nächstgelegenen Einträge per hybrider Vektor- + Volltextsuche, ein Gemini-Richter wählt den besten, und der Durchlauf leitet nach Konfidenz — ideal, um Freitext auf eine feste Liste wie Kontonummern abzubilden.',
    },
    fields: [
      { name: 'knowledgeBaseId', type: 'knowledge base', desc: { en: 'The org knowledge base to search (selecting it snapshots its match fields).', de: 'Die zu durchsuchende Org-Wissensdatenbank (bei Auswahl werden ihre Trefferfelder übernommen).' } },
      { name: 'queryVariable', type: 'expression', desc: { en: 'The value to match, e.g. {{extraction_1.position}}.', de: 'Der abzugleichende Wert, z. B. {{extraction_1.position}}.' } },
      { name: 'topK', type: '1–10 (default 5)', desc: { en: 'Candidates retrieved before the judge picks.', de: 'Vor der Richter-Auswahl geholte Kandidaten.' } },
      { name: 'confidenceThreshold', type: '0–100 (default 90)', desc: { en: 'Minimum judge confidence to accept a match.', de: 'Mindestkonfidenz des Richters, um einen Treffer zu akzeptieren.' } },
    ],
    produces: [
      { v: 'vectorsearch_<id>.confidence', d: { en: 'Judge confidence for the match, 0–1.', de: 'Richter-Konfidenz für den Treffer, 0–1.' } },
      { v: 'vectorsearch_<id>.reason', d: { en: 'Why this entry was chosen.', de: 'Warum dieser Eintrag gewählt wurde.' } },
      { v: 'vectorsearch_<id>.match.<field>', d: { en: 'Any field of the matched entry.', de: 'Jedes Feld des getroffenen Eintrags.' } },
    ],
    handlesIn: [{ name: 'in', htype: 'param', desc: { en: 'Value to match.', de: 'Abzugleichender Wert.' } }],
    handlesOut: [
      { name: 'success', htype: 'json', desc: { en: 'Matched at or above the threshold.', de: 'Treffer am oder über dem Schwellenwert.' } },
      { name: 'fail', htype: 'json', desc: { en: 'No match, below threshold, or an error.', de: 'Kein Treffer, unter Schwellenwert oder ein Fehler.' } },
    ],
    example: {
      title: { en: 'Map item → account', de: 'Artikel → Konto abbilden' },
      body: { en: 'Search the SKR 03 knowledge base with {{extraction_0.item}}. "Logitech Keyboard" matches account 0490; use {{vectorsearch_0.match.konto_nr}} downstream.', de: 'Die SKR-03-Wissensdatenbank mit {{extraction_0.item}} durchsuchen. „Logitech Tastatur“ trifft Konto 0490; {{vectorsearch_0.match.konto_nr}} nachgelagert verwenden.' },
    },
    notes: [{ en: 'Gemini judge. Mockable in tests. Validators: VECTOR_SEARCH_NO_KB, VECTOR_SEARCH_NO_QUERY.', de: 'Gemini-Richter. In Tests mockbar. Validatoren: VECTOR_SEARCH_NO_KB, VECTOR_SEARCH_NO_QUERY.' }],
  },
};

/** Node types documented above, in a sensible reading order for the overview grid. */
export const NODE_DOC_ORDER: NodeType[] = [
  'TRIGGER', 'FILTER', 'CATEGORIZE', 'EXTRACT', 'VECTOR_SEARCH',
  'LABEL', 'REMOVE_LABEL', 'EMAIL_ACTION', 'SEND_EMAIL', 'WEBHOOK',
  'DELAY', 'INTEGRATION_CALL', 'INPUT', 'OUTPUT',
];
