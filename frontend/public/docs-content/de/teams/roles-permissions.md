# Rollen & Berechtigungen

_Postwerk ist mandantenfähig: Alles lebt innerhalb einer Organisation. Laden Sie Mitglieder ein, gewähren Sie Zugriff auf jedes Postfach und steuern Sie, wer die Organisation verwalten darf._

## Organisationen besitzen alles

Eine **Organisation** ist der Mandant. Automatisierungen, Kategorien, Parametersets, Vorlagen, Wissensdatenbanken und E-Mail-Konten gehören alle der Organisation — nicht einer einzelnen Person — sodass die Arbeit Mitgliederwechsel übersteht. Jede Abfrage ist auf die gerade aktive Organisation eingegrenzt.

## Mitgliederrollen

Jedes Mitglied hat eine Organisationsrolle, die festlegt, was es tun darf:

| Rolle | Darf |
|-------|------|
| Admin | Alles außer Abrechnung, Organisation löschen und Eigentümerwechsel — inklusive Mitglieder-, Plan- und Automatisierungsverwaltung. Impliziter Zugriff auf alle Postfächer. |
| Mitglied | Automatisierungen und Ressourcen bauen, testen und aktivieren, Freigaben entscheiden und aus dem Marktplatz installieren. Postfachzugriff nur über erteilte Berechtigungen. |
| Betrachter | Nur Lesezugriff: Automatisierungen, Läufe, Ressourcen und das Audit-Log ansehen — ohne Änderungen. |

> [!INFO]
> Jede Organisation hat genau einen **Inhaber** (Owner) — die Person, die sie erstellt hat — mit voller Kontrolle inklusive Abrechnung und Organisationslöschung. Die Inhaberschaft lässt sich übertragen, aber nicht wie eine normale Rolle zuweisen.

## Berechtigungen pro Postfach

Owner und Admins haben impliziten Zugriff auf alle Postfächer. Für **Mitglieder und Betrachter** wird der Zugriff auf jedes verbundene Postfach **pro Mitglied** als zwei unabhängige Berechtigungen erteilt:

- **Lesen** — die Automatisierungen des Mitglieds dürfen E-Mails aus diesem Postfach lesen.
- **Senden** — das Mitglied darf über dieses Postfach senden oder antworten.

Ein Mitglied sieht und nutzt nur die Postfächer, für die es freigeschaltet wurde. Die Sende-Berechtigung wird zudem zum Aktivierungszeitpunkt durchgesetzt, sodass eine Automatisierung nicht live gehen kann, die über ein Postfach sendet, von dem der Eigentümer nicht senden darf.

## Mitglieder einladen

1. Öffnen Sie **Teams → Mitglieder → Einladen**.
2. Geben Sie eine E-Mail-Adresse ein.
3. Die eingeladene Person tritt Ihrer Organisation beim Annehmen bei — pro Organisation ist kein separates Konto nötig.
4. Erteilen Sie die Lese-/Sende-Berechtigungen pro Postfach, die das Mitglied benötigt.

## Pläne & KI-Kontingent

Pläne legen eine monatliche **Kostenobergrenze für die KI-Nutzung** fest — wie das Kontingent funktioniert, sehen Sie in den [FAQ](/docs/faq/general). Nicht-KI-Verarbeitung wird nie darauf angerechnet.

> [!INFO]
> Da alles organisationsweit eingegrenzt ist, gehören Automatisierungen und Ressourcen der Organisation und nicht der Person, die sie erstellt hat — sie überstehen also Mitglieder, die hinzukommen oder gehen.
