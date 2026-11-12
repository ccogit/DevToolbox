# Aufgabe: Recherche und Planung des optimalen Copilot-Auftrags für die DevToolbox

## 1. Ziel

Die DevToolbox erzeugt Aufträge für **Microsoft 365 Copilot** (Chat im Unternehmen). Die Ergebnisse von Copilot sind
derzeit **nicht gut genug**: Sie sind unvollständig, und Copilot ist sichtbar überfordert, vor allem wenn viel Code
mitgeliefert wird.

Erarbeite, **wie ein Auftrag unter den gegebenen Bedingungen (Abschnitt 3) am besten aufgebaut und formuliert wird**.
Dazu gehören die Aufteilung auf die drei Dateien, die Arbeitsschritte, die Formulierungen und die Größe der Inhalte.
Lege außerdem fest, **was die DevToolbox dafür anders erzeugen muss**.

Im Mittelpunkt steht der Job **`phase-spec`** (Spezifikation einer fachlichen Entität aus einem Referenzprojekt). Am
Ende überträgst du die Ergebnisse kurz auf `implementation` und `mapping-doc`.

Dies ist eine **Recherche- und Planungsaufgabe**. Java-Code wird nicht geändert. Neu formulieren sollst du nur die
Copilot-Texte (Skill und Aufgabe), siehe Abschnitt 6.

## 2. Ausgangslage

Repo: `main` von diesem Repository. Lies zuerst:

| Datei | Inhalt |
|---|---|
| `README.md` | Zweck, Jobs, Parameter, Ablage der Ergebnisse |
| `src/main/resources/skills/phase-spec/skill.md` | Skill für Copilot (Vorgehen, Arbeitsregeln, Ausgabeformat) |
| `src/main/resources/skills/phase-spec/read.md`, `process.md`, `write.md` | Phasenabschnitte, werden in den Skill eingesetzt (`{{PHASE_SECTIONS}}`) |
| `src/main/resources/aufgaben/phase-spec/aufgabe.md` | Vorlage der Aufgabe-Datei (Platzhalter `{{…}}`) |
| `src/main/java/de/devtoolbox/jobs/phasespec/PhaseSpecJob.java` | Parameter und Zusammenbau der drei Dateien |
| `src/main/java/de/devtoolbox/tools/codecollector/` | Zusammenstellung des Code-Kontexts: `CodeCollectorTool`, `ProjectScanner`, `PhaseClassifier`, `MarkdownWriter`, `AttributeIndex`, `DdlReader`, `TargetModel` |
| `src/main/java/de/devtoolbox/core/CopilotLimit.java` | Größenschätzung und Warnung |

Ein echtes, vollständiges Ergebnis erzeugst du selbst, ohne die Oberfläche:
`java -jar target/devtoolbox.jar phase-spec project=testziel "terms=Risiko"` (JDK 25, vorher `mvn package`). Die Jar
**nie ohne Argumente starten**, sonst öffnet sich die Oberfläche und blockiert. Die drei erzeugten Dateien sind dein
Anschauungsmaterial. Die Testdaten sind synthetisch; echter Projektcode steht dir nicht zur Verfügung.

### Beobachtungen aus dem echten Einsatz

- Für die Entität „Nachlass/Zuschlag“ hatte die Code-Datei **über 11.000 Zeilen mit 167 Quelldateien**. Für die
  Read-Spezifikation war nur ein kleiner Teil davon relevant. Der Rest waren generische Framework-Klassen,
  Export- und Dokumentationsklassen, Mapping-Klassen für ein anderes System und fachfremde Entitäten.
- Die Entität besteht fachlich aus **drei Teiltabellen** (risikobezogen, vertragsbezogen, Beiträge). Sie werden
  gemeinsam verarbeitet, haben aber verschiedene Attribute und Filterlogiken.
- Die Ergebnisse waren lückenhaft. Copilot musste die Attribute aus den Entity-Klassen rekonstruieren, obwohl das
  **Attributverzeichnis** bereits geliefert wird. Es steht allerdings am Anfang der großen Code-Datei, nicht in der
  Aufgabe.

### Selbstauskunft von Copilot (Hypothesen, keine Fakten)

Copilot hat auf die Frage, was besser werden muss, Folgendes vorgeschlagen. **Prüfe jeden Punkt kritisch.**
Selbstauskünfte eines Sprachmodells über die eigene Arbeitsweise sind oft falsch.

1. Den Code-Kontext fachlich vorfiltern: nur relevante Klassen, keine Framework-, Export- und Fremdklassen.
2. Die Aufgabe in fachliche Teilschritte aufteilen: je Teiltabelle eine Aufgabe, danach eine Konsolidierung.
3. Eine maschinenlesbare Attributliste vorgeben, gegen die Copilot validiert.
4. Prüfschritte im Skill erzwingen: Anzahl der Attribute = Anzahl der Tabellenzeilen; jede Zeile vollständig
   gefüllt; am Ende eine Vollständigkeitsübersicht (gesamt, dokumentiert, fehlend, doppelt).
