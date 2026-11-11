# Aufgabe: Review des Projekts „DevToolbox“

## 1. Kontext

Die **DevToolbox** ist eine Java-25-Swing-Anwendung (FlatLaf, Maven). Sie erzeugt Aufgaben und Skills für MS Copilot und unterstützt damit eine Versicherungs-Batch-Migration (Spring Batch, DB2 for z/OS):

- **Spezifikation** einer fachlichen Entität aus einem Referenzprojekt (Job `phase-spec`)
- **Implementierung** nach Spezifikation und Standards (Job `implementation`)
- **Mapping-Dokumentation** aus dem implementierten Code (Job `mapping-doc`)

Copilot nimmt **höchstens 3 Dateien pro Prompt** an. Jeder Job liefert deshalb genau drei Dateien zum Hochladen (Skill, Aufgabe, Inhalt).

Grundlage ist der aktuelle Stand von `main`. Lies zuerst `README.md`, `pom.xml` und die Paketstruktur unter `src/main/java/de/devtoolbox/`:

| Paket | Inhalt |
|---|---|
| `core` | Job, Config, MigrationProject, ProjectLayout, EntitySuggestions, … |
| `jobs` | PhaseSpecJob, ImplementationJob, MappingDocJob |
| `tools` | codecollector (Scanner, DdlReader, TargetModel, …), mappingdoc, standards, importer |
| `ui` | MainWindow, JobView, ParamForm, ProjectEditor, StandardsDialog, Console, Ui, UiTheme |

Die Texte für Copilot liegen unter `src/main/resources/skills/` und `src/main/resources/aufgaben/`. Testdaten liegen in `testprojekt/`, `testprojekt-ziel/` und `migrationsprojekte/`.

**Bauen und testen:** JDK 25, `mvn clean test` und `mvn package`. Die Jar **nicht ohne Argumente starten**, sonst öffnet sich die Oberfläche und blockiert. Für Läufe ohne Oberfläche gibt es die Kommandozeile, Beispiele in `README.md` unter „Bauen und starten“, etwa `java -jar target/devtoolbox.jar phase-spec project=testziel "terms=Risiko"`.

## 2. Regeln für die Prüfung

- **Nur lesen und analysieren.** Ändere keinen Code, committe nichts, pushe nichts. Temporäre Dateien und Messläufe nur außerhalb des Repos ablegen.
- **Jede Aussage mit Beleg:** Datei und Zeile (`pfad/Datei.java:123`), bei Laufzeitaussagen mit Messwert und Messweg.
- **Nur echte Befunde.** Keine Geschmacksfragen ohne konkreten Nachteil, keine Wiederholungen. Unsichere Befunde ausdrücklich als „Vermutung“ kennzeichnen.
- **Gewichte nach Schwere** (Abschnitt 5), nicht nach Anzahl.

## 3. Prüfbereiche und Kriterien

### P1 – Fachliche Korrektheit der Jobs

| Kriterium | Prüffrage |
|---|---|
| Ergebnis-Vertrag | Liefert jeder Job genau die in `JobInfo`/README beschriebenen Dateien? Sind es höchstens 3 Upload-Dateien? Ist der Prompt im Log korrekt? |
| Eingaben | Werden Pflichtfelder geprüft, mit verständlicher Fehlermeldung? Was passiert bei leeren, falschen oder nicht existierenden Pfaden? |
| Ableitungen | Ist `ProjectLayout` korrekt: `C:\firma-develop`, Sparten-Projekt, Migrationsmodul, `*-designer-services`, Zielmodell, Mapping-Vorlagen, Jira-Präfix HAFT/SACH? Was passiert, wenn mehrere Kandidaten passen? |
| Parser | `DdlReader` (CREATE TABLE/VIEW, COMMENT/LABEL, mehrzeilige Kommentare), `TargetModel` (JSON mit `entityTypeName`), `MappingTemplate` (Confluence-Tabelle mit umbrochenen Zeilen und Makros) – Grenzfälle und robustes Verhalten bei fremden Formaten |
| Mapping-Doku | Werden Spezifikation (`{jiraissues:…}`) und Status (FERTIG) korrekt und nur für die richtige Entität eingetragen? Bleiben JSON und Tabelle sonst unverändert? |
| Standards | Erkennung „Vorlage je Projekt vorhanden/fehlt“, Ersetzen der Platzhalter, Laden und Schreiben von `STANDARDS.md` ohne Datenverlust |

