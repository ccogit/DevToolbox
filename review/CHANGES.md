# CHANGES – Umsetzung des optimierten Copilot-Auftrags

Grundlage: `review/AUFGABE-COPILOT-UMSETZUNG.md`, Stand `main` d986608. Dieses Dokument deckt die Pakete A, B, C
und E ab (inkl. der zugehörigen Tests und `testprojekt/TESTFAELLE.md`). Paket D und die Dokumentation (README,
`copilot-testdateien/LOESUNG.md`, Testprotokoll) wurden parallel bearbeitet; ihre Abschnitte sind unten als
Platzhalter markiert.

Pfade ohne Präfix liegen unter `src/main/java/de/devtoolbox/`. Zeilenangaben beziehen sich auf den Stand nach der
Umsetzung.

## Paket A – Texte, Soll-Listen in der Aufgabe, Prompts

| Nr | Umsetzung | Datei:Zeile |
|---|---|---|
| A1 | Die fünf Vorlagen wörtlich durch Abschnitt 7 ersetzt (per `diff` gegen die Codeblöcke des Auftrags geprüft: identisch). | `src/main/resources/skills/phase-spec/skill.md`, `read.md`, `process.md`, `write.md`, `src/main/resources/aufgaben/phase-spec/aufgabe.md` |
| A2 | Soll-Listen werden vom Tool erzeugt und über `{{INVENTORY}}` in die Aufgabe gesetzt. Neues Ergebnisobjekt `Tool.Output` (Dateien, Text der Soll-Listen, Sollzahlen je Phase, Zahl der Kerntabellen, größte Quelldateien, Vorschläge zum Ausschließen) ohne Instanzfeld und ohne Zusatzdatei; `TaskJob.run` ruft `tool.produce` und reicht es an `renderTask` weiter. Die Code-Datei enthält kein Attributverzeichnis mehr (Kopfhinweis und Abschnitt entfernt); `MarkdownWriter.Meta` unterscheidet die Aufrufer jetzt über `supportAlwaysKept`, der Text für `phase-spec` bleibt gleich. Protokoll: weiterhin „Attributverzeichnis: …“ und neu „Soll Read: n Zeilen aus k Tabellen · Soll Process: m Zeilen“. | `core/Tool.java:29–50`, `core/TaskJob.java:37`, `:83–91`, `tools/codecollector/AttributeIndex.java:261–344` (`inventory`), `CodeCollectorTool.java:108–206`, `MarkdownWriter.java:57–59`, `:98–102`, `jobs/phasespec/PhaseSpecJob.java:174–206` |
| A3 | Kern- und Nebentabellen; neuer Parameter `tables` („Tabellen der Entität (DDL)“, Text, optional, nicht unter „Erweitert“, Hinweistext wie im Auftrag). Unbekannter Name → `IllegalArgumentException` mit Liste der vorhandenen Tabellen. Ohne `tables`: Tabellen mit Suchbegriff im Namen; gibt es keine, alle im Code erwähnten mit WARNUNG „Keine Tabelle enthält einen Suchbegriff – Tabellen der Entität angeben.“ Übrige erwähnte Tabellen sind Nebentabellen. Ohne DDL: Protokollhinweis, keine Wirkung. | `core/Keys.java:18`, `CodeCollectorTool.java:74–75`, `:375–392` (`sources`), `AttributeIndex.java:82–133` (`fromDdl`), `:190–232` |
| A4 | `renderSkill(Config)` setzt nur die Abschnitte der gewählten Phasen ein (Reihenfolge Read, Process, Write); Kommentar in `TaskJob` angepasst. | `core/TaskJob.java:39–43`, `PhaseSpecJob.java:96–107` |
| A5 | Gliederung neu formuliert (Statuszeile, Kennzahlen je Phase, „eine Zeile je Nr der Liste „Soll …““, OPL-Spalte „Betrifft (Nr der Zeile bzw. Satzregel)“, Schlusssätze). | `PhaseSpecJob.java:217–247` |
| A6 | Neue überschreibbare Methode `TaskJob.prompts` (Vorgabe: bisheriger Satz); `PhaseSpecJob` liefert Start- und Fortsetzungsprompt aus 5.3 mit eingesetzten Dateinamen als „Prompt: …“ und „Prompt bei UNVOLLSTÄNDIG: …“. Länge im Testprojekt: 577 bzw. 622 Zeichen. | `core/TaskJob.java:45–51`, `:100`, `PhaseSpecJob.java:109–128` |

