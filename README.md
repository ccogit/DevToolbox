# DevToolbox

Eine kleine Java-25-Anwendung mit Jobs, die **Aufgaben** für MS Copilot erzeugen. Zur Bearbeitung einer Aufgabe
braucht Copilot einen **Skill** (die allgemeine Methode). Copilot nimmt nur 3 Dateien pro Prompt an, deshalb liefert
ein Job genau drei Dateien: Skill + Aufgabe + Inhalt.

## Jobs

### Migrationsspezifikation je Phase (fachliche Entität)

Durchsucht ein Projekt (z. B. eine bestehende Spring-Batch-Migration) nach einer fachlichen Entität wie
`Risiko` (mit Synonymen als Suchbegriffe, z. B. `Risiko, Risk`) und erzeugt:

- `skill-phasen-spezifikation.md` – der **Skill**: Methode, Arbeitsregeln und Gliederung für jede Phase. Er hängt
  nicht von der Entität ab und ist für alle Aufgaben dieses Typs gleich.
- `aufgabe-<entität>.md` – die **Aufgabe**: Entität, Suchbegriffe, zu betrachtende Phasen, Zusatzkontext,
  zugehörige Code-Datei und die erwartete Gliederung des Ergebnisses.
- `code-<entität>.md` – zuerst das **Attributverzeichnis** der Entität, dann der gesamte zugehörige Code mit klaren
  Dateigrenzen (`===== BEGIN FILE … =====` / `===== END FILE … =====`), gruppiert nach
  Read → Process → Write → Datenzugriff → Konfiguration → Modell.

Read und Process werden **attributweise** spezifiziert: eine Tabellenzeile je Attribut, Quellen, Klassen, Regeln
und Belege als Spalten. Attribute, die nicht gelesen oder gemappt werden, erscheinen ausdrücklich.
Primärquelle der Attribute ist die **DDL der Altdatenbank** (DB2 for z/OS: `CREATE TABLE`, `ALTER TABLE … ADD`,
`LABEL ON`, `COMMENT ON`). Aufgenommen werden Tabellen, deren Name einen Suchbegriff enthält oder die im
eingesammelten Code vorkommen (SQL, `@Table`, Konstanten). Ohne DDL kommen die Attribute aus den
Datenstrukturen im Code (TOs, POs, DTOs, Entities inkl. `@Column`).

Prompt in Copilot (steht auch im Protokoll): „Bearbeite die Aufgabe in aufgabe-<entität>.md mit dem Skill
skill-phasen-spezifikation.md.“

Wichtige Einstellungen:

| Einstellung | Wirkung |
|---|---|
| Fachliche Entität | Name zuerst, dann Synonyme als Suchbegriffe, kommagetrennt; Groß-/Kleinschreibung egal |
| DDL der Altdatenbank | Optional: Datei oder Ordner mit `.ddl`/`.sql`, primäre Quelle des Attributverzeichnisses |
| Detailgrad der Spezifikation | **Kompakt**: je Phase die Attributtabellen. **Ausführlich** (Standard): zusätzlich die einzelnen Schritte des Einlesens und Verarbeitens je Attribut sowie die Schreibschritte |
| Umfang | Nur Klassen mit dem Begriff oder zusätzlich referenzierte Projektklassen. Datenstrukturen (TO, PO, DTO, Entity …) werden transitiv verfolgt, damit z. B. `RisikoTO → DeckungTO → SelbstbeteiligungTO` vollständig ankommt |
| Betrachtete Phasen | Read / Process / Write. Wirkt auf den Code (andere Phasen entfallen) und auf den Skill |
| Package/Import ignorieren | Begriff nur im Package-Namen (`de.vers.risk`) ist kein Treffer |
| Max. Tokens/Datei | Teilt die Code-Datei an Dateigrenzen auf (`code-…-teil1.md` …). Für Copilot leer lassen, da neben Skill und Aufgabe nur eine Code-Datei Platz hat |
| Kompakt | Entfernt Imports und Lizenzköpfe (spart Tokens) |

