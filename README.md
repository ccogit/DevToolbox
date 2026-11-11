# DevToolbox

Eine kleine Java-25-Anwendung mit Jobs, die **Aufgaben** für MS Copilot erzeugen. Zur Bearbeitung einer Aufgabe
braucht Copilot einen **Skill** (die allgemeine Methode). Copilot nimmt nur 3 Dateien pro Prompt an, deshalb liefert
ein Job genau drei Dateien: Skill + Aufgabe + Inhalt.

## Jobs

### Spezifikation aus Referenzprojekt (je Phase, fachliche Entität)

Durchsucht das **Referenzprojekt** – eine bereits umgesetzte, sehr ähnliche Spring-Batch-Migration eines anderen
Produkts – nach einer fachlichen Entität wie `Risiko` (mit Synonymen als Suchbegriffe, z. B. `Risiko, Risk`)
und erzeugt daraus die Vorlage einer Spezifikation für das **Zielprojekt**:

- `skill-phasen-spezifikation.md` – der **Skill**: Methode, Arbeitsregeln und Gliederung für jede Phase. Er hängt
  nicht von der Entität ab und ist für alle Aufgaben dieses Typs gleich.
- `aufgabe-<entität>.md` – die **Aufgabe**: Entität, Suchbegriffe, zu betrachtende Phasen, Zusatzkontext,
  zugehörige Code-Datei und die erwartete Gliederung des Ergebnisses.
- `code-<entität>.md` – zuerst das **Attributverzeichnis** der Entität, dann der gesamte zugehörige Code mit klaren
  Dateigrenzen (`===== BEGIN FILE … =====` / `===== END FILE … =====`), gruppiert nach
  Read → Process → Write → Datenzugriff → Konfiguration → Modell.

Das Ergebnis ist bewusst **nicht fragmentiert und ohne Redundanz**: je Phase **eine** Attributtabelle (eine Zeile je
Attribut; Quelle, Regel, Prüfbedarf, OPL-Bezug und Beleg als Spalten; verschachtelte Strukturen flach als
`vertrag.tarif`) plus die Satzregeln (Selektion, Filter, Sortierung …), dazu eine Zusammenfassung, eine kurze
Ablaufübersicht und die **Offene-Punkte-Liste (OPL)**, in der alle Fragen konsolidiert sind. Jede Regel ist als
[PRÜFEN], [TECHNISCH] oder [1:1] gekennzeichnet und verweist in der Spalte „OPL“ auf zugehörige offene Punkte.
Primärquelle der Attribute ist die **DDL der Altdatenbank** (DB2 for z/OS: `CREATE TABLE`, `CREATE VIEW`, `ALTER TABLE … ADD`,
`LABEL ON`, `COMMENT ON`). Aufgenommen werden Tabellen, deren Name einen Suchbegriff enthält oder die im
eingesammelten Code vorkommen (SQL, `@Table`, Konstanten). Ohne DDL kommen die Attribute aus den
Datenstrukturen im Code (TOs, POs, DTOs, Entities inkl. `@Column`).

Prompt in Copilot (steht auch im Protokoll): „Bearbeite die Aufgabe in aufgabe-<entität>.md mit dem Skill
skill-phasen-spezifikation.md.“ Copilot stellt das Ergebnis als **Markdown-Datei `spezifikation-<entität>.md` zum
Download** bereit; nach der Abstimmung mit dem Fachbereich wird daraus die PDF für den Implementierungs-Job.

Wichtige Einstellungen:

| Einstellung | Wirkung |
|---|---|
| Fachliche Entität | Name zuerst, dann Synonyme als Suchbegriffe, kommagetrennt; Groß-/Kleinschreibung egal |
| DDL der Altdatenbank | Optional: Datei oder Ordner mit `.ddl`/`.sql`, primäre Quelle der Quellattribute |
| Zielmodell (JSON je Entität) | Pflicht bei Phase Process: Ordner mit einer JSON-Datei je Entität; die Attributtabelle Process hat dann eine Zeile je Zielattribut (aus `attributname`/`attributeName`) |
| Detailgrad der Spezifikation | Gleiche Tabellen, unterschiedlich genaue Zellen: **Kompakt** beschreibt jede Regel in einem Satz, **Ausführlich** (Standard) als nummerierte Schritte mit Bedingung und Klasse.Methode |
| Umfang | Nur Klassen mit dem Begriff oder zusätzlich referenzierte Projektklassen. Datenstrukturen (TO, PO, DTO, Entity …) werden transitiv verfolgt, damit z. B. `RisikoTO → DeckungTO → SelbstbeteiligungTO` vollständig ankommt |
| Betrachtete Phasen | Read / Process / Write. Wirkt auf den Code (andere Phasen entfallen) und auf den Skill |
| Package/Import ignorieren | Begriff nur im Package-Namen (`de.vers.risk`) ist kein Treffer |
| Max. Tokens/Datei | Teilt die Code-Datei an Dateigrenzen auf (`code-…-teil1.md` …). Für Copilot leer lassen, da neben Skill und Aufgabe nur eine Code-Datei Platz hat |
| Kompakt | Entfernt Imports und Lizenzköpfe (spart Tokens) |

