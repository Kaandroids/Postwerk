# Häufig gestellte Fragen

_Schnelle Antworten auf die Dinge, die am häufigsten gefragt werden._

## Liest Postwerk meine gesamte E-Mail?

Nur E-Mails in den von Ihnen freigegebenen Ordnern, und nur, um Ihre Automatisierungen auszuführen. Sie steuern, welche Postfächer und Ordner verbunden sind.

## Was wird auf mein Kontingent angerechnet?

Das Kontingent ist **kostenbasiert und gilt ausschließlich für die KI-Nutzung**. Jeder Aufruf eines KI-Nodes (Categorize, Extract, Knowledge match) hat Kosten, die auf die monatliche Obergrenze Ihres Plans angerechnet werden. Nicht-KI-Verarbeitung — Filter, Labels, Antworten, Webhooks, Verzögerungen — wird nie darauf angerechnet. Die Plan-Grenzen sehen Sie in der Tabelle unten.

## Welche KI-Modelle werden verwendet?

Die KI-Nodes — Categorize, Extract und Knowledge match — werden von Googles **Gemini**-Modellen angetrieben. Es gibt keine nutzerseitige Modellauswahl und nichts zu konfigurieren; Postwerk übernimmt das für Sie.

## Welche Pläne gibt es?

| Plan | KI-Kontingent |
|------|---------------|
| STARTER | KI deaktiviert |
| PRO | Kostenobergrenze von 5 €/Monat für die KI-Nutzung |
| ENTERPRISE | Unbegrenzt |

Die Obergrenze bezieht sich auf die Kosten, nicht auf die reine Anzahl der Aufrufe — die Kosten jedes KI-Aufrufs werden erfasst und auf das Monatslimit angerechnet.

## Kann ich testen, ohne echte E-Mails zu versenden?

Ja. **Tests** laufen als Probelauf mit simulierten Aktionen — es wird nichts versendet und kein Webhook aufgerufen, es sei denn, Sie schalten einen Node ausdrücklich auf Live. **Simulationen** geben Live-Rückmeldung, wenn der Status einer Automatisierung TESTING ist, ebenfalls ohne Aktionen auszuführen. Siehe [Testfälle & Assertions](/docs/testing/test-cases).

## Wo werden meine Daten gespeichert?

Daten sind organisationsweit eingegrenzt und werden in der EU verarbeitet. Wissensdatenbanken überschreiten niemals Organisationsgrenzen.

## Wie wird der Zugriff gesteuert?

Alles gehört einer Organisation. Sie laden Mitglieder ein, erteilen Lese- und Sende-Berechtigungen pro Postfach, und die Rollen sind schlicht USER oder ADMIN. Siehe [Rollen & Berechtigungen](/docs/teams/roles-permissions).

## Kann ich Automatisierungen aus meinem eigenen Code aufrufen?

Ja. Setzen Sie einen Trigger-Node in den Webhook-Modus, um eine öffentliche eingehende URL bereitzustellen, an die Sie ein JSON-Payload POSTen können. Siehe [Automatisierungen programmatisch auslösen](/docs/developer/api).

## Wie erhalte ich Hilfe?

Nutzen Sie die In-App-Hilfe an jedem Node oder kontaktieren Sie den Support über den Assistenten. Admins erhalten auf kostenpflichtigen Plänen zudem priorisierten Support.
