# Testfälle & Assertions

_Heften Sie echte oder Beispiel-E-Mails als Testfälle an, spielen Sie sie als Probelauf durch den Graph und prüfen Sie per Assertion, was jeder Node erzeugt hat — damit eine Änderung niemals still die Weiterleitung kaputtmacht._

## Tests vs. Simulationen

Postwerk bietet zwei Wege, eine Automatisierung zu überprüfen:

- **Tests** — ein manueller **Probelauf** mit Assertions auf die Node-Ausgaben. Sie liefern die Eingabe und Ihre erwarteten Ergebnisse; es wird nie etwas versendet.
- **Simulationen** — **Live-Rückmeldung**, die sich nur füllt, wenn der Status einer Automatisierung auf TESTING gesetzt ist und echte E-Mails eintreffen. Sie beobachten, wie echte Nachrichten behandelt würden — ebenfalls ohne dass eine Aktion ausgeführt wird.

## Einen Testfall anlegen

Fügen Sie auf dem Reiter **Tests** einen Fall aus einer eingefügten E-Mail oder einer aus einem echten Lauf erfassten Nachricht hinzu. Jeder Fall speichert die Eingabe und Ihre erwarteten Ergebnisse. Das Eingabeformular berücksichtigt den Trigger-Modus — E-Mail-Trigger fragen E-Mail-Felder ab, Webhook-Trigger nehmen ein JSON-Payload, und Integrationen nehmen die Felder ihres INPUT-Parameterset.

## Assertions

Prüfen Sie die Ausgabe jedes Nodes — den genommenen Zweig, ein extrahiertes Feld, eine Kategorisierung, einen Webhook-Status:

| Feldprüfung | Beispiel |
|-------------|----------|
| Erreichter Zweig | Categorize → Support |
| Kategorie | `category.name` = Support |
| Extrahierter Wert | `extraction_0.order_no` = 10024 |
| Treffer-Feld | `vectorsearch_0.match.konto_nr` = 0490 |
| HTTP-Status | `http_0.statusCode` = 201 |

## Externe und KI-Nodes mocken

In einem Probelauf sind externe und KI-Nodes **mockbar**, damit Sie offline iterieren und sowohl den Erfolgs- als auch den Fehlerzweig durchspielen können:

- [Webhook](/docs/nodes/webhook) und [Integration call](/docs/nodes/integration_call) — geben Sie einen falschen Statuscode und Body vor (oder erzwingen Sie einen Fehler).
- [Categorize](/docs/nodes/categorize), [Extract](/docs/nodes/extract) und [Knowledge match](/docs/nodes/vector_search) — geben Sie das Ergebnis vor, das der Node zurückgeben soll.

Aktionen wie Antworten, Senden und Verschieben werden **simuliert** — nie tatsächlich ausgeführt. Jeder Node mit externem Aufruf hat zudem einen **Live-/Mock-Umschalter** pro Node: Lassen Sie ihn auf Mock, um Ihre falsche Antwort zu verwenden, oder schalten Sie ihn auf Live, um während des Probelaufs den echten Aufruf zu machen.

> [!WARNING]
> Der Live-Modus führt echte Seiteneffekte aus. Für ausgehende Webhooks ist das in Ordnung, aber ein Live-Send-Email- oder Email-action-Node versendet tatsächlich Mail an echte Empfänger. Diese bleiben standardmäßig auf Mock — schalten Sie sie nur bewusst frei.
