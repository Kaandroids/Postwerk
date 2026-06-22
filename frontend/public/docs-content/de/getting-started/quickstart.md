# Schnellstart: Ihre erste Automatisierung

_Der schnellste Weg zu einer Automatisierung in Postwerk: Beschreiben Sie sie einfach in Worten — der KI-Assistent baut alles für Sie. Lieber selbst bauen? Geht genauso._

## Der schnellste Weg: einfach den Assistenten fragen

Öffnen Sie den **KI-Assistenten** und beschreiben Sie die Aufgabe in eigenen Worten — zum Beispiel:

> „Kategorisiere eingehende Support-Mails, antworte Kund:innen mit einer Eingangsbestätigung und leite alles rund um Rechnungen an die Buchhaltung weiter.“

Mehr braucht es nicht. Der Assistent kann **alles** — von Anfang bis Ende:

- liest Ihre verbundenen Postfächer, Kategorien, Vorlagen und Wissensdatenbanken;
- legt Trigger-, Entscheidungs- und Aktions-Nodes an und verdrahtet die Zweige;
- prüft das Ergebnis nach denselben Regeln wie der Editor und behebt Probleme;
- erklärt, was gebaut wurde — Sie übernehmen, passen an oder verfeinern.

> [!TIP]
> Sie müssen die Canvas nie anfassen. Schreiben Sie einfach weiter — „und schick mir eine Slack-Nachricht, wenn es dringend ist“ — und der Assistent passt die Automatisierung für Sie an. Siehe [Mit dem KI-Assistenten bauen](/docs/ai-assistant/building-with-ai).

Wenn er fertig ist, ist die Automatisierung ein ganz normaler Graph: Öffnen Sie sie zum Feinschliff im Editor und schalten Sie sie ein.

## Oder von Hand bauen

Lieber volle Kontrolle? Bauen Sie denselben Ablauf in fünf Schritten auf der Canvas.

### 1 · Postfach verbinden

Öffnen Sie **E-Mail-Konten** und verbinden Sie das Postfach über IMAP/OAuth. Siehe [Postfach verbinden](/docs/getting-started/connect-mailbox).

### 2 · Trigger hinzufügen

Erstellen Sie eine neue Automatisierung, fügen Sie einen [Trigger](/docs/nodes/trigger) im E-Mail-Modus hinzu und wählen Sie Ihr Postfach. Jede eingehende Nachricht startet nun einen Lauf und stellt `{{email.subject}}`, `{{email.body}}` und mehr bereit.

### 3 · E-Mail kategorisieren

Fügen Sie einen [Categorize](/docs/nodes/categorize)-Node hinzu, setzen Sie die Quelle auf `{{email.body}}`, definieren Sie Kategorien — _Support_, _Vertrieb_, _Abrechnung_ — und einen Konfidenz-Schwellenwert von 70.

> [!INFO]
> Categorize bildet einen Ausgang pro Kategorie aus, plus einen `uncategorized`-Handle für Mail unter dem Schwellenwert. Die Wahl steht nachgelagert als `{{category.name}}` und `{{category.confidence}}` zur Verfügung.

### 4 · Automatisch antworten

Fügen Sie am _Support_-Zweig einen [Email action](/docs/nodes/email_action)-Node im Antwort-Modus hinzu — mit einem kurzen Vorlagentext, der `{{email.fromName}}` begrüßt.

### 5 · Vor dem Livegang testen

Öffnen Sie den Reiter **Tests**, fügen Sie eine Beispiel-E-Mail ein, führen Sie sie als Probelauf aus und prüfen Sie per Assertion, dass der _Support_-Zweig erreicht wird. Wenn alles grün ist, schalten Sie die Automatisierung ein. Siehe [Testfälle & Assertions](/docs/testing/test-cases).
