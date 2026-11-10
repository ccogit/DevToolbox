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
- `inkasso/BeitragsInkassoWriter` – erwähnt „Risk“ nur in einem TODO-Kommentar. Das ist ein **erwarteter Fehltreffer**
  (die Suche ist rein textuell).
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

Job ist immer „Spezifikation aus Referenzprojekt (je Phase, fachliche Entität)“. Das Testprojekt ist dabei das **Referenzprojekt**.

| Nr | Einstellung | Erwartung |
|---|---|---|
| 1 | Standard (`konfig/1-standard.json`) | 21 Treffer, 31 Dateien: Read 5, Process 5, Write 5, Datenzugriff 1, Konfiguration 4, Modell 11. Ergebnis: `skill-phasen-spezifikation.md` + `aufgabe-risiko.md` + `code-risiko.md` |
| 2 | Umfang „Nur Klassen mit dem Begriff“ | 21 Dateien, keine Zeile „Referenz von …“, also auch kein `DeckungTO` |
| 3 | Phasen nur „Process“ (`konfig/3-nur-process.json`) | 20 Dateien (Process 5, Konfiguration 4, Modell 11), 9 Treffer weggelassen. Die Aufgabe nennt nur „Process“ (nicht betrachten: Read, Write); Gliederung: Zusammenfassung, Ablaufübersicht, Phase Process, OPL. Der Skill ist unverändert und enthält alle Phasen |
| 4 | „Package/Import ignorieren“ aus | 23 Treffer: `Pruefziffer` kommt dazu, `AltBestandDao` wird vom Referenz- zum Treffer |
| 5 | „Tests einbeziehen“ an | 22 Treffer, `RisikoItemProcessorTest` erscheint in der Gruppe „Tests“ |
| 6 | „Kompakt“ an | Keine `import`-Zeilen, kein Lizenzkopf bei `RisikoMigrationJobConfig` |
| 7 | Max. Tokens/Datei = 3000 (`konfig/7-aufgeteilt.json`) | `code-risiko-teil1..6.md`. Jeder Teil hat das volle Inhaltsverzeichnis mit Spalte „Teil“. Die Aufgabe listet alle Teile unter „Code-Kontext“. Im Protokoll steht die Warnung „Copilot nimmt nur 3 Dateien“ |
| 8 | Nach Fall 7 noch einmal Fall 1 | Die alten `code-risiko-teil*.md` sind gelöscht, nur noch `code-risiko.md` ist vorhanden |
| 9 | In jeder Ausgabe prüfen | Kein `kunde`, kein `RisikoGeneriert`, kein `BetragFormatierer`. „Gebäude“ in `OberrisikoZuordnung` ist korrekt dargestellt, kein BOM-Zeichen vor `-- Datei mit BOM` |
| 10 | Standard, Inhaltsverzeichnis prüfen | `RisikoDeckungsAnreicherer` steht unter Read, `RisikoSchluesselDao` unter Process, `RisikoExportConfig` unter Write, `NeuRisikoRepository` trotz Verzeichnis `write` unter Read (Repository-Regel hat Vorrang) |
| 11 | DDL der Altdatenbank = `ddl/` (`konfig/2-mit-ddl.json`) | Attributverzeichnis aus der DDL: 4 Tabellen mit 21 Attributen. `ALT_RISIKO` (Name enthält Suchbegriff) mit PK `VNR, RISART`, Fremdschlüssel auf `ALT_VERTRAG`, Bezeichnungen und Kommentaren. `ALT_VERTRAG`/`ALT_OBJEKT` (über `select_alt_risiken.sql`) und `FAKTOREN` (über `AltBestandDao`). `ALT_OBJEKT` hat keinen Primärschlüssel: „Primärschlüssel: – (nicht deklariert)“ und „Eindeutiger Index: VNR“, Spalte „Schlüssel“ = UK (der Index steht in der DDL vor der Tabelle). **Nicht** enthalten: `ALT_KUNDE`. Die nie gelesene Spalte `SB_BETRAG` steht im Verzeichnis – die KI soll sie als „nicht gelesen“ melden |
| 12 | Ohne DDL (Fall 1) | Attributverzeichnis aus dem Code: 36 Attribute aus 8 Datenstrukturen, bei `NeuRisikoEntity` und `Adresse` mit Spaltennamen aus `@Column`. Nicht enthalten: `Konstanten` (nur statische Felder) und `Zahlweise` (Enum) |
| 13 | DDL-Pfad, der nicht existiert | Abbruch mit „DDL nicht gefunden: …“ |
| 14 | Migrationsprojekt „TESTZIEL“ (`konfig/4-mit-projekt.json` oder `project=testziel`) | TESTZIEL nennt `referenz: "test"`, daher wird `testprojekt/` (root von TEST) durchsucht; DDL wie in Fall 11. In der Aufgabe stehen „Zielprojekt: TESTZIEL …“ und „Referenzprojekt: TEST … · `…	estprojekt`“. In der Oberfläche sind Referenzprojekt und DDL nach Auswahl sofort gefüllt |
| 15 | `project=vgb` (referenz noch leer) | Abbruch „Fehlende Angaben: Referenzprojekt (Code)“; in der Oberfläche der Hinweis „Referenzprojekt nicht konfiguriert“ |
| 16 | Detailgrad „Kompakt“ (`detail=compact`) statt „Ausführlich“ | Aufgabe: Detailgrad „kompakt – Regeln in einem Satz je Zelle“, Zusammenfassung „höchstens 5 Sätze“; bei „ausführlich“ (Standard) „Regeln als einzelne Schritte in den Zellen“. Die Tabellen sind in beiden Fällen dieselben |

