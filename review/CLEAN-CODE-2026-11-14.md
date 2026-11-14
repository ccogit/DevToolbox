# Clean-Code-Audit und Refactoring – 14.11.2026

## Ziel und Geltungsbereich

Projektweite Betrachtung der Java-25-Anwendung mit UI, CLI, Kernlogik,
Job-Orchestrierung, Codeanalyse, Standards-/Mapping-Verarbeitung und Import.
Ausgangspunkt ist der Branch `refactor/swing-simplification-20261114`
(PR #3). Die Änderungen liegen auf dem separaten Branch
`refactor/clean-code-projectwide-20261114` und werden als gestapelter PR
gegen den ersten Branch eingereicht.

**Wichtig:** „Projektweit“ bedeutet hier eine Betrachtung aller Module und
gezielte Verbesserungen an mehreren fachlich wichtigen Grenzen. Nicht jede
bestehende Klasse wird in einem PR umgeschrieben; das würde die Gefahr
unbeabsichtigter Verhaltensänderungen erheblich erhöhen.

## Umgesetzte Änderungen

| Bereich | Vorher | Nachher | Qualitätseffekt |
| --- | --- | --- | --- |
| CLI | Argumente, JSON-Einlesen und Job-Ausführung in `App` | `CommandLine` kapselt Eingabeschichten | Single Responsibility, Testbarkeit |
| Core / Batch | Entitätswerte und Fortschrittsanzeige innerhalb derselben Schleife | Separater `entityValues`-Helfer; expliziter Laufindex | Lesbarkeit und Fehlerfall-Tests |
| Core / JSON | Unbekannte Literale/Escapes teils toleriert, fehlerhafte Unicode-Angaben uneinheitlich gemeldet | Striktere Validierung an der Parsergrenze | Verlässliche Fehlerdiagnose |
| Core / Konfiguration | `importInto` zerlegt flaches Exportformat selbst | `splitSections` und `ExportSections` | Entkopplung von Parsing und Dateischreiben |
| UI | `MainWindow` berechnet Dateisystem-/Projekt-Zusammenfassungen | `ProjectOverview` ohne Swing-Abhängigkeit | Single Responsibility, EDT-Schutz |
| Spezifikationen | Duplizierte Spaltensuche; Phase wird zweimal als Markdown-Tabelle eingelesen | Gemeinsame Spaltensuche und einmalige Interpretation der Phasentabellen | DRY, weniger Parsing |
| ZIP-Import | `plan` enthält Klassifikation, Plausibilitätsprüfung und Codeprüfung | Benannte Schritte für Status, Pfade, Attribute und Java-Quellen | Kurze Methoden, testbare Grenzen |
| Standards | `load` mischt Zeileniteration und Interpretation einer Artefaktzeile | `parseIndexRow` mit klarer Zuständigkeit | Lesbarkeit, geringer Verschachtelungsgrad |
| Entwicklungsprozess | Keine zentralen Clean-Code-Regeln | `CONTRIBUTING.md` und `.editorconfig` | Konsistente Weiterentwicklung |

## Qualitätsnachweise

- Neue JUnit-Tests für CLI-Eingabeschichten, Batch-Fehlerbehandlung,
  JSON-Validierung und Projektübersicht.
- Vorhandene Tests für Import, Standards, Projektkonfiguration und Spezifikationen
  bleiben für Regressionen erhalten.
- CI: `mvn -B -ntp verify` unter JDK 25 im Workflow `app-tests.yml`.
- Keine neuen produktiven Maven-Abhängigkeiten.

## Priorisierter Restbestand

Diese größeren Klassen haben eigenständige Verantwortlichkeiten, sollten aber
**nicht** ohne passende Charakterisierungstests in einem einzigen großen PR
zerlegt werden:

1. `ui/MainWindow.java`: verbleibende Navigation, Ausführung und
   Projektauswahl als unabhängige Controller/Views trennen.
2. `ui/ProjectEditor.java` und `ui/ParamForm.java`: Formularmodell,
   Persistierung und Dateiauswahl entkoppeln; GUI-Regressionstests ergänzen.
3. `tools/codecollector/CodeCollectorTool.java`: Scan-Orchestrierung,
   Budgetierung und Markdown-Ausgabe separat prüfen.
4. `tools/standards/StandardsTool.java`: Generierung, Index-Lesen und
   Editieren künftig in eigenständige Services gliedern.
5. `tools/speccheck/SpecChecker.java`: Prüfregeln D1–D7 in einzeln
   testbare Regelkomponenten teilen.
6. `core/MigrationProject.java` und `core/ProjectLayout.java`:
   Projekt-Persistierung, Einstellungen und Pfadableitung sauber kapseln.
7. `core/CodeSource.java`: Git-Prozessausführung, Repository-Cache und
   URL-Interpretation getrennt testen und extrahieren.

**Keine Framework-Migration:** Spring Boot würde der lokalen Swing-Anwendung
zusätzliche Schichten hinzufügen. Lombok ist bei den bereits vorhandenen Records
kein sinnvoller Ersatz für gutes Schnittstellendesign.

## Restrisiken

- Trotz automatisierter Tests sind UI-Interaktionen unter Windows sowie
  plattformspezifische Pfad-, Zugriffs- und Dateisystem-Sonderfälle manuell
  abzunehmen.
- Striktere JSON-Validierung weist zuvor stillschweigend tolerierte, aber
  formal ungültige Konfigurationswerte zurück. Das ist eine bewusste Änderung
  an der Eingabegrenze und wird über Tests dokumentiert.
