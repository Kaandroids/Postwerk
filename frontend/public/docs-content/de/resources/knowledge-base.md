# Wissensdatenbanken

_Eine Wissensdatenbank ist eine organisationsweite Tabelle von Referenzeinträgen, die der Knowledge-match-Node durchsucht — bilden Sie Freitext auf eine feste Liste ab, etwa Kontonummern, SKUs oder Routing-Regeln._

## Aufbau

Eine Wissensdatenbank verwendet ein **Parameterset** als Schema, ihre Spalten sind also dieselben `{name, type, description}`-Felder wie anderswo. Jeder Eintrag ist eine ausgefüllte Zeile, gespeichert zusammen mit einem semantischen Embedding und einem Volltext-Suchtext.

## Feldrollen

| Rolle | Wirkung |
|-------|---------|
| Embed | Feld geht in den semantischen Vektor ein |
| Keyword | Feld geht in die Volltextsuche ein |
| Eindeutiger Schlüssel | Optionaler natürlicher Schlüssel für Upsert beim Re-Import |

## Einträge importieren

Fügen Sie Zeilen inline hinzu oder importieren Sie eine CSV. Mit gesetztem eindeutigem Schlüssel führt ein erneuter Import ein Upsert durch und embeddet nur geänderte Zeilen neu; ohne Schlüssel ersetzt er den gesamten Bestand. Das Embedding läuft asynchron außerhalb des Anfrage-Threads, damit große Importe reaktionsfähig bleiben.

## Aus einem Ablauf suchen

Richten Sie einen [Knowledge match](/docs/nodes/vector_search)-Node auf die Wissensdatenbank aus. Er holt die nächstgelegenen Einträge per hybrider Vektor- und Schlüsselwortsuche (zusammengeführt per Reciprocal Rank), ein Modell beurteilt den besten Treffer, und der Lauf wird nach Konfidenz weitergeleitet. Ein sicherer Treffer verlässt den Node über `success` und injiziert `{{vectorsearch_<id>.match.<field>}}`, `{{vectorsearch_<id>.confidence}}` und `{{vectorsearch_<id>.reason}}`; ein Ergebnis unter dem Schwellenwert oder ein Fehler verlässt ihn über `fail`.

> [!INFO]
> Wissensdatenbanken werden niemals zwischen Organisationen geteilt. Beim Veröffentlichen im Marketplace entscheiden Sie, ob die Einträge mit der Automatisierung mitreisen — siehe [Veröffentlichen & Installieren](/docs/marketplace/publishing).
