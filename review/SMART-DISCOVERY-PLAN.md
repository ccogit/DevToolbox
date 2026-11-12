# Smart Discovery – Erweiterungsplan (12.11.2026)

Basis: `review/devtoolbox-2026-11-11`, Branch: `feature/devtoolbox-smart-discovery-2026-11-12`.
Ziele: robuste Klassenerkennung, korrekte Migration inklusive Bestand/Schwebe, weniger Eingaben, nachvollziehbare Vorschläge; keine Änderungen auf `main`.

| Nr. | Ansatz | Nutzerwert | Verifikation |
|---|---|---|---|
| 01 | Zentraler strukturierter Java-Katalog statt mehrerer widersprüchlicher Regex-Heuristiken | Klassen, Imports, Packages und Typabhängigkeiten reproduzierbar erfassen | Java-Parser-/Inventartests |
| 02 | Explizite Varianten Bestands-/Schwebe-Namen und Alt-/Neu-Muster erkennen | Kein Verlust von spezialisierten Readern, Prozessoren und Schreibern | Varianten-fixtures |
| 03 | Entitäten aus Datenmodellen **und** wiederkehrenden Logikmustern ableiten | Entitäten ohne klassische TO-/PO-Namen auffinden | Nur-Logik-Projekttest |
| 04 | Vorlagen aus *allen* passenden Rollen einer Entität aufnehmen statt Zufallstreffer | Vollständige Abbildung der Read/Process/Write-Artefakte | Mehrmodul-/Mehrrollen-fixtures |
| 05 | Entitätsunabhängige Klassen und Interfaces über Abhängigkeitsgraph finden | Gemeinsame Basisdaten auch ohne Entitätsnamen mitnehmen | Dependency-fixtures |
| 06 | Gleiche Klassennamen in mehreren Packages/Modulen sicher unterscheiden | Keine falsche Basisvorlage | FQN-/Mehrdeutigkeits-Test |
| 07 | Erkennung mit Beleg und Priorität offenlegen, inkl. Warnung bei Unklarheit | Vertrauen und gezielte Korrekturmöglichkeit | Report-/Snapshot-Test |
| 08 | Trockener Standards-Plan vor dem Schreiben, atomar vorbereiten, Backups eindeutig | Keine überraschenden oder halbfertigen Standards | Staging-/Rollback-Test |
| 09 | Standard-Vorschlag mit einem Klick statt mehrstufiger Auswahldialoge | Weniger Eingaben | GUI-Szenarien |
| 10 | Projektprüfung als neuer Job mit automatisch ermittelten Fehlstellen | Früher erkennen, ob Quellen, Zielmodell, Mapping und Standards einsatzfähig sind | Job-/Diagnosetests |
| 11 | Relevante Entitäten nach Quellenevidenz priorisieren und zweifelhafte DDL-Namen filtern | Bessere Standardwerte im Projekt-Editor | Suggestions-Test |
| 12 | Bedienoberfläche und Berichte: übersichtliche Hinweise, passende Abmessungen, konsistente Begriffe | Professionelle, verständliche Oberfläche | JDK21/25-CI und UI-Aufnahmen |

Arbeitsprinzip: Jede Änderung bekommt passende Regressionstests. Bereits existierende Standards werden nicht automatisch überschrieben; der Benutzer bestätigt die Übernahme. Erkennungsregeln sind deterministisch und melden unentscheidbare Fälle statt eine falsche Klasse zu wählen.
