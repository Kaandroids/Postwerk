# Mit dem KI-Assistenten bauen

_Beschreiben Sie den gewünschten Workflow in normaler Sprache. Der Assistent stellt Rückfragen, legt die Nodes an, verdrahtet die Zweige und erklärt, was er gebaut hat._

## So funktioniert es

Öffnen Sie den Assistenten und beschreiben Sie die Aufgabe — „Rechnungen an die Buchhaltung weiterleiten und allen anderen antworten." Er liest Ihre verbundenen Postfächer, Kategorien und Wissensdatenbanken und schlägt dann eine vollständige Automatisierung vor, die Sie annehmen, bearbeiten oder verfeinern können.

Der Assistent arbeitet in Phasen: Zunächst **plant** er die Automatisierung gemeinsam mit Ihnen, und sobald Sie bestätigen, wechselt er ins **Bauen** — er fügt Nodes hinzu, verdrahtet Zweige und füllt die Konfiguration aus.

## Einen guten Prompt schreiben

- Benennen Sie den **Trigger**: welches Postfach oder welcher Zeitplan startet ihn.
- Beschreiben Sie die **Entscheidungen**: wie E-Mails aufgeteilt werden sollen.
- Nennen Sie die **Aktionen**: antworten, weiterleiten, beschriften, eine API aufrufen.
- Erwähnen Sie **Daten**, die Sie extrahiert oder abgeglichen benötigen.

> [!TIP]
> Der Assistent validiert jeden Entwurf gegen dieselben Regeln wie der Editor und behebt Probleme — Nodes ohne eingehende Kante, fehlende Kategorien — bevor er ihn an Sie übergibt.

## Nachdem er gebaut hat

Jede vom Assistenten gebaute Automatisierung ist ein ganz normaler Graph: Öffnen Sie sie im Editor, passen Sie jeden Node an und [testen](/docs/testing/test-cases) Sie sie, bevor Sie live gehen.