### P2 – Copilot-Artefakte (Skills und Aufgaben)

- Sind Skill und Aufgabe **widerspruchsfrei** zueinander und zum Code?
- Ist der Text **eindeutig**? Gibt es Platzhalter `{{…}}`, die nicht ersetzt werden?
- Ist das **Ausgabeformat** klar: Dateinamen, ZIP-Struktur, OPL, keine Redundanz?
- Passt die **Größe** zum Copilot-Limit? Schätze die Tokens der erzeugten Dateien mit den Testdaten.

### P3 – Robustheit und Fehlerbehandlung

- Exceptions, die verschluckt werden oder ohne Meldung enden. Fehlerpfade, auf die nur ein Stacktrace folgt.
- Dateisystem: Kodierung (UTF-8/ISO-8859-1), CRLF/LF, lange Windows-Pfade, gesperrte Dateien, fehlende Rechte.
- **Datensicherheit:** Schreibt die Anwendung irgendwo **in die Migrationsprojekte selbst**? Sie darf nur in den DevToolbox-Ordner (`migrationsprojekte/…`) und in `Downloads\devtoolbox` schreiben. Löschen und Überschreiben nur nach Rückfrage, mit Sicherung wo zugesagt.
- Nebenläufigkeit: Jobs laufen auf einem virtuellen Thread. Werden UI-Updates nur auf dem EDT ausgeführt? Gibt es Race Conditions beim Projekt- oder Jobwechsel während eines Laufs?

### P4 – Performance

Miss, und schätze nicht nur. Gib Messweg, Datenmenge und Ergebnis an.

| Kriterium | Vorgehen | Zielwert (Orientierung) |
|---|---|---|
| Laufzeit der Jobs | Testprojekt über die CLI; zusätzlich ein **synthetisches großes Projekt** (z. B. 5.000 Java-Dateien, 200 MB, große DDL mit 500 Tabellen bzw. Views) | Testprojekt < 2 s, großes Projekt < 30 s |
| Dateisuche | `ProjectScanner`/`SourceTree`: Werden Ordner wie `target`, `.git` und `node_modules` übersprungen? Wird jede Datei mehrfach gelesen? | jede Datei höchstens einmal gelesen |
| Algorithmen | quadratische Schleifen (z. B. Regex je Datei × Suchbegriff × Attribut in `MappingAnalysis`, `AttributeIndex.relevantTables`), unnötig kompilierte Patterns in Schleifen, String-Konkatenation in Schleifen | keine vermeidbaren O(n²) bei großen Eingaben |
| Speicher | Größte Speicherspitze beim großen Projekt (`-Xmx`-Grenze ermitteln). Werden ganze Dateiinhalte unnötig gehalten? | läuft mit `-Xmx512m` |
| Oberfläche | Start bis zum ersten Fenster; Wechsel von Job und Projekt. **Blockieren Datei-I/O-Aufrufe den EDT?** (z. B. `updateProjectInfo` liest DDL, Zielmodell und Vorlagen beim Projektwechsel, Entitätsvorschläge, Basisdaten-Seite) | Start < 3 s, Wechsel < 200 ms, kein I/O > 50 ms auf dem EDT |
| Log/Konsole | Verhalten bei sehr vielen Logzeilen | kein Einfrieren |

Werkzeuge nach Wahl: CLI-Läufe mit Zeitmessung, JFR (`-XX:StartFlightRecording`), ein kleiner JMH- oder JUnit-Benchmark außerhalb des Repos, Thread-Dumps.

### P5 – Codequalität und Wartbarkeit

- Struktur: Zuständigkeiten (core/jobs/tools/ui), Abhängigkeitsrichtung (ui → jobs → tools → core?), Zyklen.
- Duplikate: z. B. Pfadfelder, JSON-Parser (`core/Json` vs. Parser in `TargetModel`), Layout-Helfer.
- Toter Code: z. B. `tools/importer/ImplementationImport` (nicht angebunden?), ungenutzte Methoden, Konstanten und Keys (`Keys.REFERENCE_DDL` …).
- Größe und Komplexität: Klassen und Methoden, die zu groß sind (vor allem `MainWindow`, `ProjectEditor`).
- Konsistenz: Benennung (Deutsch/Englisch), veraltete Begriffe nach den Umbenennungen („Basisklasse“, „Standards“ vs. „Basisdaten“).

