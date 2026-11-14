# DevToolbox

Eine kleine Java-25-Anwendung mit Jobs, die **Aufgaben** für MS Copilot erzeugen. Zur Bearbeitung einer Aufgabe
braucht Copilot einen **Skill** (die allgemeine Methode). Copilot nimmt nur 3 Dateien pro Prompt an, deshalb liefert
ein Job genau drei Dateien: Skill + Aufgabe + Inhalt.

> **Älterer Klon, Konflikte beim `git pull`?** Die Historie wurde am 14.11.2026 neu geschrieben – Umstellung siehe
> [ANLEITUNG-HISTORIE-UMSTELLEN.md](ANLEITUNG-HISTORIE-UMSTELLEN.md).

## Jobs

### Spezifikation aus Referenzprojekt: `phase-spec`

Für die Spezifikation gibt es in der Oberfläche **einen Job**; die Phasen (Read, Process, Write) werden im Feld
„Betrachtete Phasen“ gewählt. Skill, Aufgabe (nur die Soll-Listen der gewählten Phasen) und Code-Datei enthalten nur
diese Phasen. Die Jobs mit fester Phase (`spec-read`, `spec-process`, `spec-write`) gibt es nur noch auf der
Kommandozeile und in JSON-Dateien; ihr Ordner nennt die Phase, z. B. `testziel-spezifikation-read-risiko`.

Das Feld **Variante (Versuch)** bestimmt, wie die Logik in die Tiefe beschrieben wird:

| Variante | Ergebnis | Ergebnisdatei |
|---|---|---|
| A – Tabellen | Je Phase zwei Tabellen, die Logik steht in den Zellen | `spezifikation-<entität>.md` |
| D – Logikprotokoll | Zusätzlich die Liste „Soll Logik“ in der Aufgabe und im Ergebnis ein Abschnitt je Methode, vor den Tabellen; eine Antwort als Datei | `…-d.md` |
| E – in Etappen (Standard) | Wie D, aber ein Prompt je Methodengruppe und ein Abschluss-Prompt für die Tabellen | `…-e-logik-<n>.md`, `…-e.md` |
| F – im Chat | Wie D, aber Copilot schreibt in den Chat statt in eine Datei; Fortsetzung mit „WEITER ab <Nr>“ | – |

Die **Soll Logik** zählt die Toolbox selbst aus der Code-Datei aus (`LogicInventory`): jede Methode, die verzweigt,
rechnet oder einen Fehler wirft, mit der Zahl ihrer Verzweigungen (`if`, `case`, `default`, `? :`, Stream-Filter) und
Fehlerfälle (`throw`) und den Werten, die in ihr stehen; dazu die Konstanten und Enum-Werte der Klassen. Damit ist
die Tiefe des Ergebnisses genauso zählbar wie seine Breite über die Soll-Listen der Attribute. Der Auszähler ist ein
leichter Scanner, kein Parser; eine verzählte Methode kostet eine Zeile der Checkliste. Die Größe einer Etappe steht
in `PhaseSpecJob.STAGE_WEIGHT` (Startwert 30 Verzweigungen und Fehlerfälle, zu kalibrieren). Die Buchstaben B und C
waren Versuche der ersten Runde (Regeldetails, Abdeckungsnachweis) und sind entfallen.

Die Beschreibung unten gilt für `phase-spec` und die Jobs mit fester Phase.

Durchsucht das **Referenzprojekt** – eine bereits umgesetzte, sehr ähnliche Spring-Batch-Migration eines anderen
Produkts – nach einer fachlichen Entität wie `Risiko` (mit Synonymen als Suchbegriffe, z. B. `Risiko, Risk`)
und erzeugt daraus die Vorlage einer Spezifikation für das **Zielprojekt**:

- `skill-phasen-spezifikation.md` – der **Skill**: Methode, Arbeitsregeln und Gliederung für jede Phase. Er hängt
  nicht von der Entität ab und ist für alle Aufgaben dieses Typs gleich.
- `aufgabe-<entität>.md` – die **Aufgabe**: Entität, Suchbegriffe, zu betrachtende Phasen, Zusatzkontext,
  zugehörige Code-Datei, die nummerierten **Soll-Listen** und die erwartete Gliederung des Ergebnisses. Die Soll-Listen
  erzeugt das Tool: „Soll Read“ (eine Zeile je Quellattribut der Kerntabellen aus der DDL bzw. den Datenstrukturen,
  Schlüssel `Tabelle.Spalte`), „Soll Process“ (eine Zeile je Zielattribut aus dem Zielmodell) und die Nebentabellen
  ohne Soll. Copilot liefert je Nr genau eine Tabellenzeile; so lässt sich die Vollständigkeit später lokal messen.
