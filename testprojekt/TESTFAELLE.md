# Testprojekt für die DevToolbox

Eine kleine, nicht baubare Spring-Batch-Migration „Wohngebäude alt (WG2010)“ zum Thema **Risiko**.
Sie ist nur zum Durchsuchen gedacht.

Für alle Fälle gilt: **Projektverzeichnis** = dieser Ordner, **Fachbegriff(e)** = `Risiko, Risk`.
Fertige Konfigurationen liegen in `konfig/`. In der Oberfläche lädst du sie mit „Konfiguration laden…“.
Ohne Oberfläche: `java -jar target/devtoolbox.jar testprojekt/konfig/<datei>.json`.

## Aufbau

| Phase | Treffer (enthalten „Risiko/Risk“) | Referenzen (ohne Begriff, nur über Referenzen) |
|---|---|---|
| Read | `AltRisikoItemReader`, `AltRisikoRowMapper`, `select_alt_risiken.sql` | – |
| Process | `RisikoItemProcessor`, `RisikoartMapper` (CRLF), `OberrisikoZuordnung` (Windows-1252) | `BeitragsConverter` |
| Write | `RisikoItemWriter`, `insert_risiko.sql` (BOM + CRLF) | `FehlerProtokollWriter` |
| Datenzugriff | `NeuRisikoRepository` | `AltBestandDao` (Nachlesen der Versicherungssumme) |
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
- `src/test/...RisikoItemProcessorTest` – nur mit „Tests einbeziehen“.
- `target/.../RisikoGeneriert` – liegt im Build-Ordner und darf **nie** erscheinen.
- `RisikoItemProcessor` – enthält ```` ``` ```` im Javadoc; der Markdown-Block muss trotzdem bis zum Dateiende reichen.
- `RisikoMigrationJobConfig` – Lizenzkopf, der im Kompaktmodus entfällt.
- Fachliche Regeln, die die KI finden sollte: Filter auf ruhende Verträge (Status R), +10 % Versicherungssumme im
  Premium-Tarif `T-PREM` (produktspezifisch), Ratenzuschläge 3 %/5 %, Nachlesen der Versicherungssumme über die
  Wohnfläche, Risikoart-Codes FE/LW/ST/EL/GL, Oberrisiko G/H mit Default GEBAEUDE, doppelte IDs → MIG_FEHLER.

## Testfälle

Job ist immer „Migrationsspezifikation je Phase (Fachbegriff)“.

| Nr | Einstellung | Erwartung |
|---|---|---|
| 1 | Standard (`konfig/1-standard.json`) | 18 Treffer, 28 Dateien: Read 3, Process 4, Write 4, Datenzugriff 2, Konfiguration 4, Modell 11. Ergebnis: `skill-risiko.md` + `code-risiko.md` |
| 2 | Umfang „Nur Klassen mit dem Begriff“ | 18 Dateien, keine Zeile „Referenz von …“, also auch kein `DeckungTO` |
| 3 | Phasen nur „Process“ (`konfig/3-nur-process.json`) | 20 Dateien (Process 4, Datenzugriff 1, Konfiguration 4, Modell 11), 6 Treffer weggelassen. Der Skill enthält nur „Phase Process“ und keine „Durchgängigkeit“ |
| 4 | „Package/Import ignorieren“ aus | 20 Treffer: `Pruefziffer` kommt dazu, `AltBestandDao` wird vom Referenz- zum Treffer |
| 5 | „Tests einbeziehen“ an | 19 Treffer, `RisikoItemProcessorTest` erscheint in der Gruppe „Tests“ |
| 6 | „Kompakt“ an | Keine `import`-Zeilen, kein Lizenzkopf bei `RisikoMigrationJobConfig` |
| 7 | Max. Tokens/Datei = 3000 (`konfig/7-aufgeteilt.json`) | `code-risiko-teil1..5.md`. Jeder Teil hat das volle Inhaltsverzeichnis mit Spalte „Teil“. Im Protokoll steht die Warnung „mehr als 3 Dateien“ |
| 8 | Nach Fall 7 noch einmal Fall 1 | Die alten `code-risiko-teil*.md` sind gelöscht, nur noch `code-risiko.md` ist vorhanden |
| 9 | In jeder Ausgabe prüfen | Kein `kunde`, kein `RisikoGeneriert`, kein `BetragFormatierer`. „Gebäude“ in `OberrisikoZuordnung` ist korrekt dargestellt, kein BOM-Zeichen vor `-- Datei mit BOM` |