Die Phasen-Zuordnung ist heuristisch (Spring-Batch-Interfaces, Namen, Packages). Feste Regeln mit Vorrang, in
dieser Reihenfolge (`PhaseClassifier`):

1. Klassen `…Repository` gehören immer zu **Read** (ein Repository je Entität, es liest die Daten) – auch in einem
   Write-Verzeichnis.
2. Klassen in den Verzeichnissen `anreicherer`/`anreicherung` gehören immer zu Read, in `mapper`/`mapping` immer zu
   Process und in `write`/`writer` immer zu Write (`FIXED_DIRECTORIES`).

Alle Namens- und Verzeichniskonventionen (Klassen-Endungen je Phase, Datenstrukturen TO/PO/DTO/Entity, Präfixe
Alt/Neu, feste Regeln) stehen zentral in `Conventions`; sie steuern sowohl die Phasen-Zuordnung als auch die
Erkennung der Artefakte beim Erzeugen der Standards. Eine neue Konvention wird nur dort ergänzt.

Die Texte liegen in `src/main/resources/skills/phase-spec/` (Skill) und `src/main/resources/aufgaben/phase-spec/`
(Aufgabe) und lassen sich ohne Java-Kenntnisse anpassen.

### Implementierung nach Spezifikation (je Phase, fachliche Entität)

Der zweite Schritt: Aus der Spezifikation (Ergebnis des ersten Jobs, vom Fachbereich geprüft und geändert, als **PDF**)
soll Copilot die gewählten Phasen (Read, Process, Write) der Entität im **Zielprojekt** implementieren – und zwar
**anhand der Standards des Projekts**.

**Standards** werden einmal je Migrationsprojekt erzeugt und können danach überarbeitet werden: im Projekt-Editor
(„Bearbeiten…“ → „Standards erzeugen…“, bei neuen Projekten direkt nach dem Anlegen angeboten). Quelle ist der Code
dieser Migration (`root`), das Referenzprojekt, ein anderes Migrationsprojekt oder ein frei gewähltes
Code-Verzeichnis; Beispiel-Entität ist
die vollständigste oder eine ausgewählte. Abgelegt werden sie in `migrationsprojekte/<id>/standards/`:

```
standards/
  STANDARDS.md        Index: Phase, Artefakt, Vorlage, Klassen-/Package-/Pfad-Muster, Basisklasse, Hinweise
  vorlagen/*.java     Vorlagen mit Platzhaltern {{Entitaet}} / {{entitaet}} / {{ENTITAET}}
  basis/*.java        Basisklassen und Schnittstellen
```

Zeilen in `STANDARDS.md` lassen sich ändern, löschen oder ergänzen, Vorlagen bereinigen, und in „Hinweise“ stehen
Vorgaben für Copilot. Erneutes Erzeugen ersetzt sie nur nach Rückfrage; die bisherigen bleiben als
`standards-<Zeitpunkt>/` erhalten. Zuordnung der Klassen beim Erzeugen:

| Phase | Vorlagen |
|---|---|
| Read | Datenobjekte (TO, PO, Entity, DTO), Repository (eines je Entität) und Einleselogik (Reader, RowMapper, Anreicherer, Klassen in `anreicherer`/`anreicherung`) |
| Process | Verarbeitungslogik (ItemProcessor, Mapper, Converter, Klassen in `mapper`/`mapping`) |
| Write | Schreiblogik (ItemWriter, Writer, Klassen in `write`/`writer`) |
| Read, Write | Datenzugriff (DAOs) |

Der Job liest die Standards (Feld „Standards (Ordner)“, aus dem Projekt vorbelegt), nimmt die Zeilen der gewählten
Phasen und setzt die Entität in Klassennamen und Packages ein. Ergebnis zum Hochladen:

- `skill-implementierung.md` – der **Skill**: Struktur aus den Vorlagen, Inhalt aus der Spezifikation; vollständiger
  Code, offene Punkte als `// TODO [OFFEN]`.