## Paket B – Code-Kontext filtern

| Nr | Umsetzung | Datei:Zeile |
|---|---|---|
| B1 | Schalter `ignoreComments` („Kommentare ignorieren“, Vorgabe an, „Erweitert“). Zeilen, die nach führenden Leerzeichen mit `//`, `/*`, `*` oder (nur `.sql`) `--` beginnen, sind keine Trefferzeilen; Dateinamentreffer zählt weiter. Dateien, die dadurch kein Treffer mehr sind, nennt das Protokoll mit Anzahl und den ersten zehn Namen. | `ProjectScanner.java:31–44`, `:76–83`, `:108–135`, `CodeCollectorTool.java:87–88`, `:286–302`, `core/Keys.java:50` |
| B2 | Parameter `excludeFiles` („Dateien ausschließen“, Text, leer, „Erweitert“), kommagetrennte Globs: ohne `/` gegen den Dateinamen, mit `/` gegen den relativen Pfad. Ausgeschlossene Dateien sind weder Treffer noch Referenz (sie fehlen auch im Typindex); das Protokoll nennt die Anzahl. | `ProjectScanner.java:55–59`, `:137–172`, `CodeCollectorTool.java:89–90`, `:294–296`, `core/Keys.java:52` |
| B3 | Vorgabe von `compact` ist `true`. | `CodeCollectorTool.java:91` |
| B4 | Überschreitet die Code-Datei ihr Budget (80 % von `copilotLimit`, Konstante `CODE_SHARE` „Startwert, zu kalibrieren“), werden nicht geschützte Dateien in der festgelegten Reihenfolge weggelassen, bis das Budget passt. `MarkdownWriter.Entry` trägt dafür Referenztiefe (`depth`) und ursprüngliche Kategorie (`origin`). Jede weggelassene Datei steht im Protokoll und in der Code-Datei im Abschnitt „Nicht enthaltene Dateien“ (vor dem Inhaltsverzeichnis, Tabelle `\| Datei \| Kategorie \| Grund \|` mit dem Satz aus dem Auftrag). Reicht es nicht, warnt das Tool zusätzlich zu C2. Nur `phase-spec`; `collect` (mapping-doc) unverändert. | `CodeCollectorTool.java:46–49`, `:158–184`, `:205–245`, `:353`, `MarkdownWriter.java:21–48`, `:110–118` |

## Paket C – Größe messen und warnen

| Nr | Umsetzung | Datei:Zeile |
|---|---|---|
| C1 | `CopilotLimit.report`: je Datei „Größe <Datei>: n Zeichen, n Zeilen, ca. n Tokens (geschätzt: Zeichen / 4)“, dann „Größe gesamt“, dann die fünf größten Quelldateien der Code-Datei. Immer, auch bei `copilotLimit=0`. | `core/CopilotLimit.java:39–63`, `core/TaskJob.java:105` |
| C2 | Vorgabe `30000` (Konstante `DEFAULT_LIMIT`), Hinweistext wie im Auftrag; `0` oder leer schaltet Prüfung und B4 ab. Warnung nennt nur die noch nicht genutzten Empfehlungen in der festgelegten Reihenfolge, „Dateien ausschließen“ mit den fünf größten nicht geschützten Dateien. Kommentar in `Keys` korrigiert. Gilt über `CopilotLimit.param()` auch für `implementation` und `mapping-doc`. | `core/CopilotLimit.java:17–28`, `core/Keys.java:60–61`, `core/TaskJob.java:58–61`, `:106`, `PhaseSpecJob.java:146–172` |
| C3 | Konstante `MAX_TARGET_ROWS = 60` („Startwert, zu kalibrieren“). Über 60 Soll-Zeilen der gewählten Phasen: bei mehreren Phasen „einen Lauf je Phase“, bei einer Phase mit mehreren Kerntabellen „einen Lauf je Tabelle über „Tabellen der Entität““, jeweils mit dem Hinweis auf den Job „Spezifikation prüfen“. Neue Methode `TaskJob.notes`. | `PhaseSpecJob.java:38–40`, `:130–144`, `core/TaskJob.java:53–56`, `:101` |

