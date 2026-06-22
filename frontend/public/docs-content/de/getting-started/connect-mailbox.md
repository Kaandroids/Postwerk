# Postfach verbinden

_Verbinden Sie die Postfächer, aus denen Postwerk liest und über die es versendet. Sie können mehrere verbinden und je Trigger gezielt ansprechen._

## Unterstützte Anbieter

| Anbieter | Auth | Hinweise |
|----------|------|----------|
| Gmail / Google Workspace | OAuth | Empfohlen; geringster Aufwand. |
| Microsoft 365 / Outlook | OAuth | Organisations-Zustimmung kann erforderlich sein. |
| Generisches IMAP/SMTP | App-Passwort | Host, Port und Zugangsdaten. |

## Konto hinzufügen

1. Gehen Sie zu **E-Mail-Konten → Konto hinzufügen**.
2. Wählen Sie Ihren Anbieter und schließen Sie den Zustimmungs-Vorgang ab.
3. Wählen Sie aus, welche Ordner Postwerk lesen darf.
4. Senden Sie eine Testnachricht, um die Verbindung zu bestätigen.

> [!WARNING]
> Antworten und Send-E-Mail-Aktionen gehen von der verbundenen Adresse aus. Konfigurieren Sie SPF/DKIM für Ihre Domain, damit sie nicht als Spam markiert werden.

> [!INFO]
> Postfach-Zugangsdaten werden verschlüsselt gespeichert und nie in API-Antworten zurückgegeben. Der Zugriff auf jedes Postfach wird pro Organisationsmitglied erteilt — siehe [Rollen & Berechtigungen](/docs/teams/roles-permissions).