5. Das Ergebnis als Datei erzeugen, nicht im Chat. Das ist bereits so umgesetzt.
6. Iterativ arbeiten: erst ein Attributinventar ausgeben, der Nutzer bestätigt, dann die Spezifikation.

## 3. Bedingungen (fest)

- **Werkzeug:** Microsoft 365 Copilot Chat im Unternehmen, Bedienung über Browser bzw. Teams. Kein API-Zugang, keine
  eigenen Agenten, keine Plugins. Welches Modell dahinter läuft, ist nicht steuerbar.
- **Höchstens 3 Dateien je Prompt.** Mehrere Prompts nacheinander im selben Chat sind möglich. In einem späteren
  Prompt können neue Dateien hochgeladen werden.
- Dateien werden als **Markdown** hochgeladen. Wie groß eine Datei sein darf und wie viel davon Copilot tatsächlich
  verarbeitet, ist unbekannt (siehe Recherchefrage F1).
- **Vertraulichkeit:** Der Code bleibt im Unternehmen. Es gibt keine externen Werkzeuge und keine Vorverarbeitung
  außerhalb der DevToolbox.
- **Die DevToolbox bleibt einfach.** Die Bedienung sind ein Job, ein Formular und drei Dateien. Zusätzliche Schritte
  für den Nutzer nur, wenn sie die Qualität nachweislich deutlich verbessern.
- **Inhaltliche Vorgaben an das Ergebnis** (bewusst so entschieden, nur mit gutem Grund ändern): keine Redundanz;
  je Phase **eine** Attributtabelle mit einer Zeile je Attribut; alle Fragen in **einer** OPL; jede Regel mit Beleg
  `Klasse.Methode`.

## 4. Recherchefragen

Belege jede Antwort mit Quellen: URL, Datum und kurzes Zitat. Trenne **dokumentierte Fakten** (Microsoft-Dokumentation),
**Erfahrungsberichte** und **eigene Annahmen** ausdrücklich voneinander.

- **F1 – Verarbeitung hochgeladener Dateien:** Wie verarbeitet M365 Copilot Chat hochgeladene Dateien? Kommt der
  ganze Inhalt in den Kontext, oder wird er abschnittsweise abgerufen (Retrieval/Chunking)? Welche Grenzen sind
  dokumentiert (Dateigröße, Zeichen bzw. Tokens, Anzahl der Dateien, Länge der Antwort)? Was folgt daraus für
  Vollständigkeit bei einer Datei mit 11.000 Zeilen?
- **F2 – Belastbare Größe:** Bis zu welcher Größe von Code-Kontext ist eine vollständige, attributgenaue Auswertung
  realistisch? Gib eine begründete Größenordnung an, zum Beispiel in Zeilen oder Tokens je Datei und Prompt.
- **F3 – Aufbau der drei Dateien:** Was gehört in welche Datei? Betrachte Reihenfolge, Position der Anweisungen (vor
  oder nach dem Inhalt) und Wiederholung wichtiger Regeln. Sollte die Checkliste in der Aufgabe stehen statt im
  Code-Kontext? Sollten Skill und Aufgabe zusammengelegt werden, um eine Datei für Code frei zu machen?
- **F4 – Formulierung:** Welche Formulierungen verbessern Vollständigkeit und Treue bei strukturierter Extraktion aus
  langem Kontext nachweislich? Beispiele: nummerierte Checklisten, feste Tabellenschemata, Pflicht-Selbstprüfung,
  Verbot von Auslassungen („…“, „weitere analog“), Zitierpflicht. Was wirkt kontraproduktiv, etwa zu lange Skills,
  widersprüchliche Regeln oder zu viele Sonderfälle? Bewerte den vorhandenen Skill konkret.
- **F5 – Ein Prompt oder mehrere:** Lohnt eine Abfolge mehrerer Prompts im selben Chat, z. B. Inventar → Read →
  Process → Write → Konsolidierung, oder je Teiltabelle ein Durchgang? Welche Abfolge ist für den Nutzer noch einfach?
  Wie wird verhindert, dass Copilot in späteren Prompts frühere Ergebnisse vergisst oder umschreibt?
- **F6 – Große Ergebnisse:** Wie entsteht zuverlässig eine lange Ergebnisdatei (mehrere hundert Tabellenzeilen) ohne
  Abbruch oder Kürzung, etwa über Dateierzeugung, abschnittsweise Ausgabe oder Fortsetzungsprompts?