## Paket E – `implementation` und `mapping-doc`

| Nr | Umsetzung | Datei:Zeile |
|---|---|---|
| E1 | Regel „**Unvollständigkeit melden.** …“ unter „Arbeitsregeln“ ergänzt (Regel 11 bzw. 7). | `src/main/resources/skills/implementation/skill.md:84–85`, `src/main/resources/skills/mapping-doc/skill.md:99–100` |
| E2 | Prompt um „Arbeite jede Zeile der Tabelle in der Aufgabe ab; kannst du nicht alles liefern, melde Status: UNVOLLSTÄNDIG und was fehlt.“ ergänzt. | `jobs/implementation/ImplementationJob.java:148–151`, `jobs/mappingdoc/MappingDocJob.java:93–96` |
| E3 | Größenbericht aus C1; die PDF-Spezifikation wird genannt, aber nicht gezählt. Vorgabe aus C2 gilt automatisch. | `ImplementationJob.java:153`, `MappingDocJob.java:98` |

Sonst: `JobInfo` von `PhaseSpecJob` an die neuen Inhalte angepasst (Abschnitt 10, liegt im Code dieses Pakets):
`PhaseSpecJob.java:66–72`.

## Abgleich Texte – Code (Abschnitt 8), geprüft am erzeugten Ergebnis

- „Soll Read“, „Soll Process“, „Nebentabellen“ heißen in der erzeugten Aufgabe genau so (`### Soll Read – 8 Zeilen
  (R1–R8)`, `### Nebentabellen (kein Soll)`, `### Soll Process – 11 Zeilen (P1–P11)`) wie in Skill („die Liste
  „Soll Read““), Phasenabschnitten und Gliederung („eine Zeile je Nr der Liste „Soll Read““). Alle Soll-Tabellen
  haben die Spalten `Nr` und `Schlüssel` (Read-Schlüssel `Tabelle.Spalte`, Process-Schlüssel = Zielattribut).
- Spaltenköpfe für Paket D: „Attribut“ (`read.md`), „Zielattribut“ (`process.md`), „Zielfeld (Tabelle.Spalte)“
  (`write.md`) stehen wörtlich im erzeugten Skill.
- `{{DETAIL}}` unverändert; `case16_detail` grün.
- Die erzeugte Aufgabe enthält kein „Attributverzeichnis“ mehr (Test `case17_inventoryInTask`), die Code-Datei
  auch nicht.
- Kein `{{` in den drei erzeugten Dateien (geprüft am Lauf unten und in `case17`, `case3`).

## Abweichungen vom Auftrag (mit Grund)

0. **Keine ZIP-Abgabe.** Auftrag Abschnitt 12 verlangt ein ZIP; stattdessen werden die Änderungen auf Wunsch des
   Auftraggebers direkt in `main` committet.
1. **Qualifizierter Schlüssel bei gleichem Tabellennamen in zwei Schemas (A2) nicht umgesetzt.** `DdlReader`
   führt Tabellen über den unqualifizierten Namen zusammen (`DdlReader.key`, damit `COMMENT ON` ohne Schema greift);
   zwei gleichnamige Tabellen kommen also nie getrennt an. Der Schlüssel ist immer `Tabelle.Spalte` ohne Schema. Der
   Code gewinnt; siehe „Offen“.