Die Phasen-Zuordnung ist heuristisch (Spring-Batch-Interfaces, Namen, Packages). Feste Regel mit Vorrang: Klassen in
den Verzeichnissen `anreicherer`/`anreicherung` gehören immer zu Read, in `mapper`/`mapping` immer zu Process und in
`write`/`writer` immer zu Write. Die Regel steht in `PhaseClassifier.FIXED_DIRECTORIES`.

Die Texte liegen in `src/main/resources/skills/phase-spec/` (Skill) und `src/main/resources/aufgaben/phase-spec/`
(Aufgabe) und lassen sich ohne Java-Kenntnisse anpassen.

## Migrationsprojekte

Die Jobs analysieren jeweils ein bestehendes Migrationsprojekt, also die Migration eines Produkts einer Sparte
(z. B. VGB = Wohngebäude, PLG = Privat, Landwirtschaft, Gewerbe, später etwa VHV = Hausrat). Jedes Projekt ist ein
Ordner unter `migrationsprojekte/`:

```
migrationsprojekte/
  vgb/
    projekt.json    Name, Sparte, Produkt, root (Projektverzeichnis), ddl (Schema-Beschreibung)
    ddl/            DDL der Altdatenbank dieses Produkts (*.ddl / *.sql)
  plg/ …
  test/             zeigt auf testprojekt/ (zum Ausprobieren)
```

```json
{
  "name": "VGB",
  "sparte": "Sachversicherung",
  "produkt": "Wohngebäude",
  "root": "C:/Projekte/migration-vgb",
  "ddl": "ddl"
}
```

- Pfade dürfen absolut oder relativ zum Projektordner sein.
- Wählst du in der Oberfläche ein Migrationsprojekt, sind Projektverzeichnis und DDL in der Job-Konfiguration
  sofort gesetzt; im Einzelfall lassen sie sich überschreiben. Auf der Kommandozeile: `project=vgb`.
- Neues Produkt: Ordner mit `projekt.json` (und `ddl/`) anlegen – ohne Codeänderung.
- Der Ordner `migrationsprojekte` wird im Arbeitsverzeichnis oder neben der Jar gesucht; ein anderer Ort lässt sich
  mit `-Ddevtoolbox.projekte=<pfad>` angeben.
- **Für VGB und PLG ist `root` noch leer** und muss einmal eingetragen werden.

## Bauen und starten

Voraussetzungen: JDK 25 und Maven.

```
mvn package
java -jar target/devtoolbox.jar                       # Oberfläche
java -jar target/devtoolbox.jar konfig.json           # ohne Oberfläche, Job steht in der Datei ("job")
java -jar target/devtoolbox.jar phase-spec project=vgb "terms=Risiko,Risk" phases=READ,PROCESS detail=compact
java -jar target/devtoolbox.jar hilfe                 # listet alle Jobs und Parameter
```

In der Oberfläche lassen sich Konfigurationen als JSON speichern und wieder laden.

## Erweitern

- **Neuer Job:** `Job` implementieren oder, für „Tool erzeugt Inhalt, Job schreibt Aufgabe + Skill“, von `TaskJob` erben.
  Dann eine Zeile in `JobRegistry` ergänzen. Die Eingabemaske entsteht automatisch aus den `Param`-Definitionen.
- **Neues Tool (Baustein):** `Tool` implementieren und im Job verwenden. Tools erscheinen nicht in der Auswahl.

## Testmaterial

- `testprojekt/` – kleine Beispielmigration „Risiko“ mit absichtlichen Stolperfallen. Testfälle und
  Sollwerte stehen in `testprojekt/TESTFAELLE.md`, fertige Konfigurationen in `testprojekt/konfig/`.
- `copilot-testdateien/` – Markdown-Dateien mit ca. 10.000 bis 500.000 Tokens. Damit lässt sich ermitteln,
  wie viel Copilot annimmt und tatsächlich liest. Anleitung und Lösungen stehen in
  `copilot-testdateien/LOESUNG.md`.