- **F7 – Vorfilterung durch das Tool:** Welche Auswahlregeln für den Code-Kontext sind sinnvoll und mit der
  vorhandenen Logik umsetzbar? Beispiele: Ausschlussmuster je Projekt, Begrenzung der transitiven Referenzen,
  Einschränkung auf gewählte DDL-Tabellen, Phasenfilter, Größenbudget mit Priorisierung. Belege die Ansatzpunkte im
  Code mit `pfad/Datei.java:Zeile`.

## 5. Vorgehen

1. Recherchiere F1–F2. Die Antworten bestimmen die Größenbudgets für alles Weitere.
2. Erzeuge die drei Dateien für `testziel`/`Risiko` und analysiere sie: Größe, Aufbau, Position der Checkliste,
   Länge des Skills, Widersprüche und Redundanzen in den Anweisungen.
3. Leite aus F3–F7 einen **Zielauftrag** ab und vergleiche ihn mit mindestens einer Alternative, z. B. ein Prompt
   mit gefiltertem Code gegenüber mehreren Prompts je Phase oder Teiltabelle. Begründe die Wahl.
4. Formuliere die neuen Texte (Abschnitt 6, Punkt 3) und den Änderungsplan für das Tool (Abschnitt 6, Punkt 4).
5. Entwirf das Testprotokoll (Abschnitt 6, Punkt 5). Copilot selbst kannst du nicht testen; der Auftraggeber führt
   die Tests am echten Fall durch.

## 6. Ergebnis

1. **Bericht `review/COPILOT-AUFTRAG-<Datum>.md`**, Gliederung:
   1. Zusammenfassung: höchstens 10 Sätze, die Empfehlung zuerst.
   2. Antworten auf F1–F7, jeweils mit Quellen und der Einstufung Fakt, Erfahrungsbericht oder Annahme.
   3. Bewertung der sechs Copilot-Vorschläge (Abschnitt 2): übernehmen, ändern oder verwerfen, jeweils mit Grund.
   4. **Zielauftrag:** Ablauf aus Nutzersicht als Schrittfolge, Inhalt und Größenbudget je Datei und Prompt, dazu
      die Texte eventueller Folgeprompts im Wortlaut. **Jeder Prompt hat höchstens 3 Dateien**, das gilt für jede
      Variante und jeden Folgeprompt. Vorschläge, die mehr Dateien brauchen, sind unzulässig.
   5. Änderungsplan für die DevToolbox: Tabelle mit Maßnahme, Begründung, Ansatzpunkt im Code
      (`Datei:Zeile`), Aufwand (S/M/L), erwartete Wirkung und Reihenfolge. **Kein Java-Code.**
   6. Risiken und offene Punkte.
2. **Keine Redundanz im Bericht.** Jede Empfehlung steht genau einmal.
3. **Neue Copilot-Texte als vollständige Dateien** unter ihren Originalpfaden:
   `src/main/resources/skills/phase-spec/*.md` und `src/main/resources/aufgaben/phase-spec/aufgabe.md`. Die
   vorhandenen Platzhalter `{{…}}` bleiben erhalten. Neue Platzhalter nur, wenn der Änderungsplan sie vorsieht; dann
   dort ausdrücklich nennen. Die Texte sind auf Deutsch, knapp und widerspruchsfrei.
4. **Änderungsplan** wie in Punkt 1.5.
5. **Testprotokoll `review/COPILOT-TESTPROTOKOLL.md`** für den Auftraggeber. Es enthält:
   - die Varianten, mindestens heutiger Auftrag gegen Zielauftrag, am selben echten Fall;
   - Messgrößen: Anteil vollständig beschriebener Attribute, fehlende und doppelte Attribute, Regeln ohne oder mit
     falschem Beleg, erfundene Regeln, Anzahl der OPL-Punkte, Abbrüche oder Kürzungen, Aufwand für den Nutzer in
     Prompts und Minuten;
   - einen Bewertungsbogen zum Ausfüllen.
6. **Abgabe als ZIP** `copilot-auftrag-<Datum>.zip` mit allen neuen bzw. geänderten Dateien in ihren Originalpfaden,
   direkt über `main` entpackbar.

## 7. Regeln

- **Keine Commits, keine Branches, keine Pull Requests, kein Push** in dieses Repository. Arbeite in einer lokalen
  Kopie; das Ergebnis ist ausschließlich das ZIP.
- Temporäre Dateien außerhalb des Repos ablegen.
- Jede Aussage mit Beleg: eine Quelle, eine Datei und Zeile oder ein Messwert aus deinem eigenen Lauf.
- Nichts erfinden. Was du nicht klären kannst, gehört unter „Risiken und offene Punkte“.
