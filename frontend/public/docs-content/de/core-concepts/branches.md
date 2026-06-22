# Zweige & Handles

_Entscheidungs-Nodes teilen einen Lauf in Zweige auf. Wenn Sie wissen, welcher Handle wann auslöst, bleiben Ihre Abläufe vorhersehbar._

Jeder Node hat seinen eigenen Satz an Ausgangs-**Handles**. Manche haben einen einzigen Ausgang; andere teilen den Lauf nach Ergebnis oder nach Kategorie auf. Verbinden Sie jeden Handle mit dem Pfad, den dieser Fall nehmen soll.

## Handles je Node

| Node | Ausgangs-Handles |
|---|---|
| [Trigger](/docs/nodes/trigger) (E-Mail-Modus) | `new-email`, `reply` |
| [Trigger](/docs/nodes/trigger) (Webhook / Zeitplan) | `output` |
| [Filter](/docs/nodes/filter) | je Prüfung ein `check_<i>` (mit dem Prüfnamen beschriftet) + `fallback` |
| [Categorize](/docs/nodes/categorize) | ein Handle pro Kategorie + `uncategorized` |
| [Extract](/docs/nodes/extract) | je Extraktionseintrag ein `extraction_<i>` (kein Fehler-Handle) |
| [Knowledge match](/docs/nodes/vector_search) | `success`, `fail` |
| [Webhook](/docs/nodes/webhook) | `success`, `failure` |
| [Integration call](/docs/nodes/integration_call) | `done`, `failure` |
| [Email action](/docs/nodes/email_action), [Send email](/docs/nodes/send_email), [Add label](/docs/nodes/label), [Remove label](/docs/nodes/remove_label), [Delay](/docs/nodes/delay) | ein einzelner `output`-Handle |

## Filter-Prüfungen und Fallback

Ein [Filter](/docs/nodes/filter)-Node definiert eine oder mehrere benannte Prüfungen. Jede Prüfung erhält ihren eigenen `check_<i>`-Handle, beschriftet mit dem Prüfnamen, sodass Sie jede Bedingung anders weiterleiten können. E-Mails, die keiner Prüfung entsprechen, verlassen den Node über `fallback`.

## Kategorie-Handles

Der [Categorize](/docs/nodes/categorize)-Node bildet einen Ausgang pro Kategorie aus. Verbinden Sie jeden mit dem Pfad, den diese Art von E-Mail nehmen soll. E-Mails, die den Konfidenz-Schwellenwert nicht erreichen, verlassen den Node über `uncategorized`.

## Success vs. fail

Bei [Knowledge match](/docs/nodes/vector_search), [Webhook](/docs/nodes/webhook) und [Integration call](/docs/nodes/integration_call) bedeutet ein Handle „hat funktioniert" und der andere „hat nicht funktioniert" — wobei **fail nicht dasselbe ist wie ein Fehler**. Ein zwar sauberes, aber negatives Ergebnis (ein Nicht-Treffer, eine Nicht-2xx-Antwort) und ein harter Fehler verlassen den Node beide über den fail-seitigen Handle.

> [!WARNING]
> Bei Knowledge match werden sowohl ein Nicht-Treffer als auch ein harter Fehler über `fail` geleitet — die Lauf-Trace hält den Status aber unterschiedlich fest (`NOT_MATCHED` vs. `ERROR`). Prüfen Sie die Trace bei der Fehlersuche, um zu sehen, welcher Fall tatsächlich eingetreten ist.

## Lose Handles

Ein Handle, an dem nichts angeschlossen ist, beendet diesen Zweig einfach — das ist für Ergebnisse, die Sie nicht interessieren, völlig in Ordnung. Nicht in Ordnung ist ein Node ohne eingehende Kante: Der Validator markiert solche **losen, erforderlichen Eingänge** und blockiert Aktivierung und Veröffentlichung, bis Sie sie verbunden haben.
