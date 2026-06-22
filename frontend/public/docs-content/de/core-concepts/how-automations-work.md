# Wie Automatisierungen funktionieren

_Jede Automatisierung ist ein gerichteter Graph. Sobald Trigger, Nodes, Handles und Variablen sitzen, haben Sie fast alles beisammen, um sicher zu bauen._

## Der Lauf

Jede Automatisierung hat genau einen **Trigger**. Wenn er auslöst, erzeugt er einen **Lauf** — einen einzelnen Durchgang durch den Graph. Der Lauf trägt einen gemeinsamen **Kontext** mit sich: die eingehende E-Mail sowie jede unterwegs erzeugte Variable. Vom Trigger aus durchläuft der Lauf Node für Node entlang der von Ihnen verbundenen Handles, bis er das Ende jedes betretenen Zweigs erreicht.

## Nodes

Ein Node erledigt genau eine Aufgabe — filtern, klassifizieren, extrahieren, beschriften, senden, einen Webhook aufrufen, warten. Jeder Node liest aus dem gemeinsamen Lauf-Kontext, was er braucht, und kann neue Variablen zurückschreiben, sodass ein späterer Node auf der Arbeit eines früheren aufbauen kann. Den vollständigen Satz finden Sie in der [Node-Referenz](/docs/nodes/overview).

## Handles & Zweige

Nodes werden über typisierte **Handles** verbunden. Ein einfacher Aktions-Node hat einen einzigen Ausgang, während Entscheidungs-Nodes mehrere ausbilden — [Categorize](/docs/nodes/categorize) bildet einen Handle pro Kategorie plus einen `uncategorized`-Handle aus, und [Filter](/docs/nodes/filter) einen Handle pro Prüfung plus einen `fallback`. Mehr dazu unter [Zweige & Handles](/docs/core-concepts/branches).

### Port-Typen

Handles sind nach dem Datentyp eingefärbt, der durch sie fließt — so ist eine falsche Verbindung direkt auf der Leinwand sichtbar.

| Typ | Trägt |
|---|---|
| email | Einen vollständigen Nachrichtenkontext |
| param | Extrahierte Felder |
| cat | Ein Kategorisierungs-Ergebnis |
| json | Eine Webhook-/Wissensdatenbank-Antwort |
| any | Untypisierter Durchlauf |

## Variablen

Alles, was ein Node erzeugt, wird zu einer `{{variable}}`, die Sie nachgelagert referenzieren können. Jeder Node besitzt einen Namensraum — der Trigger im E-Mail-Modus besitzt `email.*`, [Categorize](/docs/nodes/categorize) besitzt `category.*`, und jede Extract-, HTTP-Request- oder Integration-Instanz besitzt einen Namensraum, der nach ihrer Node-ID benannt ist (`extraction_0`, `http_0`, …).

| Variable | Von | Beschreibung |
|---|---|---|
| `{{email.from}}`, `{{email.subject}}`, `{{email.body}}`, … | [Trigger](/docs/nodes/trigger) (E-Mail) | Die eingehende Nachricht |
| `{{trigger.<field>}}` | [Trigger](/docs/nodes/trigger) (Webhook) | Felder der eingehenden Payload |
| `{{category.name}}`, `{{category.confidence}}` | [Categorize](/docs/nodes/categorize) | Gewählte Kategorie |
| `{{extraction_0.<field>}}` | [Extract](/docs/nodes/extract) | Extrahierte Felder |
| `{{vectorsearch_0.confidence}}`, `{{vectorsearch_0.match.<field>}}` | [Knowledge match](/docs/nodes/vector_search) | Gefundener Eintrag |
| `{{http_0.statusCode}}`, `{{http_0.body}}` | [HTTP Request](/docs/nodes/webhook) | HTTP-Antwort (Status immer gesetzt) |
| `{{integration_0.<field>}}` | [Integration call](/docs/nodes/integration_call) | Rückgabe der Integration |

Die vollständige Syntax — Standardwerte, Bedingungen und Formatierung — finden Sie unter [Variablen & Ausdrücke](/docs/developer/expressions).

> [!INFO]
> Der Editor speichert fortlaufend. Das Ein- und Ausschalten einer Automatisierung ist der einzige Umschalter zwischen Entwurf und Live-Betrieb.