## Testfälle Implementierung

Job „Implementierung nach Spezifikation“, Migrationsprojekt **TESTZIEL** (Zielprojekt = `testprojekt-ziel/`),
Spezifikation = `testprojekt-ziel/spezifikation/risiko-spezifikation.pdf`, Entität = `Risiko`
(`konfig/5-implementierung.json`). Das Zielprojekt enthält die vollständig umgesetzte Entität **Vertrag**, die nur
teilweise umgesetzte Entität **Objekt** (TO, PO), Basistypen (`AbstractTO`, `AbstractPO`, `Anreicherer`) und eine
Hilfsklasse ohne Phasenbezug (`DatumUtil`).

| Nr | Einstellung | Erwartung |
|---|---|---|
| I1 | Alle Phasen | Beispiel-Entität **Vertrag** mit 8 Vorlagen: `VertragTO`, `VertragPO`, `VertragEntity`, `VertragRepository` (Read), `VertragAnreicherer`, `VertragPoRowMapper` (Read), `VertragMapper` (Process), `VertragWriter` (Write). Die Aufgabe enthält genau **eine** Artefakttabelle (Phase, neue Klasse, Package, Vorlage, Basisklasse/Schnittstellen mit neuer Entität, z. B. `Anreicherer<RisikoTO>`, Annotationen), danach nur Quelltext der Vorlagen und der Basisklassen `AbstractTO`, `AbstractPO`, `Anreicherer` – keine Steckbriefe, keine Konventions- oder Übersichtstabelle. Kein `DatumUtil`. Erwartetes Ergebnis: Code, Umsetzung je Attribut, Artefakte, OPL. Ausgabe: `skill-implementierung.md`, `aufgabe-implementierung-risiko.md`, `spezifikation-risiko.pdf` |
| I2 | Nur Phase Process | Nur Vorlage `VertragMapper` → `RisikoMapper`; Aufgabe „Phasen Process“ |
| I3 | Nur Read, Beispiel-Entität = `Objekt` | Vorlagen `ObjektTO`, `ObjektPO` |
| I4 | Beispiel-Entität = `Kunde` | Abbruch „Beispiel-Entität 'Kunde' nicht gefunden. Vorhanden: Objekt, Vertrag“ |
| I5 | Spezifikation ist keine PDF (z. B. eine .md) | Abbruch „Spezifikation ist keine PDF-Datei“ |
| I6 | Abschnitt „Abgabe“ der Aufgabe | ZIP `implementierung-risiko.zip` mit `src/main/java/…`, `src/main/resources/…` und `IMPLEMENTIERUNG.md` (Umsetzung je Attribut, Artefakte, OPL); Skill: ZIP erzeugen, im Chat nur Übersicht, sonst je Datei ein Codeblock mit Pfad |
| 17 | Aufgabe und Skill prüfen (Standard, nur Read) | Gliederung genau: Zusammenfassung, Ablaufübersicht, Phase Read (Attributtabelle Read + Satzregeln Read), OPL – sonst nichts. Skill: Regeln „Keine Redundanz“, „Eine Attributtabelle je Phase“, „Flache Attributnamen“, „Alle Fragen in die OPL“; Attributtabelle mit Spalten Prüfbedarf, OPL und Beleg |
| 18 | Abschnitt „Abgabe“ der Aufgabe | „Stelle die Spezifikation als Markdown-Datei `spezifikation-risiko.md` zum Download bereit“; Skill: Datei erzeugen, im Chat nur Kurzfassung, sonst ein einziger Codeblock |

## Testfälle Schema-Import (Oberfläche)

| Nr | Schritt | Erwartung |
|---|---|---|
| S1 | Migrationsprojekt VGB wählen | Projektzeile „Sachversicherung · **kein Schema** …“, Button „Schema importieren…“ aktiv; bei „– kein Migrationsprojekt –“ inaktiv |
| S2 | „Schema importieren…“ → `testprojekt/ddl/altbestand.ddl` | Protokoll „Schema importiert: altbestand.ddl → …\migrationsprojekte\vgb\ddl\altbestand.ddl (5 Tabellen, 23 Spalten: ALT_VERTRAG, …)“; Projektzeile „Schema: 5 Tabellen, 23 Spalten“; Feld „DDL der Altdatenbank“ zeigt auf `migrationsprojekte\vgb\ddl` |
| S3 | Dieselbe Datei erneut importieren | Rückfrage „… bereits vorhanden. Ersetzen?“ |
| S4 | Eine Datei ohne `CREATE TABLE` (z. B. `README.md` umbenannt in `.sql`) | Rückfrage „enthält kein CREATE TABLE. Trotzdem übernehmen?“ |

Nach dem Test `migrationsprojekte/vgb/ddl/altbestand.ddl` wieder löschen.