- `aufgabe-implementierung-<entität>.md` – die **Aufgabe** mit **einer** Tabelle der zu erstellenden Artefakte
  (Phase, neue Klasse und Package – z. B. `{{Entitaet}}TO` → `RisikoTO` –, Vorlage, Basisklasse/Schnittstellen,
  Annotationen, Hinweise) und den **Standards**: Vorlagen und Basisklassen.
  Erwartetes Ergebnis: Code (offene Stellen als `// TODO OPL-n`), „Umsetzung je Attribut“, „Artefakte“
  (Abweichungen von der Vorlage) und die OPL – ohne Redundanz.
- `spezifikation-<entität>.pdf` – die Spezifikation im Original (Copilot liest PDFs direkt, Tabellen bleiben erhalten).

Bei Phase Process nutzt der Job zusätzlich das **Zielmodell** des Projekts (Pflicht): Die Zielattribute der Entität
stehen einmal in der Aufgabe und legen fest, welche Zielfelder es gibt; „Umsetzung je Attribut“ hat dann eine Zeile
je Zielattribut, Lücken und Abweichungen zur Spezifikation werden OPL-Punkte.

Copilot stellt das Ergebnis als **ZIP `implementierung-<entität>.zip` zum Download** bereit: jede Datei an ihrem
Pfad aus der Spalte „Pfad“ der Standards (relativ zum Root des Zielprojekts, inkl. Modulordner) – das ZIP lässt sich
direkt über den Root entpacken –, dazu `IMPLEMENTIERUNG.md` (Umsetzung je Attribut, Artefakte, OPL).

### Mapping-Dokumentation aus implementiertem Code (je Entität)

Der dritte Schritt: Nach Abschluss der Implementierung wird für jede Entität das **tatsächlich implementierte
Mapping** dokumentiert, je Attribut getrennt für **Bestand** und **Schwebe**. Das steht in zwei Dokumenten:

- **Zielmodell-JSON** der Entität (`doc/migration/properties`): die Quelle, Felder `mapping`/`schwebeMapping`;
- **Confluence-Tabelle** (`resources/documentation`): Spalten „Mapping“/„Schwebe Mapping“. Normalerweise wird sie
  mit einem eigenen Tool aus dem JSON erzeugt; hier füllt Copilot beide aus.

```
||Entität||Attribut||Mapping||Schwebe Mapping||Spezifikation||Status||
|NachlassZuschlag|DiffStatus|{status:colour=Red|title=TBD|subtle=true}|{status:colour=Red|title=TBD|subtle=true}|…|
| |NachlassZuschlagArt|…|
```

**Grundlage ist ausschließlich der Code des Migrationsprojekts** (`root`). Das Tool `MappingDocTool`:

