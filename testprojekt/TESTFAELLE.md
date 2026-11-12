# Testprojekt für die DevToolbox

Eine kleine, nicht baubare Spring-Batch-Migration „Wohngebäude alt (WG2010)“ zur fachlichen Entität **Risiko**.
Sie ist nur zum Durchsuchen gedacht. `ddl/altbestand.ddl` enthält die DDL der Altdatenbank (DB2 for z/OS).

Für alle Fälle gilt: **Referenzprojekt (Code)** = dieser Ordner, **Fachliche Entität** = `Risiko, Risk`.
Fertige Konfigurationen liegen in `konfig/`. In der Oberfläche lädst du sie mit „Konfiguration laden…“.
Ohne Oberfläche: `java -jar target/devtoolbox.jar testprojekt/konfig/<datei>.json`.

## Aufbau

| Phase | Treffer (enthalten „Risiko/Risk“) | Referenzen (ohne Begriff, nur über Referenzen) |
|---|---|---|
| Read | `AltRisikoItemReader`, `AltRisikoRowMapper`, `select_alt_risiken.sql`, `anreicherung/RisikoDeckungsAnreicherer`, `write/NeuRisikoRepository` (Repository = immer Read) | – |
| Process | `RisikoItemProcessor`, `RisikoartMapper` (CRLF), `OberrisikoZuordnung` (Windows-1252), `mapping/RisikoSchluesselDao` | `BeitragsConverter` |
| Write | `RisikoItemWriter`, `writer/RisikoExportConfig`, `insert_risiko.sql` (BOM + CRLF) | `FehlerProtokollWriter` |
| Datenzugriff | – | `AltBestandDao` (Nachlesen der Versicherungssumme) |
| Konfiguration | `RisikoMigrationJobConfig`, `application.yml`, `batch-context.xml` | `JobProtokollListener` |
| Modell | `AltRisikoDto`, `NeuRisikoEntity`, `RisikoTO`, `RisikoPO`, `create_risiko.sql` | `AltVertragDto`, `Adresse`, `Zahlweise`, `Konstanten` (statischer Import), `DeckungTO` (Ebene 2), `SelbstbeteiligungTO` (Ebene 3) |

Absichtliche Stolperfallen:

- `kunde/*` – anderes Thema, darf **nie** erscheinen.
- `inkasso/BeitragsInkassoWriter` – erwähnt „Risk“ nur in einem TODO-Kommentar. Mit „Kommentare ignorieren“
  (Vorgabe) ist das **kein Treffer**; das Protokoll nennt die Datei. Mit `ignoreComments=false` ist es der bekannte
  Fehltreffer der rein textuellen Suche.
- `risiko/Pruefziffer` – enthält den Begriff nur im Package-Namen.
- `RisikoTO → DeckungTO → SelbstbeteiligungTO` – verschachtelte Transferobjekte. Datenstrukturen werden transitiv
  verfolgt, also muss auch `SelbstbeteiligungTO` (Ebene 3) ankommen.
- `util/BetragFormatierer` – wird von `DeckungTO` genutzt, ist aber keine Datenstruktur und darf **nicht** erscheinen.
- Feste Verzeichnisregel: Klassen in `anreicherer/anreicherung` gehören immer zu Read, in `mapper/mapping` immer zu
  Process, in `write/writer` immer zu Write – unabhängig von Interface oder Namen. Geprüft wird das mit drei Klassen,
  die sonst anders zugeordnet würden: `RisikoDeckungsAnreicherer` (implementiert `ItemProcessor`),
  `RisikoSchluesselDao` (Endung `Dao`) und `RisikoExportConfig` (`@Configuration`).