- `code-<entität>.md` – der gesamte zugehörige Code mit klaren Dateigrenzen (`===== BEGIN FILE … =====` /
  `===== END FILE … =====`), gruppiert nach Read → Process → Write → Datenzugriff → Konfiguration → Modell. Er enthält
  **kein Attributverzeichnis** mehr. Klassen, die die Entität nur am Rand erwähnen (Fassaden, Sammel-Services,
  Sammel-TOs – der Dateiname enthält keinen Suchbegriff), kommen als **Auszug**: nur die Member mit Suchbegriff und die
  von ihnen direkt verwendeten Member derselben Klasse; Auslassungen sind im Code markiert. Als Referenz zählt nur, was
  der Auszug verwendet – so zieht eine Sammelklasse nicht alle Entities des Projekts nach. Überschreitet die Datei ihr
  Budget (80 % des Copilot-Limits), lässt das Tool nicht geschützte Dateien weg, die unwichtigsten zuerst: Referenzen
  ohne klare Rolle, tiefe Modell-Referenzen, dann Klassen, die die Entität nur erwähnen, und erst zuletzt die nach der
  Entität benannten Dateien. Fällt eine Datei weg, entfallen die nur von ihr verwendeten Referenzen mit. Was fehlt,
  steht im Abschnitt „Nicht enthaltene Dateien“ (Datei, Kategorie, Grund) vor dem Inhaltsverzeichnis sowie im
  Protokoll. Der Code ist nur Belegmaterial.

Das Ergebnis ist bewusst **nicht fragmentiert und ohne Redundanz**: je Phase **eine** Attributtabelle (eine Zeile je
Attribut; Quelle, Regel, Prüfbedarf, OPL-Bezug und Beleg als Spalten; verschachtelte Strukturen flach als
`vertrag.tarif`) plus die Satzregeln (Selektion, Filter, Sortierung …), dazu eine Zusammenfassung, eine kurze
Ablaufübersicht und die **Offene-Punkte-Liste (OPL)**, in der alle Fragen konsolidiert sind. Jede Regel ist als
[PRÜFEN], [TECHNISCH] oder [1:1] gekennzeichnet und verweist in der Spalte „OPL“ auf zugehörige offene Punkte.
Primärquelle der Attribute ist die **Struktur der Altdaten (DDL)** (die DDL der Altdatenbank) (DB2 for z/OS: `CREATE TABLE`, `CREATE VIEW`, `ALTER TABLE … ADD`,
`LABEL ON`, `COMMENT ON`). **Kerntabellen** sind die Tabellen, die im Parameter „Tabellen der Entität (DDL)“ genannt
werden; ohne Angabe die Tabellen, deren Name einen Suchbegriff enthält (gibt es keine, alle im Code erwähnten, mit
Warnung im Protokoll). Nur sie erhalten ein Soll. Die übrigen im Code erwähnten Tabellen (SQL, `@Table`, Konstanten)
stehen als **Nebentabellen** ohne Soll in der Aufgabe. Ohne DDL kommen die Attribute aus den Datenstrukturen im
Code (TOs, POs, DTOs, Entities inkl. `@Column`).

**Prompts** (stehen mit den echten Dateinamen im Protokoll, je höchstens ca. 1.500 Zeichen):

- **Startprompt** („Prompt: …“): „Bearbeite die Aufgabe in aufgabe-<entität>.md nach den Regeln in
  skill-phasen-spezifikation.md. code-<entität>.md ist nur Belegmaterial. Arbeite die Soll-Listen der Aufgabe Zeile
  für Zeile ab …“ Er verlangt je Nr genau eine Zeile, einen Beleg `Klasse.Methode` aus der Code-Datei, die Ausgabe als
  **Markdown-Datei `spezifikation-<entität>.md` zum Download** und bei Unvollständigkeit „Status: UNVOLLSTÄNDIG“ mit
  der letzten vollständigen Nr.
- **Fortsetzungsprompt** („Prompt bei UNVOLLSTÄNDIG: …“): für denselben Chat, ohne Datei. Copilot liefert nur die
  fehlenden Zeilen, Satzregeln und OPL-Punkte als `spezifikation-<entität>-teil2.md`.

Nach der Abstimmung mit dem Fachbereich wird aus der Spezifikation die PDF für den Implementierungs-Job. Die
Fundstelle einer Regel steht nur in der Spalte „Beleg“, auch beim Detailgrad „Ausführlich“ (dort je Schritt eine
Fundstelle, z. B. `1: AltRisikoRowMapper.mapRow · 2: AltBestandDao.ermittleWohnflaeche`).

Das Protokoll nennt außerdem die Größe jeder Datei und gesamt (Zeichen, Zeilen, geschätzte Tokens), die Sollzahlen je
Phase und die fünf größten Quelldateien. Bei mehr als 60 Soll-Zeilen empfiehlt es bei `phase-spec` mit mehreren Phasen einen Lauf je Phase, bei einem
Einzelphasen-Job einen Lauf je Tabelle; die Ergebnisse führt der Job „Spezifikation prüfen“ zusammen.

Wichtige Einstellungen:

| Einstellung | Wirkung |
|---|---|
| Fachliche Entität | Name zuerst, dann Synonyme als Suchbegriffe, kommagetrennt; Groß-/Kleinschreibung egal |
| Struktur der Altdaten (DDL) | Optional: Datei oder Ordner mit `.ddl`/`.sql`, primäre Quelle der Quellattribute |
| Zielmodell (JSON je Entität) | Pflicht bei Phase Process: Ordner mit einer JSON-Datei je Entität; die Attributtabelle Process hat dann eine Zeile je Zielattribut (aus `attributname`/`attributeName`) |
| Detailgrad der Spezifikation | Gleiche Tabellen, unterschiedlich genaue Zellen: **Kompakt** beschreibt jede Regel in einem Satz, **Ausführlich** (Standard) als nummerierte Schritte mit Bedingung und Klasse.Methode |
| Umfang | Nur Klassen mit dem Begriff oder zusätzlich referenzierte Projektklassen. Datenstrukturen (TO, PO, DTO, Entity …) werden transitiv verfolgt, damit z. B. `RisikoTO → DeckungTO → SelbstbeteiligungTO` vollständig ankommt |
| Tabellen der Entität (DDL) (`tables`) | Optional, kommagetrennt: die Kerntabellen der Entität. Ein unbekannter Name bricht mit der Liste der vorhandenen Tabellen ab. Ohne DDL wirkungslos |
| Betrachtete Phasen | Nur bei `phase-spec` (bei `spec-read`/`-process`/`-write` fest). Read / Process / Write. Wirkt auf den Code (andere Phasen entfallen), auf den Skill (nur die gewählten Phasen) und auf die Soll-Listen |
| Package/Import ignorieren | Begriff nur im Package-Namen (`de.vers.risk`) ist kein Treffer |
| Kommentare ignorieren (`ignoreComments`) | Standard an (Erweitert): Zeilen, die mit `//`, `/*`, `*` (bei `.sql` auch `--`) beginnen, sind keine Treffer. Dateien, die dadurch kein Treffer mehr sind, nennt das Protokoll |
| Dateien ausschließen (`excludeFiles`) | Erweitert, kommagetrennte Muster: ohne `/` gegen den Dateinamen, mit `/` gegen den relativen Pfad (`*`, `?`, `**`). Ausgeschlossene Dateien sind weder Treffer noch Referenz |
| Copilot-Limit (Tokens) | Standard **30.000**, `0` = aus (Erweitert). Schätzt die Größe aller drei Upload-Dateien zusammen (Zeichen / 4), warnt bei Überschreitung mit den noch nicht genutzten Empfehlungen und lässt in der Code-Datei nicht geschützte Dateien weg (Budget 80 %). Es wird bewusst **nicht** auf mehrere Dateien aufgeteilt, damit das 3-Dateien-Limit eingehalten wird. Der Startwert ist eine Annahme und mit `copilot-testdateien` zu kalibrieren |
| Kompakt | Standard **an** (Erweitert): entfernt Imports und Lizenzköpfe (spart Tokens) |

Die Phasen-Zuordnung ist heuristisch (Spring-Batch-Interfaces, Namen, Packages). Feste Regeln mit Vorrang, in
dieser Reihenfolge (`PhaseClassifier`):

1. Eine Klasse, die `ItemProcessor` implementiert, gehört **immer zu Process** – nie zu Read oder Write, egal wie sie
   heißt und in welchem Verzeichnis sie liegt.
2. Klassen in den Verzeichnissen `anreicherer`/`anreicherung` (auch als Teil des Verzeichnisnamens) gehören zu
   Read, in `mapper`/`mapping` zu Process und in `write`/`writer` zu Write (`FIXED_DIRECTORIES`).
3. „Anreicherung“ und „Anreicherer“ sind Schlüsselbegriffe der Phase **Read**: Eine Klasse mit einem der Begriffe im
   Namen gehört zu Read.
4. Klassen `…Repository` gehören **in der Regel** zu Read, aber nicht immer: Ein festes Verzeichnis, ein
   implementiertes Spring-Batch-Interface (`ItemWriter` …) oder das Package (`process`, `export` …) ordnen ein
   Repository einer anderen Phase zu. Nur wenn nichts davon greift, gilt Read.

Alle Namens- und Verzeichniskonventionen (Klassen-Endungen je Phase, Datenstrukturen TO/PO/DTO/Entity, Präfixe
Alt/Neu, feste Regeln) stehen zentral in `Conventions`; sie steuern sowohl die Phasen-Zuordnung als auch die
Erkennung der Artefakte beim Erzeugen der Standards. Eine neue Konvention wird nur dort ergänzt.

Die Texte liegen in `src/main/resources/skills/phase-spec/` (Skill) und `src/main/resources/aufgaben/phase-spec/`
(Aufgabe) und lassen sich ohne Java-Kenntnisse anpassen.

### Referenzprojekt aus Bitbucket

Statt eines lokalen Ordners kann im Feld „Referenzprojekt (Code)“ (und in `referenz` der `projekt.json`) eine
Adresse des internen Bitbucket stehen. Die Toolbox klont das Repository dann flach (`git clone --depth 1`) nach
`~/.devtoolbox/repos/…` und holt bei jedem Lauf den neuesten Stand; der Kopf der Code-Datei nennt Branch und Commit.
Ist Bitbucket nicht erreichbar, wird mit einer Warnung der zuletzt geholte Stand verwendet.