2. **Ohne Read immer „Quellattribute (kein Soll in diesem Auftrag)“.** Der Auftrag regelt nur „Process ohne Read“.
   Auch bei „nur Write“ steht der Abschnitt da (vorher stand das Attributverzeichnis immer in der Code-Datei; Write
   braucht die Herkunft). Nebentabellen stehen immer, wenn der Quellabschnitt steht (Process nutzt z. B.
   `ALT_VERTRAG.TARIF`).
3. **Nebentabellen** zeigen wie im Beispiel des Auftrags nur „Gefunden“ und `| Spalte | Typ | Bezeichnung | Kommentar |`;
   Primär-/Fremdschlüssel und Unique-Index gibt es nur bei Kerntabellen. Deshalb prüft `case11` die Angaben zu
   `ALT_OBJEKT` (kein PK, Unique Index, UK) jetzt in `case18` mit `tables=…,ALT_OBJEKT`.
4. **Hinweis zum Unique-Index** sagt „Spalte „Schlüsselart“ = UK“ statt „Spalte „Schlüssel““, weil die Spalte im neuen
   Format „Schlüsselart“ heißt und „Schlüssel“ jetzt den Soll-Schlüssel trägt.
5. **Protokollzeile „Attributverzeichnis: …“** um „(Kern: … · Neben: …)“ ergänzt; der bisherige Anfang
   „4 Tabellen, 21 Attribute aus DDL“ bleibt.
6. **„Soll Process“ ohne Zielmodell-Datei**: Spalten `| Nr | Schlüssel | Typ |`, Hinweissatz eigener Wortlaut („Im
   Zielmodell-Ordner `…` gibt es keine JSON-Datei zu dieser Entität. Die Liste nennt deshalb die Quellattribute der
   Kerntabellen; das fehlende Zielmodell ist ein OPL-Punkt.“) – der Auftrag gibt nur den Inhalt vor.
7. **Ohne DDL** (`fromCode`): Unterabschnitte „#### Klasse <Name> – n Zeilen (R…)“ mit dem Pfad, davor der Satz
   „Quelle: Datenstrukturen im Code (…)“; Spalte „Spalte (@Column)“ nur, wenn eine Klasse `@Column` nutzt (wie bisher).
8. **Reihenfolge der Kerntabellen** mit `tables`: wie angegeben; sonst DDL-Reihenfolge (Auftrag schweigt).
9. **Gliederung A5**: Das Beispiel im Schlusssatz nennt die erste gewählte Phase („## Phase Process“ bei nur
   Process) statt immer „## Phase Read“, damit es keine Überschrift nennt, die es im Auftrag nicht gibt.
10. **Kopf der Code-Datei**: Der Satz „… sind immer enthalten“ bleibt gleich; nur wenn B4 Dateien weglässt, folgt
    „, soweit nicht unter „Nicht enthaltene Dateien“ genannt“.
