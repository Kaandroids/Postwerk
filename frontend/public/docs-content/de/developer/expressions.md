# Variablen & Ausdrücke

_Greifen Sie mit Ausdrücken in doppelten geschweiften Klammern auf jeden zuvor erzeugten Wert zu. Dieselbe Syntax gilt in jedem Feld, Header und Vorlagentext._

## Syntax

Setzen Sie einen Pfad in doppelte Klammern: `{{namespace.feld}}`. Namespaces werden pro Node vergeben — der E-Mail-Trigger besitzt `email.*`, jede Verarbeitungs-Node besitzt ihren eigenen Namespace (manche pro Node nummeriert, z. B. `extraction_0` oder `http_<id>`).

Die Variablen-Auswahl im Inspektor listet jeden für die gewählte Node verfügbaren Wert auf, sodass Sie einen Pfad selten von Hand tippen müssen.

## Der email-Namespace

Ein Trigger im **E-Mail**-Modus befüllt den `email.*`-Namespace für den gesamten Durchlauf:

| Ausdruck | Typ | Beschreibung |
|---|---|---|
| `{{email.from}}` | string | Absenderadresse |
| `{{email.fromName}}` | string | Anzeigename des Absenders |
| `{{email.to}}` | string | Empfängeradresse |
| `{{email.cc}}` | string | Cc-Empfänger |
| `{{email.subject}}` | string | Betreffzeile |
| `{{email.body}}` | string | Vollständiger Textkörper |
| `{{email.receivedAt}}` | datetime | Empfangszeit (ISO 8601) |
| `{{email.hasAttachments}}` | boolean | Ob die E-Mail Anhänge hat |
| `{{email.isReply}}` | boolean | Ob es eine Antwort auf einen bestehenden Verlauf ist |
| `{{email.folder}}` | string | Quellordner |

Ein Trigger im **Webhook**-Modus befüllt stattdessen `trigger.<feld>` aus der eingehenden JSON-Payload (über ein Parameterset abgebildet). Der Zeitplan-Modus befüllt keinen Namespace.

## Von Nodes erzeugte Variablen

Jede Node injiziert ihren eigenen Namespace, sobald sie läuft:

| Ausdruck | Von | Beschreibung |
|---|---|---|
| `{{category.name}}` | Kategorisieren | Name der gewählten Kategorie |
| `{{category.confidence}}` | Kategorisieren | Konfidenz, 0–1 |
| `{{extraction_0.<feld>}}` | Extrahieren | Ein im Parameterset definiertes Feld |
| `{{vectorsearch_0.confidence}}` | Wissens-Treffer | Richter-Konfidenz, 0–1 |
| `{{vectorsearch_0.match.<feld>}}` | Wissens-Treffer | Ein Feld des getroffenen Eintrags |
| `{{http_0.statusCode}}` | HTTP-Anfrage | HTTP-Statuscode (immer gesetzt, auch bei Fehlern) |
| `{{http_0.body}}` | HTTP-Anfrage | Roher Antwort-Body |
| `{{integration_0.<feld>}}` | Integrations-Aufruf | Ein Ausgabefeld der aufgerufenen Integration |
| `{{input.<feld>}}` | Input | Ein Eingabefeld (innerhalb einer Integration) |

> [!TIP]
> Der nummerierte Zusatz (`extraction_0`, `http_1`) kennzeichnet die Node-Instanz, sodass ein Ablauf mit zwei Extrahieren-Nodes `extraction_0.*` und `extraction_1.*` unabhängig bereitstellt.

## Konstanten

Wiederverwendbare Werte, die an der Automatisierung definiert sind, stehen als `{{const.NAME}}` zur Verfügung und können Secrets (API-Schlüssel, Tokens) enthalten, die nie in der Node-Konfiguration erscheinen.

## Ausdrücke in JSON verwenden

Ausdrücke werden überall eingesetzt — auch im Body einer Webhook-Anfrage. Setzen Sie String-Werte in Anführungszeichen; Zahlen bleiben ohne:

```json
{
  "name": "{{extraction_0.name}}",
  "email": "{{email.from}}",
  "amount": {{extraction_0.total}},
  "received": "{{email.receivedAt}}"
}
```

> [!DANGER]
> E-Mail-Inhalte sind durch Angreifer kontrollierbar. Wenn Sie `{{email.body}}` oder ein extrahiertes Feld an ein externes System übergeben, behandeln Sie es als nicht vertrauenswürdig und validieren Sie es auf der Empfängerseite.

Welche Variablen jede Node erzeugt, zeigt die [Node-Referenz](/docs/nodes/overview).