Möglich sind:

- der **Link aus dem Browser**, auch eines Unterordners und Branches, z. B.
  `https://bitbucket.firma.de/bitbucket/projects/SACH/repos/firma-sachvertrag/browse/firma-sachvertrag-migration-batch?at=refs%2Fheads%2Fdevelop`
  – Repository, Ordner und Branch werden daraus übernommen (geklont wird über HTTPS);
- die **Klon-Adresse** (HTTPS oder SSH), optional mit `#<branch>:<unterordner>`, z. B.
  `ssh://git@bitbucket.firma.de:7999/sach/firma-sachvertrag.git#develop:firma-sachvertrag-migration-batch`.

Voraussetzung ist **Git for Windows** im `PATH`. Die Anmeldung übernimmt Git, die Toolbox speichert kein Passwort:

- **HTTPS:** Der Git Credential Manager fragt beim ersten Mal nach und merkt sich die Anmeldung (alternativ ein
  HTTP-Zugriffstoken aus dem Bitbucket-Profil als Passwort). Wird das Firmenzertifikat nicht erkannt:
  `git config --global http.sslBackend schannel`.
- **SSH:** Der öffentliche Schlüssel muss im Bitbucket-Profil hinterlegt sein, ein Schlüssel mit Passphrase im
  ssh-agent (`ssh-add`) – die Toolbox kann keine Passphrase abfragen. Den Host-Schlüssel einmal im Terminal
  bestätigen: `ssh -p 7999 git@bitbucket.firma.de`.

Schlägt der Zugriff fehl, nennt das Protokoll Ursache und Abhilfe.

### Ablauf mit Copilot

**Ein Job je Phase:** Die Schritte 1 bis 5 gelten je Phase; die drei Phasen sind drei getrennte Läufe mit je einem
Chat.

1. Job einer Phase („Spezifikation Read“, „… Process“ oder „… Write“) ausfüllen und starten; optional „Tabellen der
   Entität (DDL)“ angeben.
2. Protokoll lesen: Sollzahlen und Größe der drei Dateien. Bei einer Warnung die genannte Empfehlung umsetzen (Tabellen
   einschränken, Dateien ausschließen) und neu starten.
3. In einem **neuen Chat** die drei Dateien hochladen und den **Startprompt** aus dem Protokoll einfügen.
4. Die Ergebnisdatei herunterladen und in den Ordner des Laufs legen.
5. Meldet Copilot `UNVOLLSTÄNDIG`: im selben Chat den **Fortsetzungsprompt** senden (keine Datei) und die Teildatei
   ebenfalls in den Ordner des Laufs legen.
6. Dasselbe für die beiden anderen Phasen.
7. Job „Spezifikation prüfen“ **je Ordner** starten (Prüfbericht je Phase) oder **zusammengeführt**: Ordner des ersten
   Laufs als „Ordner des Laufs“ und die Ordner der anderen Phasen unter „Weitere Ordner (andere Phasen)“ eintragen.
   Er schreibt den Prüfbericht, bei mehreren Dateien die zusammengeführte Spezifikation (beides in den Ordner des
   Laufs) und, falls etwas fehlt, einen **Nachbesserungsprompt**.

Der Normalfall hat je Phase einen Prompt mit drei Dateien; die Schritte 5 und 7 kommen ohne weiteren Upload aus.

### Spezifikation prüfen (`spec-check`)

Prüft das Ergebnis von Copilot lokal, ohne Copilot und ohne Aufgabe oder Skill zu erzeugen. Eingabe ist der
**Ordner des Laufs** (`run`, Pflicht) mit `aufgabe-*.md`, `code-*.md` und mindestens einer Ergebnisdatei
`spezifikation-*.md`. Fehlt eine davon, bricht der Job mit einer Meldung ab. Eingabedateien werden nie verändert.
Optional gibt „Weitere Ordner (andere Phasen)“ (`moreRuns`, kommagetrennte Ordnerpfade) die Ordner der Läufe der
anderen Phasen derselben Entität an: Aufgaben, Code-Kontexte und Ergebnisse aller Ordner werden gelesen (gleiche
Dateinamen in verschiedenen Ordnern sind erwartet) und zu **einer** Spezifikation zusammengeführt; Prüfbericht und
`-gesamt.md` entstehen im Ordner des Laufs. Ein fehlender weiterer Ordner bricht mit einer Meldung ab. Ohne diese
Angabe ändert sich nichts.

**Zusammenführen:** Liegen mehrere Ergebnisdateien im Ordner (Hauptdatei, `…-teil2.md`, `…-korrektur.md`, auch je
Phase ein Lauf), führt der Job sie zusammen: Zeilen der Attribut- und Feldtabellen über den Schlüssel (bei doppeltem
Schlüssel gilt die spätere Datei, Reihenfolge wie in der Soll-Liste), Satzregeln und Ablaufübersicht aneinandergehängt
und je Phase neu nummeriert, OPL-Punkte neu nummeriert samt angepasster Verweise. Statuszeile und Kennzahlen werden
neu berechnet. Bei nur einer Ergebnisdatei entsteht keine Gesamtdatei; die Prüfung läuft auf dieser Datei.