### P6 – Tests

- Grad der Abdeckung je Paket: Welche Kernlogik ist **ungetestet**?
- Qualität: Prüfen die Tests Verhalten oder nur Strings? Sind sie stabil (unabhängig von Benutzerdaten in `migrationsprojekte/`, Systemeigenschaften, Reihenfolge, Uhrzeit)?
- Fehlende Grenzfall-Tests zu P1 und P3.

### P7 – Oberfläche und Bedienbarkeit

Erstelle Screenshots, falls möglich. Die Anwendung kann über `-Ddevtoolbox.page=ausfuehren|projekt|basisdaten|hilfe` direkt auf einer Seite starten. Prüfe dabei:

- Abgeschnittene oder überlappende Komponenten bei 1360×1000 und bei 980×640.
- Konsistenz von Karten, Abständen und Beschriftungen auf allen Seiten.
- Verständlichkeit der Texte, Rückmeldungen nach Aktionen, Tastaturbedienung (Tab-Reihenfolge, Strg+Enter).

### P8 – Sicherheit

- Pfad-Traversal beim Schreiben, z. B. Dateinamen aus Vorlagen oder Entitäten im Ausgabepfad.
- Ausführen externer Programme (`Desktop.open`) mit Eingaben aus Dateien.
- Geheimnisse im Repo (Schlüssel, Tokens, persönliche Daten).

## 4. Vorgehen

1. Einlesen: README, Paketstruktur, die drei Jobs vollständig.
2. `mvn clean test` und `mvn package`: Ergebnis und Dauer notieren.
3. P1 bis P8 abarbeiten; für P4 die Messungen mit Testprojekt und synthetischem Großprojekt durchführen.
4. Befunde konsolidieren, nach Schwere sortieren, Duplikate zusammenfassen.

## 5. Bewertung der Befunde

| Schwere | Bedeutung |
|---|---|
| **Kritisch** | falsches Ergebnis, Datenverlust, Schreiben ins Migrationsprojekt, Absturz im Normalfall |
| **Hoch** | falsches Ergebnis in plausiblen Fällen, Einfrieren der Oberfläche, Zielwert aus P4 deutlich verfehlt |
| **Mittel** | Grenzfall-Fehler, irreführende Meldung, fehlende Tests für Kernlogik, deutliche Wartbarkeitsprobleme |
| **Niedrig** | Kosmetik, Benennung, kleinere Vereinfachungen |

## 6. Ergebnis: Form und Ablage

Lege das Ergebnis als **eine Markdown-Datei** `review/REVIEW-<JJJJ-MM-TT>.md` an. Sie wird nicht committet; ist das nicht möglich, gib sie vollständig als Antwort aus. Gliederung genau so:

1. **Zusammenfassung** (höchstens 10 Zeilen): Gesamturteil, die 3 wichtigsten Risiken, Testergebnis, die wichtigsten Messwerte.
2. **Befundliste:** eine Tabelle, sortiert nach Schwere.

   | ID | Schwere | Bereich (P1–P8) | Befund (1 Satz) | Beleg (Datei:Zeile / Messwert) | Auswirkung | Empfehlung (konkret) | Aufwand (S/M/L) |
   |---|---|---|---|---|---|---|---|

   IDs fortlaufend: R-01, R-02, …
3. **Performance-Messungen:** eine Tabelle mit Szenario, Datenmenge, Messweg, Ergebnis und Zielwert (erreicht ja/nein), danach die 3 größten Hotspots mit Datei:Zeile und Ursache.
4. **Testlücken:** Liste der ungetesteten Kernfunktionen mit Vorschlag für den jeweils wichtigsten Testfall.
5. **Empfohlene Reihenfolge:** die Befunde gruppiert zu höchstens 5 Arbeitspaketen, jeweils mit Ziel, enthaltenen IDs und Aufwand.
6. **Anhang:** Messskripte bzw. Befehle, Umgebung (JDK, Betriebssystem, Rechnerdaten), was nicht geprüft werden konnte und warum.

Halte es kurz: keine Code-Auszüge über 15 Zeilen, keine Wiederholung des README, keine Befunde ohne Beleg.