11. **B2-Muster**: eigene Glob-Umsetzung (keine Abhängigkeit): `**` beliebiger Pfad, `**/` auch kein Ordner, `*`
    und `?` innerhalb eines Namens, ohne Beachtung der Groß-/Kleinschreibung; `\` gilt wie `/` als Pfadmuster.
12. **B3 wirkt auch auf `mapping-doc`**, weil `MappingDocTool` den Parameter „Kompakt“ des Code-Sammlers übernimmt
    (`MappingDocTool.java:80–82`). Tests von `mapping-doc` sind davon nicht betroffen. B1/B2 wirken dort nicht (die
    Parameter fehlen in dessen Formular; ohne Wert sind sie aus).
13. **B4**: Kategorie „Test“ (nur mit „Tests einbeziehen“) steht in keiner Stufe und wird daher nie weggelassen
    (Auftrag schweigt). Reicht das Weglassen nicht, schreibt das Tool eine eigene WARNUNG („Die Code-Datei bleibt …
    über ihrem Budget …“), weil C2 nur die Summe aller drei Dateien prüft und eine zu große Code-Datei sonst
    unbemerkt bleiben kann. Der Grund in „Nicht enthaltene Dateien“ nennt Stufe und geschätzte Tokens.
14. **C2**: „Tabellen der Entität angeben“ wird nur empfohlen, wenn eine DDL angegeben ist (ohne DDL wirkungslos,
    A3). Sind alle Empfehlungen genutzt, steht „Alle Empfehlungen sind schon genutzt.“ Die fünf vorgeschlagenen
    Dateien sind die größten nicht geschützten aller gesammelten Dateien (auch bereits weggelassene).
15. **C3**: Bei einer Phase mit nur einer Kerntabelle gibt es keine Empfehlung (Auftrag nennt keine).
16. **Skillgröße**: erzeugt 8.768 Bytes (eine Phase) bzw. 11.571 Bytes (alle), Auftrag nennt 8.787 bzw. 11.588. Der
    Text ist wörtlich übernommen (`diff` leer); der Unterschied stammt aus der Messung des Auftrags.
17. **`ProjectScanner.Settings`** behält einen Konstruktor mit der alten Signatur (B1/B2 aus), damit
    `ProjectScannerTest` unverändert bleibt.
18. **`testprojekt/TESTFAELLE.md`**, Fall 14: Das Zeichen vor `estprojekt` ist schon in d986608 ein Tabulator (statt
    `\t`); unverändert gelassen.

## Geänderte Testerwartungen (mit Grund)

Alle in `src/test/java/de/devtoolbox/PhaseSpecJobTest.java`, sofern nicht anders genannt.

| Test | Alt → neu | Grund |
|---|---|---|
| `case1_standard` | 21 → 20 Treffer, 31 → 30 Dateien, Write 5 → 4; neu: Protokoll nennt `BeitragsInkassoWriter.java` als Kommentar-Datei | B1: Begriff nur im TODO-Kommentar |
| `case2_onlyHits` | 21 → 20 Dateien | B1 |
| `case3_onlyProcess` | 9 → 8 Treffer weggelassen; Skill enthält nur noch „Phase Process“ und unterscheidet sich vom Skill mit allen Phasen (vorher: gleich); neu: „Quellattribute (kein Soll …)“ ohne R-Nummern, „Soll Process – 11 Zeilen“, Beispiel „## Phase Process“ | B1, A4, A2, A5 |
| `case4_packageImportNotIgnored` | 23 → 22 Treffer | B1 |
| `case5_withTests` | 22 → 21 Treffer | B1 |
| `case6_compact` | Vergleichslauf mit `compact=false`, Kompaktlauf mit Vorgabe | B3 |
| `case7b_copilotLimitWarning` | „Kompakt“ in der Empfehlung → Empfehlungsliste C2 (mit/ohne DDL, „alle genutzt“); Fall „leer“ → Vorgabe 30.000 („Copilot-Limit 30.000 – ok“) und `copilotLimit=0` (kein „Copilot-Limit“ im Protokoll) | C2 |
| `case9_contentRules` | `BeitragsInkassoWriter` fehlt jetzt; mit `ignoreComments=false` enthalten | B1 |
| `case11_ddlDirectory` | Prüfung in der Aufgabe statt in der Code-Datei: Kerntabelle `ALT_RISIKO` R1–R8, `R2 \| ALT_RISIKO.RISART`, `R7 \| ALT_RISIKO.SB_BETRAG`, drei Nebentabellen; Code-Datei ohne Verzeichnis. Angaben zu `ALT_OBJEKT` (PK, Unique Index, UK) → `case18` | A2/A3, Abweichung 3 |
| `case12_withoutDdl` | „Attributverzeichnis“ der Code-Datei → „Soll Read – 36 Zeilen (R1–R36)“ in der Aufgabe, 8 Unterabschnitte „Klasse …“, `@Column`-Spalte | A2 |
| `case14_project` | 21 → 20 Treffer | B1 |
| `readOnlyNeedsNoTargetModel` | zusätzlich: Aufgabe mit „Soll Read“, ohne „Soll Process“ und ohne Write-Satz | A2 |
| `ImplementationJobTest.copilotLimit` | zusätzlich: Größenbericht (inkl. „PDF, nicht gezählt“), Prompt mit UNVOLLSTÄNDIG-Satz, Skill-Regel, Vorgabe 30.000 bzw. `0` = aus | C1/C2/E1–E3 |
| `MappingDocJobTest.projectValues` | zusätzlich: Größenbericht je Datei, „Copilot-Limit 30.000 – ok“, Prompt-Satz, Skill-Regel | C1/C2/E1–E3 |

Neue Tests (TESTFAELLE.md Fälle 17–28): `case17_inventoryInTask` (T1), `case18_tablesOfEntity` (T2),
`case19_unknownTable` (T3), `case20_sameColumnInTwoTables` (T4), `case21_skillPerPhases` (T5),
`case22_processWithoutTargetModelFile` (T6), `case23_noTableWithTerm` (A3-Warnung), `case24_prompts` (T11),
`case25_ignoreComments` (T7), `case26_excludeFiles` (T8), `case27_budgetOmission` (T9), `case28_sizeReport` (T10).
Neue Testdaten entstehen nur im `@TempDir`; Quelldateien unter `testprojekt/` sind unverändert.

`testprojekt/TESTFAELLE.md`: Fälle 1–6, 9, 11, 12, 14 an die neuen Werte angepasst; die veralteten Fälle 7 und 8
(Aufteilung in `code-risiko-teil1..6.md`) an `case7_singleFileEvenWithLargeCode` bzw.
`case8_repeatedRunPreservesAllExistingFiles` angeglichen; neu 7b und 17–28; Stolperfalle `BeitragsInkassoWriter`
neu beschrieben.

## Messwerte `phase-spec project=testziel "terms=Risiko"`

| Datei | vorher (3.2; eigener Lauf d986608) | nachher |
|---|---|---|
| `skill-phasen-spezifikation.md` | 211 Zeilen, 13.463 Bytes | 182 Zeilen, 11.571 Bytes (11.316 Zeichen) |
| `aufgabe-risiko.md` | 40 Zeilen, 2.919 Bytes | 131 Zeilen, 6.553 Bytes (6.411 Zeichen) – jetzt mit Soll-Listen |
| `code-risiko.md` | 1.133 Zeilen, 44.014 Bytes, 30 Dateien | 927 Zeilen, 36.270 Bytes (36.154 Zeichen), 30 Dateien: Read 5, Process 5, Write 4, Datenzugriff 1, Konfiguration 4, Modell 11 |
| zusammen | 60.396 Bytes, ca. 15.000 geschätzte Tokens | 54.394 Bytes, ca. 13.470 geschätzte Tokens („Größe gesamt“) |

- Genau drei Dateien, kein `{{`, Soll-Listen nur in `aufgabe-risiko.md` (Soll Read 8 Zeilen aus 1 Tabelle, Soll
  Process 11 Zeilen, 3 Nebentabellen). Protokoll: „Copilot-Limit 30.000 – ok“, keine Datei weggelassen.
- Der Rückgang der Code-Datei kommt aus „Kompakt“ als Vorgabe (B3) und dem entfallenen Attributverzeichnis (A2).
  Mit `terms=Risiko` ist `BeitragsInkassoWriter` schon vorher kein Treffer (er nennt nur „Risk“).
- `phases=READ`: Skill nur „Phase Read“ (8.768 Bytes), Aufgabe 5.420 Bytes, Code 645 Zeilen / 25.110 Bytes (21 Dateien),
  zusammen ca. 9.727 geschätzte Tokens.

## Ergebnis `mvn package` (JDK 25.0.4, `mvn -B -ntp clean package`)

- Vorher (d986608): 134 Tests, 1 Fehlschlag.
- Nachher: **146 Tests, 1 Fehlschlag**, `BUILD SUCCESS` mit `-Dmaven.test.failure.ignore=true`.
- Der eine Fehlschlag ist `ProjectConfigsTest.storedPerProjectAndJob` und ist ein lokales Artefakt dieses
  Arbeitsverzeichnisses: die nicht versionierte Datei `migrationsprojekte/testziel/konfigurationen/implementation.json`
  des Auftraggebers. Er tritt vorher wie nachher auf und hat mit diesen Änderungen nichts zu tun; in einem sauberen
  Checkout ist der Lauf grün.
- Gemessen ohne die parallel entstehenden Teile von Paket D.

## Ergebnis Gesamtbau (Pakete A bis E und Dokumentation)

- `mvn clean package` mit JDK 25: **160 Tests, 0 Fehlschläge, 0 Fehler** – in einem Stand ohne die lokalen,
  nicht versionierten Ordner `migrationsprojekte/testziel/konfigurationen` des Auftraggebers (dessen Datei
  verursacht den einen Fehlschlag in `ProjectConfigsTest`, siehe oben).
- Ende-zu-Ende-Prüfung: `phase-spec` auf `testziel`/Risiko erzeugte drei Dateien ohne `{{`. `spec-check` auf einem
  absichtlich fehlerhaften Ergebnis meldete fehlende, doppelte und unbekannte Schlüssel, eine leere Pflichtzelle,
  einen ungültigen Prüfbedarf, eine unbekannte Klasse und eine unbekannte Methode, einen OPL-Verweis ohne Punkt, die
  Auslassung „wie oben“ und `VOLLSTÄNDIG` trotz Befunden, und gab den Nachbesserungsprompt aus.

## Offen

1. Qualifizierte Schlüssel bei gleichem Tabellennamen in zwei Schemas (Abweichung 1): bräuchte eine Änderung an
   `DdlReader` (Zusammenführung über den unqualifizierten Namen). Am echten Fall prüfen, ob das vorkommt.
2. C3 empfiehlt bei nur Process mit mehreren Kerntabellen „einen Lauf je Tabelle“, obwohl die Process-Sollzahl aus
   dem Zielmodell dadurch nicht sinkt (wörtlich nach Auftrag umgesetzt).
3. Startwerte (30.000 Tokens, 80 %, 60 Soll-Zeilen) sind Annahmen und über das Testprotokoll zu kalibrieren.
4. B1 erkennt Kommentare nur am Zeilenanfang (Auftrag 13.5); XML-Kommentare (`<!--`) und `#` in Properties/YAML
   zählen weiter als Treffer.
5. `Keys.MAX_TOKENS` (`maxTokens`) wird von keinem Parameter mehr gelesen (schon vor dieser Umsetzung);
   `case7_singleFileEvenWithLargeCode` prüft nur, dass der Wert nichts bewirkt.
6. Für Paket D: Die Soll-Tabellen in der Aufgabe haben die Spalten `Nr` und `Schlüssel` als erste Spalten (auch
   „Soll Process“ ohne Zielmodell); „Quellattribute (kein Soll …)“ und „Nebentabellen“ haben keine Spalte `Nr`.

## Paket D – Job „Spezifikation prüfen“ (`spec-check`)

Neuer Job mit der Id `spec-check` („Spezifikation prüfen“). Er erzeugt keinen Copilot-Auftrag und erbt nicht von
`TaskJob`. Einziger Parameter ist `run` („Ordner des Laufs“).

| Teil | Datei |
|---|---|
| Job | `jobs/speccheck/SpecCheckJob.java` |
| Logik | `tools/speccheck/SpecCheckTool.java` (Ablauf, Dateien lesen, Protokoll), `Spec.java` (Ergebnisdatei einlesen), `MdTable.java` (Markdown-Tabellen), `SpecPhase.java`, `TaskLists.java` (Soll-Listen der Aufgabe), `CodeContext.java` (Dateiblöcke des Code-Kontexts), `SpecMerger.java` (Zusammenführen), `SpecChecker.java` (Prüfungen D1–D7), `SpecReport.java` (Prüfbericht, Gesamtdatei, Nachbesserungsprompt) |
| Registrierung | `core/JobRegistry.java` |
| Tests | `SpecCheckJobTest` (T12–T16), `tools/speccheck/MdTableTest`, `tools/speccheck/TaskListsAndCodeContextTest` |

Abweichungen und Entscheidungen von Paket D:

1. **Eine Ergebnisdatei: keine `-gesamt.md`.** Der Auftrag spricht nur vom Zusammenführen mehrerer Dateien. Bei
   genau einer Datei wird keine `spezifikation-<e>-gesamt.md` geschrieben; die Prüfungen laufen auf dieser Datei.
2. **Mehrere `aufgabe-*.md` und `code-*.md` in einem Ordner werden zusammengeführt** (Soll nach Schlüssel, Codeblöcke
   vereinigt). Die Entität stammt aus dem kürzesten Namen `aufgabe-*.md`. Das ist nötig für einen Lauf je Phase
   (Auftrag 5.4, Variante D), weil dort je Phase eine Aufgabe und eine Code-Datei im selben Ordner liegen.
3. **D4: „nicht prüfbar“** (Beleg in einem anderen Format als `Klasse.Methode` oder Dateiname) wird zusätzlich im
   Nachbesserungsprompt unter „Beleg steht nicht im Code-Kontext“ genannt. Der Auftrag nennt für den Prompt nur
   Klasse bzw. Methode, die fehlen.
4. **Zusatzhinweis (kein Fehler):** Fehlt die Tabelle einer gewählten Phase oder ihre Schlüsselspalte, steht in D1 ein
   Hinweis; dann ist kein Schlüsselvergleich möglich.
5. **„OPL-Punkte“ je Phase** in den Kennzahlen = Zahl der verschiedenen OPL-Punkte, auf die diese Phase verweist
   (Spalte „OPL-Punkte (referenziert)“); die Gesamtzahl steht getrennt darunter.
6. **Satzregel-Tabellen ohne Spalte „Nr“** werden beim Zusammenführen nicht neu nummeriert.
7. **Dateien werden als UTF-8 gelesen, mit Rückfall auf windows-1252**, falls die Datei kein gültiges UTF-8 ist
   (Downloads aus Copilot und Editoren unter Windows).

## Dokumentation (README, `copilot-testdateien/LOESUNG.md`, Testprotokoll)

Geändert wurden:

- `README.md`: Abschnitt „Spezifikation aus Referenzprojekt“ – Inhalt der drei Dateien (Soll-Listen in der Aufgabe,
  Code-Datei ohne Attributverzeichnis, „Nicht enthaltene Dateien“, Kern- und Nebentabellen), Startprompt und
  Fortsetzungsprompt, Größenbericht und Empfehlung bei vielen Soll-Zeilen, Beleg-Regel auch für „Ausführlich“;
  Tabelle „Wichtige Einstellungen“ um `tables`, `ignoreComments`, `excludeFiles` ergänzt, neue Vorgaben für
  „Kompakt“ (an) und „Copilot-Limit (Tokens)“ (30.000, 0 = aus); neue Abschnitte „Ablauf mit Copilot“ (Schritte aus
  Auftrag 5.1) und „Spezifikation prüfen (`spec-check`)“ (Eingabe, Zusammenführen, Prüfungen D1–D7, Ausgaben,
  Nachbesserungsprompt, Grenze der Prüfung).
- `copilot-testdateien/LOESUNG.md`: letzter Absatz nennt „Copilot-Limit (Tokens)“ für alle drei Dateien zusammen statt
  „Max. Tokens/Datei“.
- `JobInfo` von `PhaseSpecJob`: bereits mit Paket A an die neuen Inhalte angepasst (siehe oben), keine weitere Änderung.
- `testprojekt/TESTFAELLE.md`: siehe Pakete A bis C.
- `review/COPILOT-TESTPROTOKOLL.md` ist nicht Teil dieser Änderung.