**Prüfungen** (am zusammengeführten Stand, im Bericht je ein Abschnitt D1–D7):

| Nr | Prüfung |
|---|---|
| D1 | Soll-Schlüssel gegen Schlüssel im Ergebnis, je Phase: fehlend, doppelt, unbekannt |
| D2 | Pflichtzellen (Einlesen/Mapping/Schreiben, Prüfbedarf, Beleg) nicht leer |
| D3 | Prüfbedarf beginnt mit `[PRÜFEN]`, `[TECHNISCH]` oder `[1:1]` |
| D4 | Jeder Beleg `Klasse.Methode` steht im Code-Kontext (Klasse als Dateiblock, Methode in der Klasse); Belege mit Dateinamen: der Dateiblock existiert; andere Formate gelten als „nicht prüfbar“ |
| D5 | OPL-Verweise: Verweis auf einen nicht vorhandenen Punkt (Fehler), Punkt ohne Verweis (Hinweis) |
| D6 | Auslassungen in Zellen (`…`, `...`, „usw.“, „analog“, „wie oben“, „siehe oben“) |
| D7 | Statuszeile vorhanden; `VOLLSTÄNDIG` trotz Befund in D1 ist ein Widerspruch |

**Ausgaben** im Ordner des Laufs: `pruefbericht-<entität>.md` (Kennzahlentabelle je Phase mit Soll, geliefert,
fehlend, doppelt, unbekannt, Hilfswerte, Zeilen ohne gültigen Beleg und OPL-Punkten, dazu die Befunde je Prüfung mit
Nr und Schlüssel) und bei mehreren Ergebnisdateien `spezifikation-<entität>-gesamt.md`. Haben D1, D2 oder D4 Befunde,
gibt das Protokoll den **Nachbesserungsprompt** für denselben Chat aus: Copilot liefert nur die genannten Zeilen neu
als `spezifikation-<entität>-korrektur.md` und schreibt „nicht belegt“ samt OPL-Punkt, wenn er keinen Beleg findet.
Der Prompt ist auf etwa 1.500 Zeichen begrenzt; reicht das nicht, nennt er die ersten Befunde und das Protokoll
weist auf einen weiteren Durchgang hin. **Geprüft sind nur Form und Auffindbarkeit der Belege, nicht die fachliche
Richtigkeit** – das bleibt Sache des Fachbereichs.

### Implementierung nach Spezifikation (je Phase, fachliche Entität)

Der zweite Schritt: Aus der Spezifikation (Ergebnis des ersten Jobs, vom Fachbereich geprüft und geändert, als **PDF**)
soll Copilot die gewählten Phasen (Read, Process, Write) der Entität im **Zielprojekt** implementieren – und zwar
**anhand der Standards des Projekts**.

**Standards** sind ein einmaliger Aufwand über alle Sparten und Projekte und gehören zum Setup des Tools: es gibt **einen
übergreifenden Standard** in `migrationsprojekte/_basisdaten/standards/` (Seitenleiste „Basisdaten“ → „Aus Code
erzeugen…“; ein älterer Ordner `_standards/` wird weiter verwendet, solange `_basisdaten/standards/` fehlt). Ein Projekt kann ihn mit **projekteigenen Standards** überschreiben (`migrationsprojekte/<id>/standards/`,
Projekt-Editor: „Projekteigene Standards erzeugen…“, „Projekteigene bearbeiten…“; „Projekteigene entfernen“ stellt den
übergreifenden wieder her). Es gilt der projekteigene Standard, sofern er eine `STANDARDS.md` enthält, sonst der
übergreifende; die Projektinfo zeigt, welcher. Quelle beim Erzeugen ist der Code der Migrationen/Referenzen aller Projekte
oder ein frei gewähltes Code-Verzeichnis; Beispiel-Entität ist die vollständigste oder eine ausgewählte. Aufbau des Ordners:

```
standards/  (bzw. _basisdaten/standards/)
  STANDARDS.md        Index: Phase, Artefakt, Vorlage, Klassen-/Package-/Pfad-Muster, Oberklasse/Schnittstellen, Hinweise
  vorlagen/*.java     Vorlagen mit Platzhaltern {{Entitaet}} / {{entitaet}} / {{ENTITAET}}
  basis/*.java        Vorlagen je Projekt (einmalig): gemeinsame Klassen und Schnittstellen
```

Auf der Seite „Basisdaten“ lassen sich ganze Klassen ergänzen: „Vorlagen je Entität“ → „Hinzufügen…“ (der Entitätsname in der Klasse
wird durch Platzhalter ersetzt, Phase und Artefakt werden vorbelegt, die Zeile in `STANDARDS.md` ergänzt),
„Vorlagen je Projekt (einmalig)“ → „Hinzufügen…“ (unverändert nach `basis/`) und „Entfernen“; „STANDARDS.md bearbeiten“ und „Ordner öffnen“
für freie Änderungen.

