# Automatisierungen programmatisch auslösen

_Postwerk hat keine allgemeine REST-API und keine API-Schlüssel. Um einen Lauf aus Ihrem eigenen Code zu starten, setzen Sie einen Trigger-Node in den Webhook-Modus — er stellt eine öffentliche eingehende URL bereit, an die Sie POSTen können._

## So funktioniert es

Ein [Trigger](/docs/nodes/trigger)-Node im **Webhook**-Modus veröffentlicht eine öffentliche eingehende URL. Alles, was einen HTTP-POST absenden kann — ein Skript, ein anderer Dienst, ein No-Code-Tool — kann diese URL mit einem JSON-Payload ansprechen, um einen Lauf zu starten. Ein **Parameterset** bildet die Felder Ihres Payloads in den `trigger.*`-Namensraum ab, den der restliche Ablauf liest.

## 1 · Trigger in den Webhook-Modus schalten

Wählen Sie im Automatisierungs-Editor den Trigger-Node und wählen Sie **Webhook** als Modus. Postwerk erzeugt die eingehende URL und lässt Sie das Parameterset wählen, das das erwartete Payload beschreibt.

## 2 · Authentifizierung wählen

Die eingehende URL unterstützt drei Auth-Optionen:

| Auth | Wie Aufrufer sich ausweisen |
|------|-----------------------------|
| Keine | Jeder mit der URL kann POSTen (behandeln Sie die URL als Geheimnis) |
| API-Schlüssel-Header | Senden Sie ein gemeinsames Geheimnis in einem Request-Header |
| HMAC-Signatur | Signieren Sie den Request-Body; Postwerk prüft die Signatur |

## 3 · Ein Payload POSTen

```bash
curl -X POST "https://app.postwerk.com/api/v1/hooks/<endpoint-id>" \
  -H "Content-Type: application/json" \
  -d '{ "subject": "New order", "body": "Order #10024 for Jane Doe" }'
```

Das Parameterset bildet jedes JSON-Feld auf den `trigger.*`-Namensraum ab, sodass nachgelagerte Nodes `{{trigger.subject}}`, `{{trigger.body}}` und so weiter lesen können.

> [!TIP]
> Trigger-Daten im Webhook-Modus landen in `trigger.*` (nicht in `email.*`). Beim Testen wechselt das Test-Eingabeformular für Webhook-Trigger zu einem JSON-Payload-Editor — siehe [Testfälle & Assertions](/docs/testing/test-cases).

> [!DANGER]
> Das Payload ist angreiferkontrolliert. Wenn Sie `{{trigger.*}}`-Werte an ein nachgelagertes System übergeben, behandeln Sie sie als nicht vertrauenswürdig und validieren Sie auf der Empfängerseite. Bevorzugen Sie für sensible Fälle API-Schlüssel- oder HMAC-Auth gegenüber „Keine".