- `src/test/...RisikoItemProcessorTest` – nur mit „Tests einbeziehen“.
- `target/.../RisikoGeneriert` – liegt im Build-Ordner und darf **nie** erscheinen.
- `RisikoItemProcessor` – enthält ```` ``` ```` im Javadoc; der Markdown-Block muss trotzdem bis zum Dateiende reichen.
- `RisikoMigrationJobConfig` – Lizenzkopf, der im Kompaktmodus entfällt.
- Fachliche Regeln, die die KI finden sollte: Filter auf ruhende Verträge (Status R), +10 % Versicherungssumme im
  Premium-Tarif `T-PREM` (produktspezifisch), Ratenzuschläge 3 %/5 %, Nachlesen der Versicherungssumme über die
  Wohnfläche, Risikoart-Codes FE/LW/ST/EL/GL, Oberrisiko G/H mit Default GEBAEUDE, doppelte IDs → MIG_FEHLER.

## Testfälle

Job ist der kombinierte Job `phase-spec` („Spezifikation aus Referenzprojekt (je Phase, fachliche Entität)“; nur Kommandozeile bzw. JSON-Datei, nicht in der Oberfläche). In der Oberfläche gibt es stattdessen je Phase einen Job (`spec-read`, `spec-process`, `spec-write`, Phase fest, siehe Fälle 29 bis 31). Das Testprojekt ist dabei das **Referenzprojekt**.

| Nr | Einstellung | Erwartung |
|---|---|---|
| 1 | Standard (`konfig/1-standard.json`) | 20 Treffer, 30 Dateien: Read 5, Process 5, Write 4, Datenzugriff 1, Konfiguration 4, Modell 11. Protokoll: „1 Dateien enthalten den Begriff nur in Kommentaren und sind kein Treffer: BeitragsInkassoWriter.java“. Ergebnis: `skill-phasen-spezifikation.md` + `aufgabe-risiko.md` + `code-risiko.md` |
| 2 | Umfang „Nur Klassen mit dem Begriff“ | 20 Dateien, keine Zeile „Referenz von …“, also auch kein `DeckungTO` |
| 3 | Phasen nur „Process“ (`konfig/3-nur-process.json`) | 20 Dateien (Process 5, Konfiguration 4, Modell 11), 8 Treffer weggelassen. Die Aufgabe nennt nur „Process“ (nicht betrachten: Read, Write); Gliederung: Zusammenfassung, Ablaufübersicht, Phase Process, OPL. Soll-Listen: „Quellattribute (kein Soll in diesem Auftrag)“ ohne R-Nummern und „Soll Process – 11 Zeilen (P1–P11)“. Der Skill enthält nur den Abschnitt „Phase Process“ |
| 4 | „Package/Import ignorieren“ aus | 22 Treffer: `Pruefziffer` kommt dazu, `AltBestandDao` wird vom Referenz- zum Treffer |
| 5 | „Tests einbeziehen“ an | 21 Treffer, `RisikoItemProcessorTest` erscheint in der Gruppe „Tests“ |
| 6 | „Kompakt“ (Vorgabe: an) gegen „Kompakt“ aus | Kompakt: keine `import`-Zeilen, kein Lizenzkopf bei `RisikoMigrationJobConfig`, Datei kleiner. Mit `compact=false` sind Importe und Lizenzkopf enthalten |
| 7 | `maxTokens=3000` (die frühere Aufteilung gibt es nicht mehr) | Trotzdem genau eine `code-risiko.md`, ohne Spalte „Teil“, mit genau einem Inhaltsverzeichnis; keine WARNUNG |
| 7b | Copilot-Limit (`copilotLimit`) | Vorgabe 30.000: Protokoll „Copilot-Limit 30.000 – ok“. `copilotLimit=1000`: WARNUNG mit „Empfehlungen: 1. nur eine Phase wählen; 2. Dateien ausschließen (größte nicht geschützte Dateien: …); 3. Umfang „Nur Klassen mit dem Begriff““ (mit DDL zusätzlich „Tabellen der Entität angeben“; genutzte Empfehlungen entfallen). `copilotLimit=0`: keine Prüfung |
| 8 | Fall 1 zweimal hintereinander | Der zweite Lauf schreibt in `…-spezifikation-risiko-2`; der erste Ordner, fremde Dateien darin, Unterordner und andere Ordner bleiben unverändert |
| 9 | In jeder Ausgabe prüfen | Kein `kunde`, kein `RisikoGeneriert`, kein `BetragFormatierer`, kein `BeitragsInkassoWriter` (mit `ignoreComments=false` ist er enthalten). „Gebäude“ in `OberrisikoZuordnung` ist korrekt dargestellt, kein BOM-Zeichen vor `-- Datei mit BOM` |
| 10 | Standard, Inhaltsverzeichnis prüfen | `RisikoDeckungsAnreicherer` steht unter Read, `RisikoSchluesselDao` unter Process, `RisikoExportConfig` unter Write, `NeuRisikoRepository` trotz Verzeichnis `write` unter Read (Repository-Regel hat Vorrang) |
| 11 | DDL der Altdatenbank = `ddl/` (`konfig/2-mit-ddl.json`) | Protokoll „Attributverzeichnis: 4 Tabellen, 21 Attribute aus DDL (Kern: ALT_RISIKO · Neben: ALT_VERTRAG, ALT_OBJEKT, FAKTOREN)“. Die Soll-Listen stehen in der **Aufgabe**, nicht in der Code-Datei: Kerntabelle `ALT_RISIKO` (Name enthält Suchbegriff) mit R1–R8, PK `VNR, RISART`, Fremdschlüssel auf `ALT_VERTRAG`, Bezeichnungen und Kommentaren; die nie gelesene Spalte `SB_BETRAG` ist R7 – die KI soll sie als „nicht gelesen“ melden. Nebentabellen (kein Soll): `ALT_VERTRAG`/`ALT_OBJEKT` (über `select_alt_risiken.sql`) und `FAKTOREN` (über `AltBestandDao`). **Nicht** enthalten: `ALT_KUNDE` |
| 12 | Ohne DDL (Fall 1) | „Soll Read – 36 Zeilen (R1–R36)“ aus 8 Datenstrukturen im Code, Schlüssel `Klasse.Feld`, bei `NeuRisikoEntity` und `Adresse` mit Spaltennamen aus `@Column`. Nicht enthalten: `Konstanten` (nur statische Felder) und `Zahlweise` (Enum); keine Nebentabellen |
| 13 | DDL-Pfad, der nicht existiert | Abbruch mit „DDL nicht gefunden: …“ |
| 14 | Migrationsprojekt „TESTZIEL“ (`konfig/4-mit-projekt.json` oder `project=testziel`) | TESTZIEL nennt `referenz: "test"`, daher wird `testprojekt/` (root von TEST) durchsucht; 20 Treffer, DDL wie in Fall 11. In der Aufgabe stehen „Zielprojekt: TESTZIEL …“ und „Referenzprojekt: TEST … · `…	estprojekt`“. In der Oberfläche sind Referenzprojekt und DDL nach Auswahl sofort gefüllt |
| 15 | `project=vgb` (referenz noch leer) | Abbruch „Fehlende Angaben: Referenzprojekt (Code)“; in der Oberfläche der Hinweis „Referenzprojekt nicht konfiguriert“ |
| 16 | Detailgrad „Kompakt“ (`detail=compact`) statt „Ausführlich“ | Aufgabe: Detailgrad „kompakt – Regeln in einem Satz je Zelle“, Zusammenfassung „höchstens 5 Sätze“; bei „ausführlich“ (Standard) „Regeln als einzelne Schritte in den Zellen“. Die Tabellen sind in beiden Fällen dieselben |
| 17 | Mit DDL (wie Fall 11) | Aufgabe: Abschnitt „Soll-Listen“ mit „Soll Read – 8 Zeilen (R1–R8)“, `R2 \| ALT_RISIKO.RISART`, „Nebentabellen (kein Soll)“ mit `ALT_OBJEKT.WOHNFLAECHE`, „Soll Process – 11 Zeilen (P1–P11)“ und dem Satz „Für Write gibt es keine Soll-Liste …“. Code-Datei ohne „Attributverzeichnis“; kein `{{` in Aufgabe und Skill. Protokoll „Soll Read: 8 Zeilen aus 1 Tabelle · Soll Process: 11 Zeilen“ |
| 18 | Mit DDL, `tables=alt_risiko, ALTB.ALT_OBJEKT` | Zwei Kerntabellen: „Soll Read – 13 Zeilen (R1–R13)“, Unterabschnitte „ALTB.ALT_RISIKO – 8 Zeilen (R1–R8)“ und „ALTB.ALT_OBJEKT – 5 Zeilen (R9–R13)“ (ohne Primärschlüssel, „Eindeutiger Index: VNR“, R9 = UK); `ALT_VERTRAG` und `FAKTOREN` sind Nebentabellen. Ohne DDL meldet das Protokoll, dass die Einstellung nur mit DDL wirkt |
| 19 | Mit DDL, `tables=GIBT_ES_NICHT` | Abbruch; die Meldung nennt den Namen und die vorhandenen Tabellen |
| 20 | Mit DDL, `tables=ALT_RISIKO,ALT_OBJEKT` | Die Spalte `VNR` beider Tabellen ergibt zwei Schlüssel `ALT_RISIKO.VNR` (R1) und `ALT_OBJEKT.VNR` (R9), je eine Zeile |
| 21 | Mit DDL, `phases=READ` bzw. `phases=READ,WRITE` | Skill nur mit „Phase Read“ bzw. „Phase Read“ und „Phase Write“; Aufgabe ohne „Soll Process“ |
| 22 | Mit DDL, `phases=PROCESS`, Zielmodell-Ordner ohne Datei zur Entität | WARNUNG „Keine Zielmodell-Datei …“; „Soll Process – 8 Zeilen (P1–P8)“ mit den Quellattributen (`P2 \| ALT_RISIKO.RISART`) und dem Hinweis, dass das fehlende Zielmodell ein OPL-Punkt ist |
| 23 | DDL ohne Tabelle, deren Name einen Suchbegriff enthält | Alle im Code erwähnten Tabellen sind Kerntabellen; WARNUNG „Keine Tabelle enthält einen Suchbegriff – Tabellen der Entität angeben.“ |
| 24 | Standard, Protokoll | „Prompt: Bearbeite die Aufgabe in aufgabe-risiko.md nach den Regeln in skill-phasen-spezifikation.md. …“ und „Prompt bei UNVOLLSTÄNDIG: …“ mit `spezifikation-risiko-teil2.md`; je höchstens 1.500 Zeichen |
| 25 | Kommentare ignorieren (eigenes Projekt im Test) | Datei mit dem Begriff nur in `//`-, `/*`-, `*`- bzw. (SQL) `--`-Zeilen ist kein Treffer; Datei mit dem Begriff im Namen bleibt Treffer. Mit `ignoreComments=false` sind alle Treffer |
| 26 | `excludeFiles=*Export*` bzw. `excludeFiles=**/process/**` | `RisikoExportConfig` fehlt (1 Datei ausgeschlossen) bzw. die drei Process-Treffer unter `risiko/process/` und die Referenz `BeitragsConverter` fehlen (4 Dateien ausgeschlossen) |
| 27 | Kleines Copilot-Limit (eigenes Projekt im Test) | Weggelassen wird in dieser Reihenfolge: Referenz ohne Rolle (`FrameworkHelfer`), Konfigurations-Referenz, Modell-Referenz der Ebene 2, Konfigurations-Treffer, Modell-Treffer – nur so weit wie nötig. Geschützt bleiben Read-Treffer, Datenzugriff und Modell-Referenz der Ebene 1. Code-Datei mit Abschnitt „Nicht enthaltene Dateien“ vor dem Inhaltsverzeichnis; reicht es nicht, WARNUNG |
| 28 | Größenbericht | Je Datei eine Zeile „Größe …: … Zeichen, … Zeilen, ca. … Tokens (geschätzt: Zeichen / 4)“, „Größe gesamt“ und die fünf größten Quelldateien. Bei mehr als 60 Soll-Zeilen „Empfehlung: … einen Lauf je Phase …“ bzw. bei einer Phase mit mehreren Kerntabellen „… einen Lauf je Tabelle …“ |
| 29 | Jobs `spec-read`, `spec-process`, `spec-write` (Standardwerte, Zielmodell nur bei Process; Test `PhaseJobsTest`) | Je Job genau drei Dateien (Skill, `aufgabe-risiko.md`, `code-risiko.md`). Der Skill enthält nur den Abschnitt seiner Phase, die Aufgabe nur die Soll-Liste dieser Phase, der Ordner des Laufs den Namen der Phase (`…-spezifikation-read-risiko`). Kein Feld „Betrachtete Phasen“; „Zielmodell“ nur bei Process. Keine Empfehlung „einen Lauf je Phase“ im Protokoll |
| 30 | `phase-spec` über die Kommandozeile; Oberfläche | `JobRegistry.find("phase-spec")` findet den Job, `JobRegistry.all()` (Oberfläche, `hilfe`-Liste der Jobs) enthält die drei Phasen-Jobs, nicht `phase-spec`; Ordnernamen von `phase-spec` unverändert |
| 31 | `spec-check` mit „Weitere Ordner (andere Phasen)“ (Test `t17`) | Zwei Läufe (Read, Process) mit gleichen Dateinamen in zwei Ordnern: `pruefbericht-risiko.md` und `spezifikation-risiko-gesamt.md` im Ordner des Laufs, Read- und Process-Abschnitt, OPL neu nummeriert. Fehlender weiterer Ordner: Meldung „Weiterer Ordner nicht gefunden“ |

## Testfälle Implementierung

Job „Implementierung nach Spezifikation“, Migrationsprojekt **TESTZIEL**, Spezifikation =
`testprojekt-ziel/spezifikation/risiko-spezifikation.pdf`, Entität = `Risiko` (`konfig/5-implementierung.json`).
Die Standards liegen in `migrationsprojekte/testziel/standards/` – erzeugt aus `testprojekt-ziel/` mit der
Beispiel-Entität **Vertrag** (vollständig umgesetzt; **Objekt** nur teilweise; `DatumUtil` ist keine Vorlage).

| Nr | Einstellung | Erwartung |
|---|---|---|
| I0 | Projekt-Editor TESTZIEL → „Standards erzeugen…“ → Quelle „Code dieser Migration“, Beispiel „Vertrag – 8 Artefakte …“ | Rückfrage „Es gibt bereits Standards … Ersetzen?“; danach `standards/` mit `STANDARDS.md` (8 Zeilen), `vorlagen/{{Entitaet}}TO.java` … und `basis/AbstractTO.java`, `AbstractPO.java`, `Anreicherer.java`; die bisherigen als `standards-<Zeitpunkt>/`. In den Vorlagen: `{{Entitaet}}TO`, Package `de.vers.neu.{{entitaet}}.to`, `ALT_{{ENTITAET}}`; „Vertragsbeginn“ bleibt. Projektzeile „Standards: 8 Vorlagen (Vertrag)“. Danach die Sicherung löschen |
| I1 | Alle Phasen | Protokoll „Standards: 8 von 8 Vorlagen für Read, Process, Write (Beispiel-Entität Vertrag)“. Artefakttabelle mit `RisikoTO` in `de.vers.neu.risiko.to` … `RisikoWriter`; Basisklasse z. B. `Anreicherer<RisikoTO>`; Abschnitt „Standards“ mit den Platzhalter-Werten, 8 Vorlagen und 3 Basisklassen. Ausgabe: `skill-implementierung.md`, `aufgabe-implementierung-risiko.md`, `spezifikation-risiko.pdf` |
| I2 | Nur Phase Write | „Standards: 1 von 8 Vorlagen für Write“ (`RisikoWriter`) |
| I3 | `STANDARDS.md` bearbeiten: Zeile 5 löschen, bei Zeile 1 einen Hinweis eintragen; dann nur Read | 5 Vorlagen; die Artefakttabelle zeigt den Hinweis in der Spalte „Hinweise“ (danach Änderung zurücknehmen) |
| I4 | Standards-Ordner ohne `STANDARDS.md` | Abbruch „Keine Standards in … – im Projekt-Editor über „Standards erzeugen…“ anlegen.“ |
| I5 | Spezifikation ist keine PDF (z. B. eine .md) | Abbruch „Spezifikation ist keine PDF-Datei“ |
| I6 | Abschnitt „Abgabe“ der Aufgabe | ZIP `implementierung-risiko.zip` mit `src/main/java/…`, `src/main/resources/…` und `IMPLEMENTIERUNG.md` (Umsetzung je Attribut, Artefakte, OPL) |
| I7 | Alle Phasen mit Zielmodell (TESTZIEL) | „Zielattribute: 11 aus Risiko.json“; Abschnitt „Zielattribute (Zielmodell)“; „Umsetzung je Attribut“ je Zielattribut |
| I8 | Nur Read | kein Abschnitt „Zielattribute“; Process ohne Zielmodell bricht ab („… Zielmodell … nötig“) |
| I9 | Neues Projekt mit „Code dieser Migration“ anlegen | Nach dem Speichern Frage „Standards … jetzt … erzeugen?“ |
| I10 | „Standards erzeugen…“ → Quelle „Anderes Verzeichnis wählen…“ → z. B. `testprojekt` | Ordnerauswahl; Kandidaten aus dem gewählten Code (z. B. „Risiko – …“); `STANDARDS.md` nennt den gewählten Ordner als Quelle. Weitere Projekte mit `root` erscheinen als „Anderes Migrationsprojekt – …“ |
| I11 | Pfad-Spalte | `STANDARDS.md` hat die Spalte „Pfad“ (z. B. `src/main/java/de/vers/neu/{{entitaet}}/to/{{Entitaet}}TO.java`); die Artefakttabelle der Aufgabe zeigt „Datei (Pfad im Zielprojekt)“ = `src/main/java/de/vers/neu/risiko/to/RisikoTO.java`; die Abgabe verlangt ein ZIP mit genau diesen Pfaden. Bei mehrmoduligen Quellen bleibt der Modulordner erhalten (`migration-read/src/main/java/…`, `migration-write/…`). Standards ohne Pfad-Spalte: Pfad wird aus dem Package als `src/main/java/…` abgeleitet |

## Testfälle Schema-Import (Oberfläche)

| Nr | Schritt | Erwartung |
|---|---|---|
| S1 | Migrationsprojekt VGB wählen | Projektzeile „Sachversicherung · **kein Schema** …“, Button „Schema importieren…“ aktiv; bei „– kein Migrationsprojekt –“ inaktiv |
| S2 | „Schema importieren…“ → `testprojekt/ddl/altbestand.ddl` | Protokoll „Schema importiert: altbestand.ddl → …\migrationsprojekte\vgb\ddl\altbestand.ddl (5 Tabellen, 23 Spalten: ALT_VERTRAG, …)“; Projektzeile „Schema: 5 Tabellen, 23 Spalten“; Feld „DDL der Altdatenbank“ zeigt auf `migrationsprojekte\vgb\ddl` |
| S3 | Dieselbe Datei erneut importieren | Rückfrage „… bereits vorhanden. Ersetzen?“ |
| S4 | Eine Datei ohne `CREATE TABLE` (z. B. `README.md` umbenannt in `.sql`) | Rückfrage „enthält kein CREATE TABLE. Trotzdem übernehmen?“ |

Nach dem Test `migrationsprojekte/vgb/ddl/altbestand.ddl` wieder löschen.

## Testfälle Projekt-Editor (Oberfläche)

| Nr | Schritt | Erwartung |
|---|---|---|
| P1 | TESTZIEL wählen → „Bearbeiten…“ → „Speichern“ ohne Änderung | `projekt.json` unverändert, insbesondere relative Pfade wie `../../testprojekt-ziel` |
| P2 | „Neu…“ → Id `vhv2`, Kurzname `VHV2`, Referenzprojekt VGB → Speichern | Ordner `migrationsprojekte/vhv2/` mit `projekt.json` (`"referenz": "vgb"`, `"ddl": "ddl"`) und `ddl/`; VHV2 ist ausgewählt, die Projektzeile zeigt „Referenz: VGB – Wohngebäude …“ |
| P3 | „Neu…“ mit bereits vorhandener Id | Meldung „Ein Migrationsprojekt mit der Id … gibt es schon.“ |
| P4 | Bearbeiten: Referenzprojekt auf „– keins –“, Pfad angeben → Speichern | `"referenz"` enthält den Pfad; Feld „Referenzprojekt (Code)“ der Spezifikation übernimmt ihn |
| P5 | VHV2 wählen → „Bearbeiten…“ → „Löschen…“ | Rückfrage nennt Ordner und Anzahl Dateien; nach „Ja“ ist `migrationsprojekte/vhv2/` weg, Auswahl „– kein Migrationsprojekt –“, Protokoll „Migrationsprojekt gelöscht: …“ |
| P6 | Projekt löschen, das von einem anderen als Referenz genannt wird (an einer Kopie testen) | Rückfrage enthält „Achtung: Als Referenzprojekt verwendet von …“; danach zeigt das andere Projekt „Referenzprojekt „…“ nicht gefunden“ |
| P7 | „Neu…“ | Dialog ohne „Löschen…“ |

P5 räumt das in P2 angelegte Projekt wieder auf.