Zeilen in `STANDARDS.md` lassen sich ändern, löschen oder ergänzen, Vorlagen bereinigen, und in „Hinweise“ stehen
Vorgaben für Copilot. Erneutes Erzeugen ersetzt sie nur nach Rückfrage; die bisherigen bleiben als
`standards-<Zeitpunkt>/` erhalten. Zuordnung der Klassen beim Erzeugen:

| Phase | Vorlagen |
|---|---|
| Read | Datenobjekte (TO, PO, Entity, DTO), Repository (soweit es liest – ein Repository einer anderen Phase zählt zu deren Logik) und Einleselogik (Reader, RowMapper, Klassen und Verzeichnisse mit „Anreicherung“/„Anreicherer“ im Namen) |
| Process | Verarbeitungslogik (ItemProcessor, Mapper, Converter, Klassen in `mapper`/`mapping`) |
| Write | Schreiblogik (ItemWriter, Writer, Klassen in `write`/`writer`) |
| Read, Write | Datenzugriff (DAOs) |

Der Job liest die Standards (Feld „Standards (Ordner)“, aus dem Projekt vorbelegt), nimmt die Zeilen der gewählten
Phasen und setzt die Entität in Klassennamen und Packages ein. Ergebnis zum Hochladen:

- `skill-implementierung.md` – der **Skill**: Struktur aus den Vorlagen, Inhalt aus der Spezifikation; vollständiger
  Code, offene Punkte als `// TODO [OFFEN]`.
- `aufgabe-implementierung-<entität>.md` – die **Aufgabe** mit **einer** Tabelle der zu erstellenden Artefakte
  (Phase, neue Klasse und Package – z. B. `{{Entitaet}}TO` → `RisikoTO` –, Vorlage, Oberklasse/Schnittstellen,
  Annotationen, Hinweise), dem Abschnitt **Vorlagen je Projekt (einmalig)** und den **Standards**: Vorlagen und
  Vorlagen je Projekt.
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
   Nummer bekommt das Jira-Projekt der Sparte, lokal eingestellt auf der Seite **Einstellungen**,
   Standard `HAFT` / `SACH`) und **Status** =
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

## Basisdaten (übergreifend)

Die Seite „Basisdaten“ (Seitenleiste) enthält, was einmal für alle Projekte eingerichtet wird, in `migrationsprojekte/_basisdaten/`:
die **Implementierungs-Standards** (`standards/`, siehe oben) und die **Entitäten für alle Projekte**
(`entitaeten.json`, z. B. `{"entitaeten": ["Risiko", "Vertrag"]}`) – ein Namensvorrat. „Aus markierten Ordnern
ermitteln“ liest ihn aus dem Code der angekreuzten Projekte bzw. hinzugefügten Code-Ordner (zusammengeführt,
sortiert, ohne Doppelte) und speichert ihn sofort; von Hand geänderte Namen werden mit „Änderungen speichern“
übernommen. Auf der Projektseite ergänzt „Aus dem Namensvorrat ergänzen…“ die „Zusätzlichen Entitäten“ des Projekts
um ausgewählte Namen.

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
    ddl/            Struktur der Altdaten (DDL) dieses Produkts (*.ddl / *.sql)
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
| `root` | Code dieser Migration: mögliche Quelle der Standards und Referenz, wenn ein anderes Projekt dieses nennt |
| `referenz` | Das Referenzprojekt, dessen Code durchsucht wird: **Id eines anderen Projekts** (dann gilt dessen `root`), ein Pfad oder eine **Bitbucket-Adresse** (siehe „Referenzprojekt aus Bitbucket“) |
| `ddl` | Struktur der Altdaten (DDL) dieses (Ziel-)Produkts, primäre Quelle der Quellattribute (Read) |
| `zielmodell` | Ordner mit den JSON-Dateien des Zielmodells: liefert die Zielattribute für die Phase Process. Entitäten über `entityTypeName`/`entitaet` (auch mehrere je Datei), Attribute über `attributeName`/`attributname`; weitere Angaben je Attribut (`mapping`, `schwebeMapping`, `specification`, `status`) werden mit übergeben. **Leer = aus `root` abgeleitet** (siehe unten). Pflicht, wenn Process gewählt ist |
| `mappingvorlagen` | Ordner mit einer Mapping-Vorlage (Confluence-Tabelle) je Entität – für den Job „Mapping-Dokumentation“. **Leer = aus `root` abgeleitet** |
| `entitaeten` | Nur **zusätzliche** Entitäten dieses Projekts, kommagetrennt (z. B. `Sonderfall, Zusatz`), die in keiner Quelle vorkommen. Die Entitäten selbst werden automatisch ermittelt (Karte „Entitäten“ der Projektseite): aus dem Zielmodell, wenn dessen Ordner JSON-Dateien enthält, sonst aus DDL, Mapping-Vorlagen und Referenzcode; die Auswahlliste „Fachliche Entität“ der Jobs besteht aus der ermittelten Liste plus diesen Zusätzen. Einträge, die schon ermittelt werden, werden nicht gespeichert |
| `sparte` | Leer = aus dem Namen des Parent-Projekts (`…haftpflicht…` / `…sach…`); bestimmt das Jira-Präfix |