1. findet JSON und Tabelle der Entität – ohne Angabe im Modul `<parent>-designer-services` neben dem
   Migrationsmodul (siehe [Projektstruktur](#projektstruktur)); das JSON über `entityTypeName`, die Tabelle über die
   Entität in der Tabelle bzw. den Dateinamen;
2. trägt vorab ein, was feststeht: **Spezifikation** = `{jiraissues:HAFT-6554}` (Eingabe „Jira-Ticket“; eine reine
   Nummer bekommt das Präfix der Sparte: Haftpflicht `HAFT`, Sach `SACH`) und **Status** =
   `{status:colour=Green|title=FERTIG|subtle=true}`;
3. sammelt den Code der Entität: **Process-Phase** (Mapping), **Read-Phase und Datenzugriff** (daraus ergeben sich
   die Quellspalten) und die Datenstrukturen;
4. erstellt eine heuristische **Voranalyse**: je Attribut die Fundstellen im Process-Code (`Klasse.Methode:Zeile`),
   ob sie in einer Methode mit Schwebe-Bezug liegen, und alle Process-Zeilen, die „Schwebe“ erwähnen;
5. nimmt, falls ein **Referenzprojekt** angegeben ist (aus dem Migrationsprojekt vorbelegt), dessen Process-Code
   **nur zum Vergleich** auf (Dateien mit Präfix `REFERENZ/`). Mappt es ein Attribut fachlich anders, ergänzt Copilot
   die Regel um „Hinweis: abweichend vom Referenzprojekt (dort: …)“.

Ergebnis zum Hochladen (3 Dateien):

- `skill-mapping-dokumentation.md` – der **Skill**: Bestand und Schwebe unterscheiden (Regelfall „wie Bestand“,
  Sonderwege nur mit Beleg im Code), Regeln im Stil „Wenn F951.BSZ_SCHL != null, dann …, ansonsten …“ mit
  Tabelle.Spalte als Quelle, JSON und Tabelle wortgleich, Struktur unverändert.
- `aufgabe-mapping-dokumentation-<entität>.md` – die **Aufgabe** mit Voranalyse, Code und ggf. Referenz-Code.
- `vorlagen-mapping-<entität>.md` – die beiden **vorbefüllten Dokumente** als Dateiblöcke.

Copilot stellt **beide Dateien zum Download** bereit: das JSON unter seinem Originalnamen (z. B.
`NachlassZuschlag.json`) und die Tabelle als `.txt` (bzw. `.md`). Ins echte Projekt wird (noch) nichts geschrieben.

## Migrationsprojekte

Ein Migrationsprojekt ist die Migration eines Produkts einer Sparte:

| Id | Produkt | Sparte |
|---|---|---|
| `vgb` | Wohngebäude | Sachversicherung |
| `vhv` | Hausrat | Sachversicherung |
| `plg` | Privat, Landwirtschaft, Gewerbe | Haftpflichtversicherung |

Ausgewählt wird das **Zielprojekt**, für das spezifiziert wird. Durchsucht wird der Code seines
**Referenzprojekts**. Jedes Projekt ist ein Ordner unter `migrationsprojekte/`:

```
migrationsprojekte/
  plg/
    projekt.json    Name, Sparte, Produkt, root, referenz, ddl
    ddl/            Schema-Beschreibung: DDL der Altdatenbank dieses Produkts (*.ddl / *.sql)
  vgb/ …  vhv/ …
  test/, testziel/  Beispiel: TESTZIEL nutzt TEST (= testprojekt/) als Referenz
```

```json
{
  "name": "PLG",
  "sparte": "Haftpflichtversicherung",
  "produkt": "Privat, Landwirtschaft, Gewerbe",
  "root": "C:/Projekte/firma-haftpflichtvertrag/firma-haftpflichtvertrag-migration-plg",
  "referenz": "vgb",
  "ddl": "ddl",
  "zielmodell": "",
  "mappingvorlagen": ""
}
```

| Eintrag | Bedeutung |
|---|---|
| `root` | Code dieser Migration: Quelle der Standards und Referenz, wenn ein anderes Projekt dieses nennt |
| `referenz` | Das Referenzprojekt, dessen Code durchsucht wird: **Id eines anderen Projekts** (dann gilt dessen `root`) oder ein Pfad |
| `ddl` | Schema-Beschreibung dieses (Ziel-)Produkts, primäre Quelle der Quellattribute (Read) |
| `zielmodell` | Ordner mit den JSON-Dateien des Zielmodells: liefert die Zielattribute für die Phase Process. Entitäten über `entityTypeName`/`entitaet` (auch mehrere je Datei), Attribute über `attributeName`/`attributname`; weitere Angaben je Attribut (`mapping`, `schwebeMapping`, `specification`, `status`) werden mit übergeben. **Leer = aus `root` abgeleitet** (siehe unten). Pflicht, wenn Process gewählt ist |
| `mappingvorlagen` | Ordner mit einer Mapping-Vorlage (Confluence-Tabelle) je Entität – für den Job „Mapping-Dokumentation“. **Leer = aus `root` abgeleitet** |
| `sparte` | Leer = aus dem Namen des Parent-Projekts (`…haftpflicht…` / `…sach…`); bestimmt das Jira-Präfix |

### Projektstruktur

Alle Entwicklungsprojekte liegen unter `C:\firma-develop`, darin die Projekte der Sparten. Ein Migrationsprojekt
(`root`) ist ein Modul im Projekt seiner Sparte; daneben liegt immer `<parent>-designer-services` mit Zielmodell
und Mapping-Vorlagen:

```
C:\firma-develop\
  firma-haftpflichtvertrag\                       Haftpflicht (PLG)
    <migrationsmodul mit "plg" im Namen>\       → root
    firma-haftpflichtvertrag-designer-services\
      doc\migration\properties\                 Zielmodell: ein JSON je Entität     → zielmodell
      resources\documentation\                  Mapping-Vorlagen (Confluence)       → mappingvorlagen
  firma-sachvertrag\                              Sach (VGB, VHV) – gleicher Aufbau
```

Daraus leitet die Anwendung alles selbst ab (`ProjectLayout`), wenn in der `projekt.json` nichts eingetragen ist:

| Was | Abgeleitet aus |
|---|---|
| Sparten-Projekt | Sparte: `firma-haftpflichtvertrag` bzw. `firma-sachvertrag`, fehlt es: das einzige `firma-…haftpflicht…` / `firma-…sach…` |
| `root` | Modul im Sparten-Projekt mit der Projekt-Id als Namensteil (`…-vgb-…`); hat keines die Id, das einzige Modul mit „migration“ im Namen |
| `zielmodell`, `mappingvorlagen` | Modul `*-designer-services` neben `root` (bzw. im Sparten-Projekt) |
| `sparte` | Name des Parent-Projekts von `root` |
| `referenz` als Projekt-Id | abgeleiteter `root` des anderen Projekts |

Alles bleibt **überschreibbar**: Im Projekt-Editor erscheinen die abgeleiteten Werte sofort (nach Sparte/Id bzw.
nach „Code dieser Migration“); ein abweichender Pfad wird gespeichert, ein Pfad gleich dem abgeleiteten nicht (er
folgt dann der Struktur). In den Jobs werden Zielmodell und Mapping-Vorlagen nach Eingabe von „Code des
Migrationsprojekts“ vorbelegt. Ein anderer Entwicklungsordner: `-Ddevtoolbox.develop=<pfad>`. Die JSON-Datei einer
Entität wird über `entityTypeName` gefunden; andere JSON-Dateien im Ordner (ohne Entität) werden ignoriert.

- **Schema-Beschreibung:** Die DDL der Alt-Tabellen, aus denen die Entitäten in der Read-Phase gelesen werden
  (DB2 for z/OS, möglichst mit `LABEL ON`/`COMMENT ON`; Schlüssel und eindeutige Indizes optional), wird über **„Schema importieren…“** neben
  der Projektauswahl übernommen: Das Tool prüft die Datei, meldet die erkannten Tabellen und Spalten und legt sie in
  `migrationsprojekte/<id>/ddl/` ab (bei Bedarf wird `ddl` in der `projekt.json` gesetzt). Die Projektzeile zeigt
  den Stand („Schema: 4 Tabellen, 21 Spalten“). Anforderungen an die Datei: `migrationsprojekte/<id>/ddl/README.md`.
- Pfade dürfen absolut oder relativ zum Projektordner sein.
- Wählst du in der Oberfläche ein Migrationsprojekt, sind Referenzprojekt und DDL in der Job-Konfiguration sofort
  gesetzt; im Einzelfall lassen sie sich überschreiben. Auf der Kommandozeile: `project=plg`.
- **Bearbeiten in der Oberfläche:** „Neu…“ legt ein Migrationsprojekt an (Ordner, `projekt.json`, `ddl/`),
  „Bearbeiten…“ ändert die `projekt.json` des gewählten Projekts in einer Maske wie der Job-Konfiguration
  (Referenz als Auswahl der anderen Projekte oder als Pfad). Pfade im Projektordner werden relativ gespeichert,
  bestehende relative Angaben bleiben erhalten, unbekannte Einträge ebenfalls. „Löschen…“ im Bearbeiten-Dialog
  entfernt nach Rückfrage den Projektordner (`projekt.json`, Schema-Beschreibung); Code, Referenz und Zielmodell
  außerhalb bleiben erhalten. Projekte, die es als Referenz nennen, zeigen danach „Referenzprojekt … nicht gefunden“.
- Neues Produkt ohne Codeänderung – über „Neu…“ oder einen Ordner mit `projekt.json`.
- Der Ordner `migrationsprojekte` wird im Arbeitsverzeichnis oder neben der Jar gesucht; ein anderer Ort lässt sich
  mit `-Ddevtoolbox.projekte=<pfad>` angeben.
- **Für VGB, VHV und PLG** ergeben sich `root`, Zielmodell und Mapping-Vorlagen aus `C:\firma-develop`; einzutragen ist nur noch `referenz` (und `root`, falls die Modulnamen die Id nicht enthalten).

## Bauen und starten

Voraussetzungen: JDK 25 und Maven.

```
mvn package
java -jar target/devtoolbox.jar                       # Oberfläche
java -jar target/devtoolbox.jar konfig.json           # ohne Oberfläche, Job steht in der Datei ("job")
java -jar target/devtoolbox.jar phase-spec project=vgb "terms=Risiko,Risk" phases=READ,PROCESS detail=compact
java -jar target/devtoolbox.jar hilfe                 # listet alle Jobs und Parameter
```

In der Oberfläche lassen sich Konfigurationen als JSON speichern und wieder laden. Beide Dialoge öffnen
`src/main/resources/konfigurationen/`; der vorgeschlagene Name zeigt den Inhalt, z. B.
`vgb-spezifikation-risiko-alle-phasen.json` (Projekt – Job – Entität – Phasen – ggf. „kompakt“). Ohne Quellcode
(nur Jar) wird `konfigurationen/` neben `migrationsprojekte/` verwendet.

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
