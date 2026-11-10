# DevToolbox

Eine kleine Java-25-Anwendung mit Jobs, die Inhalte und KI-Anweisungen (Skills) für MS Copilot erzeugen.
Copilot nimmt nur 3 Dateien pro Prompt an. Ein Job liefert deshalb genau einen Skill und den passenden Inhalt
als möglichst wenige Markdown-Dateien.

## Jobs

### Migrationsspezifikation je Phase (Fachbegriff)

Durchsucht ein Projekt (z. B. eine bestehende Spring-Batch-Migration) nach einem Fachbegriff wie `Risiko, Risk`
und erzeugt:

- `code-<begriff>.md` – den gesamten zugehörigen Code mit klaren Dateigrenzen (`===== BEGIN FILE … =====` /
  `===== END FILE … =====`), gruppiert nach Read → Process → Write → Datenzugriff → Konfiguration → Modell.
- `skill-<begriff>.md` – die Anleitung für die KI. Sie erstellt daraus eine fachliche Spezifikation, je gewählter
  Phase in einem eigenen Abschnitt, mit Belegen, `[OFFEN]` / `[PRODUKTSPEZIFISCH]`-Markierungen und
  phasenübergreifender Feldverfolgung.

Wichtige Einstellungen:

| Einstellung | Wirkung |
|---|---|
| Fachbegriff(e) | Kommagetrennt, inkl. Synonyme; Groß-/Kleinschreibung egal |
| Umfang | Nur Klassen mit dem Begriff oder zusätzlich referenzierte Projektklassen. Datenstrukturen (TO, PO, DTO, Entity …) werden transitiv verfolgt, damit z. B. `RisikoTO → DeckungTO → SelbstbeteiligungTO` vollständig ankommt |
| Betrachtete Phasen | Read / Process / Write. Wirkt auf den Code (andere Phasen entfallen) und auf den Skill |
| Package/Import ignorieren | Begriff nur im Package-Namen (`de.vers.risk`) ist kein Treffer |
| Max. Tokens/Datei | Teilt die Code-Datei an Dateigrenzen auf (`code-…-teil1.md` …) |
| Kompakt | Entfernt Imports und Lizenzköpfe (spart Tokens) |

Die Phasen-Zuordnung ist heuristisch (Spring-Batch-Interfaces, Namen, Packages). Feste Regel mit Vorrang: Klassen in
den Verzeichnissen `anreicherer`/`anreicherung` gehören immer zu Read, in `mapper`/`mapping` immer zu Process und in
`write`/`writer` immer zu Write. Die Regel steht in `PhaseClassifier.FIXED_DIRECTORIES`.

Die Skill-Texte liegen in `src/main/resources/skills/phase-spec/` und lassen sich ohne Java-Kenntnisse anpassen.

## Bauen und starten

Voraussetzungen: JDK 25 und Maven.

```
mvn package
java -jar target/devtoolbox.jar                       # Oberfläche
java -jar target/devtoolbox.jar konfig.json           # ohne Oberfläche, Job steht in der Datei ("job")
java -jar target/devtoolbox.jar phase-spec base=C:\proj "terms=Risiko,Risk" phases=READ,PROCESS
java -jar target/devtoolbox.jar hilfe                 # listet alle Jobs und Parameter
```

In der Oberfläche lassen sich Konfigurationen als JSON speichern und wieder laden.

## Erweitern

- **Neuer Job:** `Job` implementieren oder, für „Tool erzeugt Inhalt + Skill“, von `SkillJob` erben.
  Dann eine Zeile in `JobRegistry` ergänzen. Die Eingabemaske entsteht automatisch aus den `Param`-Definitionen.
- **Neues Tool (Baustein):** `Tool` implementieren und im Job verwenden. Tools erscheinen nicht in der Auswahl.

## Testmaterial

- `testprojekt/` – kleine Beispielmigration „Risiko“ mit absichtlichen Stolperfallen. Testfälle und
  Sollwerte stehen in `testprojekt/TESTFAELLE.md`, fertige Konfigurationen in `testprojekt/konfig/`.
- `copilot-testdateien/` – Markdown-Dateien mit ca. 10.000 bis 500.000 Tokens. Damit lässt sich ermitteln,
  wie viel Copilot annimmt und tatsächlich liest. Anleitung und Lösungen stehen in
  `copilot-testdateien/LOESUNG.md`.