### Projektstruktur

Alle Entwicklungsprojekte liegen in einem **Entwicklungsordner**, darin die Projekte der Sparten. Ein
Migrationsprojekt (`root`) ist ein Modul im Projekt seiner Sparte; daneben liegt immer `<parent>-designer-services`
mit Zielmodell und Mapping-Vorlagen.

Entwicklungsordner, Namenspräfix der Sparten-Projekte und die Jira-Projekte sind **lokale Einstellungen** und stehen
bewusst nicht im Repository. Eingetragen werden sie in der Oberfläche auf der Seite **Einstellungen** (Seitenleiste);
gespeichert werden sie in `migrationsprojekte/umgebung.properties`, die Git ignoriert. Dafür ist kein Schreibzugriff
aufs Benutzerprofil nötig. Eine vorhandene `%USERPROFILE%\.devtoolbox\umgebung.properties` wird zusätzlich gelesen;
der Eintrag im Projektordner hat Vorrang. Inhalt der Datei:

```properties
entwicklungsordner=C:/firma-develop
projektpraefix=firma-
jira_haftpflicht=HAFT
jira_sach=SACH
```

Bei leeren Feldern bzw. ohne Datei gelten `C:/develop`, kein Präfix und `HAFT` / `SACH`. Für einen einzelnen Start
gehen auch `-Ddevtoolbox.develop`, `-Ddevtoolbox.prefix`, `-Ddevtoolbox.jira.haftpflicht` und
`-Ddevtoolbox.jira.sach`; sie haben Vorrang. Beispiel mit dem Präfix `firma-`:

```
C:\firma-develop\
  firma-haftpflichtvertrag\                     Haftpflicht (PLG)
    firma-haftpflichtvertrag-plg-migration\     → root (PLG)
    firma-haftpflichtvertrag-designer-services\
      doc\migration\properties\                 Zielmodell: ein JSON je Entität     → zielmodell
      resources\documentation\                  Mapping-Vorlagen (Confluence)       → mappingvorlagen
  firma-sachvertrag\                            Sach – gleicher Aufbau
    firma-sachvertrag-vgb-migration-batch\      → root (VGB)
    firma-sachvertrag-migration\                → root (VHV, allgemein benannt)
```

Daraus leitet die Anwendung alles selbst ab (`ProjectLayout`), wenn in der `projekt.json` nichts eingetragen ist:

| Was | Abgeleitet aus |
|---|---|
| Sparten-Projekt | Sparte: `<präfix>haftpflichtvertrag` bzw. `<präfix>sachvertrag`, fehlt es: das einzige `<präfix>…haftpflicht…` / `<präfix>…sach…` |
| `root` | Modul im Sparten-Projekt mit der Projekt-Id als Namensteil (`…-vgb-…`, `…-plg-…`); hat keines die Id, das allgemeine Modul `<parent>-migration` (VHV), sonst das einzige Modul mit „migration“ im Namen |
| `zielmodell`, `mappingvorlagen` | Modul `*-designer-services` neben `root` (bzw. im Sparten-Projekt) |
| `sparte` | Name des Parent-Projekts von `root` |
| `referenz` als Projekt-Id | abgeleiteter `root` des anderen Projekts |

Alles bleibt **überschreibbar**: Im Projekt-Editor erscheinen die abgeleiteten Werte sofort (nach Sparte/Id bzw.
nach „Code dieser Migration“); ein abweichender Pfad wird gespeichert, ein Pfad gleich dem abgeleiteten nicht (er
folgt dann der Struktur). In den Jobs werden Zielmodell und Mapping-Vorlagen nach Eingabe von „Code des
Migrationsprojekts“ vorbelegt. Die JSON-Datei einer
Entität wird über `entityTypeName` gefunden; andere JSON-Dateien im Ordner (ohne Entität) werden ignoriert.

- **Struktur der Altdaten (DDL):** Die DDL der Alt-Tabellen, aus denen die Entitäten in der Read-Phase gelesen werden
  (DB2 for z/OS, möglichst mit `LABEL ON`/`COMMENT ON`; Schlüssel und eindeutige Indizes optional), wird über **„DDL importieren…“** neben
  der Projektauswahl übernommen: Das Tool prüft die Datei, meldet die erkannten Tabellen und Spalten und legt sie in
  `migrationsprojekte/<id>/ddl/` ab (bei Bedarf wird `ddl` in der `projekt.json` gesetzt). Die Projektzeile zeigt
  den Stand („Altdaten-Struktur: 4 Tabellen, 21 Spalten“). Anforderungen an die Datei: `migrationsprojekte/<id>/ddl/README.md`.
- Pfade dürfen absolut oder relativ zum Projektordner sein.
- Wählst du in der Oberfläche ein Migrationsprojekt, sind Referenzprojekt und DDL in der Job-Konfiguration sofort
  gesetzt; im Einzelfall lassen sie sich überschreiben. Auf der Kommandozeile: `project=plg`.
- **Bearbeiten in der Oberfläche:** die Projektseite hat oben eine Projektauswahl (Wechsel lädt das Projekt, ungespeicherte Änderungen werden erfragt); „Neu…“ legt ein Migrationsprojekt an (Ordner, `projekt.json`, `ddl/`),
  „Bearbeiten…“ ändert die `projekt.json` des gewählten Projekts in einer Maske wie der Job-Konfiguration
  (Referenz als Auswahl der anderen Projekte oder als Pfad). Pfade im Projektordner werden relativ gespeichert,
  bestehende relative Angaben bleiben erhalten, unbekannte Einträge ebenfalls. „Löschen…“ im Bearbeiten-Dialog
  entfernt nach Rückfrage den Projektordner (`projekt.json`, DDL); Code, Referenz und Zielmodell
  außerhalb bleiben erhalten. Projekte, die es als Referenz nennen, zeigen danach „Referenzprojekt … nicht gefunden“.
- **Entitäten und Standards auf der Projektseite:** Die Karte „Entitäten“ zeigt die automatisch ermittelte Liste (eine
  Zeile je Entität mit Herkunft, aus dem Zielmodell auch mit Anzahl der Attribute) und aktualisiert sich beim Ändern der
  Pfade; darunter stehen die „Zusätzlichen Entitäten“. Die Karte „Implementierungs-Standards“ (nur für den Job
  Implementierung) hat einen Hauptknopf – „Erzeugen…“, solange das Projekt keine eigenen Standards hat, sonst
  „Bearbeiten…“ – sowie die Links „Neu erzeugen…“ und „Entfernen“.
- Neues Produkt ohne Codeänderung – über „Neu…“ oder einen Ordner mit `projekt.json`.
- Der Ordner `migrationsprojekte` wird im Arbeitsverzeichnis oder neben der Jar gesucht; ein anderer Ort lässt sich
  mit `-Ddevtoolbox.projekte=<pfad>` angeben.
- **Für VGB, VHV und PLG** ergeben sich `root`, Zielmodell und Mapping-Vorlagen aus dem Entwicklungsordner; einzutragen ist nur noch `referenz`.

## Bauen und starten

Voraussetzungen: JDK 25 und Maven.

```
mvn package
java -jar target/devtoolbox.jar                       # Oberfläche
java -jar target/devtoolbox.jar konfig.json           # ohne Oberfläche, Job steht in der Datei ("job")
java -jar target/devtoolbox.jar phase-spec project=vgb "terms=Risiko,Risk" phases=READ,PROCESS detail=compact
java -jar target/devtoolbox.jar spec-read project=vgb "terms=Risiko,Risk"
java -jar target/devtoolbox.jar hilfe                 # listet alle Jobs und Parameter (auch phase-spec)
```

**Ausgaben der Jobs:** Standardmäßig entstehen Unterordner in `Downloads/devtoolbox/`. Jeder Lauf erhält
einen eigenen Ordner, beispielsweise `ohne-projekt-spezifikation-read-risiko` (`phase-spec`:
`ohne-projekt-spezifikation-risiko`), beim nächsten Lauf `ohne-projekt-spezifikation-read-risiko-2`. Vorherige Ergebnisse werden nicht gelöscht oder überschrieben.
Ein individuell gewählter Ausgabeordner darf nicht innerhalb des Referenz- oder Migrationscodes liegen.

Die Eingaben jedes Jobs werden **je Migrationsprojekt automatisch** gespeichert: beim Ausführen sowie beim Wechsel
von Job oder Projekt in `migrationsprojekte/<id>/konfigurationen/<job-id>.json` (flache JSON-Datei wie eine
Job-Konfiguration, also auch ohne Oberfläche lesbar). Beim Wählen von Projekt und Job werden sie geladen; Werte, die
das Projekt selbst liefert (Pfade aus der `projekt.json`), werden nicht kopiert, sondern folgen dem Projekt – und
füllen Felder, die in der gespeicherten Konfiguration leer sind. Ohne Projekt („kein Projekt“) merkt sich die
Oberfläche wie bisher die letzten Werte je Job.

Auf der Projektseite sichert **„Konfiguration exportieren…“** das Projekt in eine JSON-Datei (`projekt.*` = Inhalt der
`projekt.json` inkl. Entitätenliste, `job.<job-id>.*` = alle Job-Konfigurationen); **„Konfiguration importieren…“**
liest eine solche Datei nach Rückfrage in das gewählte bzw. – auf der Seite „Neu“ – in ein neues Projekt (Ordner aus
der Projekt-ID der Datei) und ersetzt dabei `projekt.json` und die Job-Konfigurationen der Datei.

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
